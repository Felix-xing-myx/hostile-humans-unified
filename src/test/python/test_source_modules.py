"""Verify the real package tree independently of source_paths compatibility lookups."""
import json
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
NAMESPACE = "dev.felix.hostilehumans"

class SourceModulesTest(unittest.TestCase):
    def test_java_packages_match_physical_paths(self):
        for folder in ("main", "test"):
            base = ROOT / "src" / folder / "java"
            files = list(base.rglob("*.java"))
            self.assertTrue(files)
            for file in files:
                package = re.search(r"(?m)^package ([\w.]+);", file.read_text(encoding="utf-8"))
                self.assertIsNotNone(package, str(file))
                self.assertTrue(package[1].startswith(NAMESPACE + "."), str(file))
                self.assertEqual(file.parent.relative_to(base).as_posix(), package[1].replace(".", "/"))

    def test_module_inventory_is_exact(self):
        data = json.loads((ROOT / "docs/module-map.json").read_text(encoding="utf-8"))
        self.assertEqual(data["namespace"], NAMESPACE)
        recorded = set()
        for module, spec in data["modules"].items():
            self.assertTrue(spec["purpose"].strip())
            for name in spec["classes"]:
                path = spec["source_root"] + "/" + name + ".java"
                self.assertNotIn(path, recorded)
                recorded.add(path)
                self.assertTrue((ROOT / path).is_file(), path)
        actual = {p.relative_to(ROOT).as_posix() for p in (ROOT / "src/main/java").rglob("*.java")}
        self.assertEqual(recorded, actual)

    def test_dynamic_test_index_tracks_real_sources(self):
        data = json.loads((ROOT / "src/test/resources/hhu-module-sources.json").read_text(encoding="utf-8"))
        actual = list((ROOT / "src/main/java").rglob("*.java"))
        self.assertEqual(len(data), len(actual))
        for name, spec in data.items():
            file = ROOT / spec["path"]
            self.assertEqual(file.stem, name)
            package = re.search(r"package ([\w.]+);", file.read_text(encoding="utf-8"))[1]
            self.assertEqual(spec["class"], package + "." + name)

    def test_mixin_configurations_resolve_to_real_classes(self):
        resources = ROOT / "src/main/resources"
        for file in resources.glob("hostile_humans_unified*mixins.json"):
            data = json.loads(file.read_text(encoding="utf-8"))
            self.assertEqual(data["package"], NAMESPACE + ".mixin")
            for key in ("mixins", "client", "server"):
                for name in data.get(key, []):
                    self.assertTrue((ROOT / "src/main/java" / (data["package"] + "." + name).replace(".", "/")).with_suffix(".java").is_file())
            if "plugin" in data:
                self.assertTrue((ROOT / "src/main/java" / data["plugin"].replace(".", "/")).with_suffix(".java").is_file())

    def test_no_legacy_java_roots_or_wildcard_module_imports(self):
        legacy = re.compile(r"club[./]someoneice[./]humangunner|com[./]craftix[./]hostile_humans")
        for file in (ROOT / "src").rglob("*.java"):
            text = file.read_text(encoding="utf-8")
            self.assertIsNone(legacy.search(text), str(file))
            self.assertIsNone(re.search(r"(?m)^import dev\.felix\.hostilehumans\.[\w.]+\.\*;", text), str(file))

if __name__ == "__main__":
    unittest.main()
