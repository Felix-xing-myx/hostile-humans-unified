"""Read-only javap contract audit; never invokes javac, Gradle or a game."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess


def methods(javap, jar, name):
    output = subprocess.check_output([javap, "-classpath", str(jar), "-p", "-c", "-s", name], text=True, encoding="utf-8", errors="replace")
    result = {}
    current = None
    descriptor = None
    lines = []
    for line in output.splitlines():
        if re.match(r"^  (public|protected|private|static).*\(", line):
            if current and descriptor:
                result[(current, descriptor)] = "\n".join(lines)
            current = line.split("(", 1)[0].split()[-1]
            descriptor = None
            lines = []
        if line.strip().startswith("descriptor:"):
            descriptor = line.strip().split(":", 1)[1].strip()
        lines.append(line)
    if current and descriptor:
        result[(current, descriptor)] = "\n".join(lines)
    return result


def main():
    parser = argparse.ArgumentParser()
    for name in ("javap", "scguns", "jeg", "minecraft"):
        parser.add_argument("--" + name, required=True)
    args = parser.parse_args()
    assert Path(args.javap).stem.lower() == "javap", "Only the read-only javap tool is supported"
    level = "net/minecraft/world/level/Level"
    query = level + ".m_45976_:(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"
    insert = level + ".m_7967_:(Lnet/minecraft/world/entity/Entity;)Z"
    contracts = [
        ("scguns", "top.ribs.scguns.entity.throwable.ThrowableGasGrenadeEntity", "applyGasEffects", "()V", "top/ribs/scguns/common/SulfurGasCloud.applyGasEffects:(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/Vec3;DII)V", 1),
        ("scguns", "top.ribs.scguns.entity.throwable.ThrowableGasGrenadeEntity", "m_8119_", "()V", "top/ribs/scguns/common/SulfurGasCloud.checkAndHandleFireExplosion:(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/Vec3;D)Z", 1),
        ("scguns", "top.ribs.scguns.entity.throwable.ThrowableChokeBombEntity", "applyChokeEffects", "()V", "top/ribs/scguns/common/ChokeBombCloud.applyChokeEffects:(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/Vec3;D)V", 1),
        ("scguns", "top.ribs.scguns.common.SulfurGasCloud", "applyGasEffects", "(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/Vec3;DII)V", query, 1),
        ("scguns", "top.ribs.scguns.common.ChokeBombCloud", "applyChokeEffects", "(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/Vec3;D)V", query, 1),
        ("scguns", "top.ribs.scguns.entity.throwable.ThrowableSwarmBombEntity", "onDeath", "()V", insert, 2),
        ("jeg", "ttv.migami.jeg.entity.projectile.WhirpoolEntity", "createLandWhirpool", "(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/core/BlockPos;)V", insert, 1),
        ("jeg", "ttv.migami.jeg.entity.Splash", "m_8119_", "()V", level + ".m_45933_:(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;", 1),
        ("jeg", "ttv.migami.jeg.entity.Bubble", "m_8119_", "()V", level + ".m_45933_:(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;", 1),
        ("jeg", "ttv.migami.jeg.entity.Splash", "m_8119_", "()V", "net/minecraft/world/entity/LivingEntity.m_6469_:(Lnet/minecraft/world/damagesource/DamageSource;F)Z", 2),
        ("minecraft", "net.minecraft.world.entity.AreaEffectCloud", "m_8119_", "()V", query, 1),
        ("minecraft", "net.minecraft.world.entity.projectile.ThrownPotion", "m_37547_", "(Ljava/util/List;Lnet/minecraft/world/entity/Entity;)V", query, 1),
        ("minecraft", "net.minecraft.world.entity.projectile.ThrownPotion", "m_37537_", "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/alchemy/Potion;)V", insert, 1),
    ]
    cache = {}
    checked = []
    for backend, name, method, descriptor, invocation, expected in contracts:
        key = backend, name
        if key not in cache:
            cache[key] = methods(args.javap, getattr(args, backend), name)
        body = cache[key].get((method, descriptor))
        assert body is not None, (backend, name, method, descriptor, "missing method")
        count = body.count("// Method " + invocation)
        assert count == expected, (backend, name, method, invocation, count, expected)
        checked.append({"backend": backend, "class": name, "method": method, "descriptor": descriptor, "invoke": invocation, "count": count})
    jars = {}
    for backend in ("scguns", "jeg", "minecraft"):
        path = Path(getattr(args, backend))
        with path.open("rb") as stream:
            digest = hashlib.file_digest(stream, "sha256").hexdigest()
        jars[backend] = {"path": str(path.resolve()), "sha256": digest}
    print(json.dumps({"read_only": True, "contracts_passed": len(checked), "jars": jars, "contracts": checked}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
