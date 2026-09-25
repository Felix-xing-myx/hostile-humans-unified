"""Offline structural audit; never launches Minecraft or a server.

Checks resource preservation, JSON/TOML, class-file versions, self-owned symbol
linkage after SRG remapping, and the absence of embedded original mod jars.
This is deliberately NOT a runtime/Mixin integration test.
"""
from pathlib import Path
import argparse
import hashlib
import json
import re
import struct
import tomllib
import zipfile


class ClassFile:
    def __init__(self, data):
        self.data, self.offset = data, 0
        assert self.read("I") == 0xCAFEBABE
        self.read("H")
        self.version = self.read("H")
        count = self.read("H")
        self.cp = [None] * count
        i = 1
        while i < count:
            tag = self.read("B")
            if tag == 1:
                n = self.read("H")
                value = self.take(n).decode("utf-8", errors="replace")
            elif tag in (3, 4):
                value = self.take(4)
            elif tag in (5, 6):
                value = self.take(8)
            elif tag in (7, 8, 16, 19, 20):
                value = self.read("H")
            elif tag in (9, 10, 11, 12, 17, 18):
                value = (self.read("H"), self.read("H"))
            elif tag == 15:
                value = (self.read("B"), self.read("H"))
            else:
                raise AssertionError(f"unknown constant tag {tag}")
            self.cp[i] = (tag, value)
            i += 2 if tag in (5, 6) else 1
        self.read("H")
        self.name = self.class_name(self.read("H"))
        parent = self.read("H")
        self.parents = [self.class_name(parent)] if parent else []
        self.parents += [self.class_name(self.read("H")) for _ in range(self.read("H"))]
        self.fields = self.members()
        self.methods = self.members()

    def take(self, count):
        value = self.data[self.offset:self.offset + count]
        self.offset += count
        return value

    def read(self, fmt):
        return struct.unpack(">" + fmt, self.take(struct.calcsize(fmt)))[0]

    def utf(self, index):
        return self.cp[index][1]

    def class_name(self, index):
        return self.utf(self.cp[index][1])

    def members(self):
        result = set()
        for _ in range(self.read("H")):
            self.read("H")
            result.add((self.utf(self.read("H")), self.utf(self.read("H"))))
            for _ in range(self.read("H")):
                self.read("H")
                self.take(self.read("I"))
        return result

    def references(self):
        for constant in self.cp:
            if constant and constant[0] in (9, 10, 11):
                owner, name_type = constant[1]
                name, desc = self.cp[name_type][1]
                yield constant[0], self.class_name(owner), self.utf(name), self.utf(desc)


