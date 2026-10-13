"""Read-only historical TaCZ linkage/hook audit; does not compile or load mods.

Uses existing compiled HHU classes, so rebuild them separately when API usage changes.
Passing is structural evidence, not a Minecraft startup or gameplay guarantee.
"""
import argparse
import json
import re
import subprocess
import zipfile
from pathlib import Path
from verify_package import ClassFile

PREFIX = "com/tacz/"

def disassemble(jar, owner, javap):
    result = subprocess.run([str(javap), "-p", "-c", "-s", "-classpath", str(jar),
                             owner.replace("/", ".")], capture_output=True, text=True)
    if result.returncode:
        return {}
    methods = {}
    # javap emits a declaration followed by its descriptor and optional Code.
    for match in re.finditer(r"^  [^\n]*[ ;]([\w$<>]+)\([^\n]*\);\n    descriptor: (\S+)(.*?)(?=^  [^ \n]|^}\s*$)",
                             result.stdout, re.M | re.S):
        methods[match.group(1), match.group(2)] = match.group(3)
    return methods


def audit(jar, compiled, javap, contract):
    references, required_classes = set(), set()
    for path in compiled.rglob("*.class"):
        cls = ClassFile(path.read_bytes())
        references.update(r for r in cls.references() if r[1].startswith(PREFIX))
        required_classes.update(cls.class_name(i) for i, entry in enumerate(cls.cp)
                                if entry and entry[0] == 7 and cls.class_name(i).startswith(PREFIX))
    assert references, "No compiled TaCZ references found; provide existing main classes"
    with zipfile.ZipFile(jar) as archive:
        classes = {n[:-6]: ClassFile(archive.read(n)) for n in archive.namelist()
                   if n.startswith(PREFIX) and n.endswith(".class")}
    failures = ["missing class " + n for n in sorted(required_classes - classes.keys())]

    def member(owner, name, desc, field, seen=None):
        seen = set() if seen is None else seen
        if owner in seen:
            return False
        seen.add(owner)
        if not owner.startswith(PREFIX):
            return False  # Only TaCZ-owned members are resolved here.
        cls = classes.get(owner)
        return bool(cls and ((name, desc) in (cls.fields if field else cls.methods)
                            or any(member(p, name, desc, field, seen) for p in cls.parents)))

    # Existing bytecode predates the sound bridge edit. Audit its source replacement
    # explicitly until the next authorized build refreshes main classes.
    obsolete_sound = ("com/tacz/guns/client/sound/SoundPlayManager", "playAnimationSound")
    sound_source = Path("src/main/java/club/someoneice/humangunner/TaczReloadSoundClient.java").read_text(encoding="utf-8")
    uses_bridge = "ClientAnimationCompatibility.playTaczReloadSound(" in sound_source and "SoundPlayManager.playAnimationSound(" not in sound_source
    inherited = {
        ("java/lang/Enum", "name", "()Ljava/lang/String;"),
        ("net/minecraftforge/eventbus/api/Event", "setCanceled", "(Z)V"),
        ("net/minecraft/client/model/Model", "renderToBuffer", "(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V"),
    }
    def external_inheritance(owner, name, desc, seen=None):
        seen = set() if seen is None else seen
        if owner in seen:
            return False
        seen.add(owner)
        if (owner, name, desc) in inherited:
            return True
        cls = classes.get(owner)
        return bool(cls and any(external_inheritance(p, name, desc, seen) for p in cls.parents))

    for tag, owner, name, desc in sorted(references):
        if uses_bridge and (owner, name) == obsolete_sound:
            continue
        if tag != 9 and name == "renderToBuffer" and owner == PREFIX + "guns/client/model/SlotModel" and member(owner, "m_7695_", desc, False):
            continue
        if tag != 9 and external_inheritance(owner, name, desc):
            continue
        if not member(owner, name, desc, tag == 9):
            failures.append(f"missing member {owner}.{name}{desc}")

    constants = {name: "".join(re.findall(r'"([^"\n]*)"', expression))
                 for name, expression in re.findall(r"public static final String (\w+) = (.*?);\s*(?=\n)",
                     contract.read_text(encoding="utf-8"), re.S)}
    reload_owner = PREFIX + "guns/entity/shooter/LivingEntityReload"
    shoot_owner = PREFIX + "guns/entity/shooter/LivingEntityShoot"
    script_owner = PREFIX + "guns/item/ModernKineticGunScriptAPI"
    bodies = {owner: disassemble(jar, owner, javap) for owner in
              (reload_owner, shoot_owner, script_owner, PREFIX + "guns/util/EntityUtil")}

    def require_method(owner, selector):
        name, desc = selector.split("(", 1)
        if (name, "(" + desc) not in classes.get(owner, type("Empty", (), {"methods": set()})()).methods:
            failures.append(f"missing hook method {owner}.{selector}")

    for key in ("RELOAD", "CANCEL_RELOAD", "TICK_RELOAD"):
        require_method(reload_owner, constants[key])
    shoot_selectors = [constants["SHOOT"], constants["SHOOT_LEGACY"]]
    if not any(n + d in shoot_selectors for n, d in classes.get(shoot_owner, type("Empty", (), {"methods": set()})()).methods):
        failures.append("missing supported shoot entry")
    for key in ("SCRIPT_INVENTORY_AMMO", "SCRIPT_SHOOT_ONCE"):
        require_method(script_owner, constants[key])

    def count_calls(owner, selectors, target, expected):
        selected = []
        for (name, desc), body in bodies[owner].items():
            if any((s.startswith("*(") and desc == s[1:]) or name + desc == s for s in selectors):
                selected.append(body)
        parsed = re.fullmatch(r"L([^;]+);([^ (]+)(\(.*)", target)
        needle = parsed[1] + "." + parsed[2] + ":" + parsed[3]
        count = sum(line.count(needle) for body in selected for line in body.splitlines()
                    if re.search(r"\binvoke\w+\s", line))
        if count != expected:
            failures.append(f"hook call {target}: expected {expected}, found {count}")

    for key in ("RELOAD_INVENTORY_AMMO", "START_RELOAD", "RELOAD_NOTIFICATION"):
        count_calls(reload_owner, [constants["RELOAD"], constants["RELOAD_BODY"]], constants[key], 1)
    count_calls(reload_owner, [constants["TICK_RELOAD"]], constants["GUN_TICK_RELOAD"], 1)
    count_calls(shoot_owner, shoot_selectors, constants["SHOOT_INVENTORY_AMMO"], 1)
    collision_args = "(Lnet/minecraft/world/entity/projectile/Projectile;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)"
    require_method(PREFIX + "guns/util/EntityUtil", "findEntityOnPath" + collision_args + "Lcom/tacz/guns/entity/EntityKineticBullet$EntityResult;")
    require_method(PREFIX + "guns/util/EntityUtil", "findEntitiesOnPath" + collision_args + "Ljava/util/List;")
    collision = bodies[PREFIX + "guns/util/EntityUtil"]
    for method in ("findEntityOnPath", "findEntitiesOnPath"):
        selectors = [n + d for n, d in collision if n == method]
        count_calls(PREFIX + "guns/util/EntityUtil", selectors,
                    "Lnet/minecraft/world/level/Level;m_6249_(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;", 1)
    require_method(PREFIX + "guns/client/model/functional/MuzzleFlashRender",
                   "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/item/ItemDisplayContext;II)V")
    for owner, name, desc in (
        (reload_owner, "shooter", "Lnet/minecraft/world/entity/LivingEntity;"),
        (reload_owner, "data", "Lcom/tacz/guns/entity/shooter/ShooterDataHolder;"),
        (shoot_owner, "shooter", "Lnet/minecraft/world/entity/LivingEntity;"),
        (script_owner, "shooter", "Lnet/minecraft/world/entity/LivingEntity;")):
        if not member(owner, name, desc, True):
            failures.append(f"missing shadow {owner}.{name}:{desc}")
    if uses_bridge:
        require_method(PREFIX + "guns/client/sound/SoundPlayManager", "playClientSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/resources/ResourceLocation;FFI)Lcom/tacz/guns/client/sound/GunSoundInstance;")
    # Optional Player Animator bridge uses reflection, absent from member refs.
    for owner, selector in (
        (PREFIX + "guns/client/resource/GunDisplayInstance", "getPlayerAnimator3rd()Lnet/minecraft/resources/ResourceLocation;"),
        (PREFIX + "guns/compat/playeranimator/animation/PlayerAnimatorAssetManager", "get()Lcom/tacz/guns/compat/playeranimator/animation/PlayerAnimatorAssetManager;"),
        (PREFIX + "guns/compat/playeranimator/animation/PlayerAnimatorAssetManager", "containsKey(Lnet/minecraft/resources/ResourceLocation;)Z"),
        (PREFIX + "guns/compat/playeranimator/animation/PlayerAnimatorAssetManager", "getAnimations(Lnet/minecraft/resources/ResourceLocation;Ljava/lang/String;)Ljava/util/Optional;")):
        require_method(owner, selector)
    return {"jar": jar.name, "compiled_members": len(references),
            "compiled_classes": len(required_classes), "failures": failures}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("jars", nargs="+", type=Path)
    parser.add_argument("--compiled", type=Path, default=Path("build/classes/java/main"))
    parser.add_argument("--javap", type=Path, default=Path("D:/Java/17/bin/javap.exe"))
    parser.add_argument("--contract", type=Path, default=Path("src/main/java/club/someoneice/humangunner/TaczAmmoMixinContract.java"))
    args = parser.parse_args()
    reports = [audit(jar, args.compiled, args.javap, args.contract) for jar in args.jars]
    print(json.dumps(reports, indent=2, ensure_ascii=False))
    raise SystemExit(1 if any(r["failures"] for r in reports) else 0)
