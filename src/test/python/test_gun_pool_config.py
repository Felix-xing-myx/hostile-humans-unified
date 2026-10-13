"""Packaged pool integrity; Java behavior regressions live in UnifiedConfigTest/TierConfigFilesTest."""
import json
import os
from pathlib import Path
import re
import unittest
import zipfile

PROJECT = Path(__file__).resolve().parents[3]
ROOT = json.loads((PROJECT / "src/main/resources/defaults/hostile_humans_unified.json").read_text(encoding="utf-8"))
RANKS = ("roamer", "tier1", "tier2", "tier3")


class GunPoolConfig(unittest.TestCase):
    def test_backend_globals_and_bans_are_separate(self):
        self.assertEqual(ROOT["tacz"]["spawn_pool_source"], "global")
        self.assertEqual(ROOT["scguns"]["spawn_pool_source"], "tier")
        for backend in ("tacz", "scguns"):
            self.assertEqual(ROOT[backend]["gun_blacklist"], [])
            self.assertTrue(ROOT[backend]["auto_add_new_guns"])
            self.assertEqual(ROOT[backend]["gun_whitelist"], {})
            self.assertNotIn("hired_gun_additional_whitelist", ROOT[backend])
            for rank in RANKS:
                section = ROOT["_tier_file_metadata"][rank]["tacz"] if backend == "tacz" else ROOT["tiers"][rank]["scguns"]
                self.assertNotIn("gun_blacklist", section)
                self.assertIn("gun_whitelist", section)

    def test_curated_rank_pools_have_valid_positive_weights(self):
        pools = []
        for rank in RANKS:
            pool = ROOT["tiers"][rank]["scguns"]["gun_whitelist"]
            self.assertTrue(pool)
            for gun, weight in pool.items():
                self.assertRegex(gun, r"^(scguns|jeg):[a-z0-9_/.]+$")
                self.assertIs(type(weight), int)
                self.assertGreater(weight, 0)
            pools.append(pool)
        self.assertEqual(len({tuple(p.items()) for p in pools}), 4)

    @unittest.skipUnless(os.environ.get("SCGUNS_API_JAR"), "Set SCGUNS_API_JAR to validate installed gun data")
    def test_curated_guns_exist_in_installed_catalog(self):
        with zipfile.ZipFile(os.environ["SCGUNS_API_JAR"]) as jar:
            for rank in RANKS:
                for gun in ROOT["tiers"][rank]["scguns"]["gun_whitelist"]:
                    namespace, path = gun.split(":", 1)
                    if namespace != "scguns": continue
                    data = json.loads(jar.read(f"data/{namespace}/guns/{path}.json"))
                    self.assertIn("general", data)

    def test_blacklist_reaches_input_selection_and_final_shot(self):
        def java(name):
            return (PROJECT / "src/main/java/club/someoneice/humangunner" / name).read_text(encoding="utf-8")
        self.assertIn("HumanGunAcceptance.acceptsOwnerInput(stack)", java("HumanInventoryContainer.java"))
        self.assertIn("HumanGunAcceptance.accepts(stack)", java("AmmoWeaponSelection.java"))
        self.assertIn("GunFireAuthorization.allows(human, weapon, target)", java("CompositeGunSupport.java"))
        self.assertIn("!HumanGunAcceptance.accepts(weapon)", java("GunFireAuthorization.java"))
        self.assertIn("HumanGunAcceptance.accepts(gun)", java("HumanAmmo.java"))
        self.assertNotIn("getNamespace", java("HumanGunAcceptance.java"))