def audit(artifact, original, addon, minecraft, jdk, compat_mods=None):
    repairs = json.loads((Path(__file__).parent / "structure_compat_repairs.json").read_text(encoding="utf-8"))
    checked_repairs = set()
    with zipfile.ZipFile(artifact) as package:
        names = set(package.namelist())
        assert len(names) == len(package.namelist()), "duplicate zip entries"
        assert not any(n.endswith(".jar") for n in names), "embedded binary mod"
        assert not any(n.startswith(("net/minecraft/", "net/minecraftforge/", "com/tacz/")) for n in names)
        metadata = tomllib.loads(package.read("META-INF/mods.toml").decode())
        assert {m["modId"] for m in metadata["mods"]} == {"hostile_humans", "humangunner"}
        tacz = [d for d in metadata["dependencies"]["hostile_humans"] if d["modId"] == "tacz"]
        assert len(tacz) == 1 and tacz[0]["mandatory"] is False, "TaCZ must be optional"
        json_count = 0
        metadata_count = 0
        for n in names:
            if n.endswith((".json", ".mcmeta")):
                try:
                    json.loads(package.read(n))
                except json.JSONDecodeError as error:
                    raise AssertionError(f"Invalid JSON {n}: {error}") from error
                if n.endswith(".json"):
                    json_count += 1
                else:
                    metadata_count += 1
        resource_count = 0
        for source in (original, addon):
            with zipfile.ZipFile(source) as upstream:
                for n in upstream.namelist():
                    if n.startswith(("assets/", "data/", "datapacks/")) and not n.endswith("/"):
                        assert n in names, f"missing resource: {n}"
                        # Only addon/extension assets override matching base paths.
                        if source == addon or n.startswith(("assets/hostile_humans/", "data/hostile_humans/", "datapacks/")):
                            if n in repairs:
                                expected = repairs[n]
                                assert hashlib.sha256(upstream.read(n)).hexdigest() == expected["upstream_sha256"], f"repair source changed: {n}"
                                assert hashlib.sha256(package.read(n)).hexdigest() == expected["repaired_sha256"], f"repair output changed: {n}"
                                checked_repairs.add(n)
                            elif n == "data/hostile_humans/worldgen/structure_set/all_houses.json":
                                legacy = re.sub(r"(?m)^\s*//[^\r\n]*", "", upstream.read(n).decode())
                                assert json.loads(legacy) == json.loads(package.read(n)), n
                            else:
                                assert upstream.read(n) == package.read(n), f"changed legacy resource: {n}"
                        resource_count += 1
        assert checked_repairs == set(repairs), "unverified structure repair entries"
        classes = {n[:-6]: ClassFile(package.read(n)) for n in names if n.endswith(".class")}
        assert all(c.version == 61 for c in classes.values()), "non-Java-17 bytecode"
        optional_tacz = ("GunnerGoal", "TaczIntegration", "TaczMaidIntegration")
        tacz_reference_classes = []
        for key, cls in classes.items():
            if any(c and c[0] == 1 and "com/tacz/" in c[1] for c in cls.cp):
                simple = key.rsplit("/", 1)[-1].split("$", 1)[0]
                assert simple in optional_tacz, f"TaCZ API escaped optional adapter: {key}"
                tacz_reference_classes.append(key)
        defaults = json.loads(package.read("defaults/hostile_humans_unified.json"))
        assert list(defaults)[-1] == "tacz", "TaCZ config must be last"
        assert set(defaults["tiers"]) == {"roamer", "tier1", "tier2", "tier3"}
        for key in ("com/craftix/hostile_humans/entity/entities/Human",
                    "club/someoneice/humangunner/HumanGunner",
                    "dev/felix/hostilehumans/core/OwnerIndex"):
            assert key in classes
        forbidden = ("HumanMixin", "HumanServerDataMixin", "SpawnHandlerMixin",
                     "HumanCombatCompatMix", "WalkNodeMix", "ThrownTridentMix", "LocateMixin")
        assert not any(n.rsplit("/", 1)[-1][:-6] in forbidden for n in names if n.endswith(".class"))
        mixins = []
        for config_name in ("humangunner.mixins.json", "mixins.hostile_humans.json"):
            cfg = json.loads(package.read(config_name))
            for name in cfg.get("mixins", []) + cfg.get("client", []):
                path = cfg["package"].replace(".", "/") + "/" + name
                assert path in classes, f"missing mixin: {path}"
                mixins.append(path)

        archives = [zipfile.ZipFile(minecraft)]
        archives += [zipfile.ZipFile(p) for p in original.parent.glob("*.jar")
                     if p not in (original, addon)]
        if compat_mods is not None and compat_mods.resolve() != original.parent.resolve():
            archives += [zipfile.ZipFile(p) for p in compat_mods.glob("*.jar")
                         if p not in (original, addon)]
        archives += [zipfile.ZipFile(p) for p in (jdk / "jmods").glob("*.jmod")]
        archive_names = {id(z): set(z.namelist()) for z in archives}
        cache = dict(classes)

        def lookup(name):
            if name in cache:
                return cache[name]
            for archive in archives:
                key = ("classes/" if str(archive.filename).endswith(".jmod") else "") + name + ".class"
                if key in archive_names[id(archive)]:
                    cache[name] = ClassFile(archive.read(key))
                    return cache[name]
            cache[name] = None
            return None

        def has_member(owner, member, field, visited=None):
            visited = set() if visited is None else visited
            if owner in visited:
                return False
            visited.add(owner)
            cls = lookup(owner)
            if cls is None:
                # An optional third-party parent is outside this structural audit.
                return None
            if member in (cls.fields if field else cls.methods):
                return True
            uncertain = False
            for parent in cls.parents:
                result = has_member(parent, member, field, visited)
                if result is True:
                    return True
                uncertain |= result is None
            return None if uncertain else False

        selector_checks = 0
        skipped_optional = []
        source_root = Path(__file__).resolve().parents[1] / "src/main/java"
        for path in mixins:
            source = (source_root / (path + ".java")).read_text(encoding="utf-8")
            declaration = re.search(r"@Mixin\(([\s\S]*?)\)", source).group(1)
            imports = {n.rsplit(".", 1)[-1]: n.replace(".", "/")
                       for n in re.findall(r"import ([\w.]+);", source)}
            targets = [imports.get(n, n.replace(".", "/"))
                       for n in re.findall(r"([\w.]+)\.class", declaration)]
            targets += [n.replace(".", "/") for n in re.findall(r'"([\w.]+)"', declaration)]
            assert not any(t in classes for t in targets), f"internal overwrite remains: {path}"
            mappings = {}
            refmap_name = "mixins.hostile_humans.refmap.json"
            if path.startswith("com/craftix/"):
                mappings = json.loads(package.read(refmap_name))["mappings"].get(path, {})
            for target in targets:
                target_class = lookup(target)
                if target_class is None:
                    assert "@Pseudo" in source, f"required mixin target missing: {target}"
                    skipped_optional.append(target)
                    continue
                for shadow in re.findall(r"@Shadow[^;]*?\b([\w$]+);", source):
                    assert any(n == shadow for n, d in target_class.fields), f"missing shadow {target}.{shadow}"
                    selector_checks += 1
                for raw in re.findall(r'method\s*=\s*(\{[^}]+\}|"[^"]+")', source):
                    for selector in re.findall(r'"([^"]+)"', raw):
                        mapped = mappings.get(selector, selector)
                        if mapped.startswith("L") and ";" in mapped:
                            mapped = mapped.split(";", 1)[1]
                        name = mapped.split("(", 1)[0]
                        descriptor = mapped[len(name):] or None
                        exists = any(n == name and (descriptor is None or d == descriptor)
                                     for n, d in target_class.methods)
                        assert exists, f"missing injection target {target}.{mapped}"
                        selector_checks += 1
        checked = 0
        unresolved_external_parents = 0
        for cls in classes.values():
            for kind, owner, name, desc in cls.references():
                if owner.startswith(("com/craftix/hostile_humans/", "club/someoneice/humangunner/",
                                     "dev/felix/hostilehumans/")):
                    assert owner in classes, f"missing own class: {owner}"
                    resolved = has_member(owner, (name, desc), kind == 9)
                    assert resolved is not False, (
                        f"unresolved own symbol: {cls.name} -> {owner}.{name}{desc}")
                    if resolved is True:
                        checked += 1
                    else:
                        unresolved_external_parents += 1
        for archive in archives:
            archive.close()
    result = {"artifact": artifact.name, "sha256": hashlib.sha256(artifact.read_bytes()).hexdigest(),
              "java_classes": len(classes), "json_files": json_count, "pack_metadata_files": metadata_count,
              "preserved_resource_entries": resource_count, "own_symbol_references_checked": checked,
              "documented_resource_repairs": len(checked_repairs),
              "references_with_unavailable_parent": unresolved_external_parents,
              "external_mixin_classes": len(mixins), "mixin_selectors_and_shadows_checked": selector_checks,
              "uninstalled_optional_targets": skipped_optional,
              "tacz_mandatory": False, "tacz_reference_classes": sorted(tacz_reference_classes),
              "runtime_tested": False}
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    for arg in ("artifact", "original", "addon", "minecraft", "jdk"):
        parser.add_argument("--" + arg, required=True, type=Path)
    parser.add_argument("--compat-mods", type=Path, help="Installed dependency directory when originals live in a backup")
    args = parser.parse_args()
    audit(**vars(args))
