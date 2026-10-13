"""JEG optional-boundary and shipped pool compatibility, without a Java build."""
import json
import os
import sys
import unittest
import zipfile
from pathlib import Path
ROOT = Path(__file__).resolve().parents[3]
PKG = ROOT / "src/main/java/club/someoneice/humangunner"
DEFAULT = json.loads((ROOT / "src/main/resources/defaults/hostile_humans_unified.json").read_text(encoding="utf-8"))
JAR = os.environ.get("JEG_API_JAR")

class JegContract(unittest.TestCase):
    def test_optional_independent_registration_and_shared_pools(self):
        support = (PKG / "CompositeGunSupport.java").read_text(encoding="utf-8")
        self.assertIn('ModList.get().isLoaded("jeg")', support)
        pools = (PKG / "GunSpawnPools.java").read_text(encoding="utf-8")
        self.assertIn('"tacz", "scguns", "jeg"', pools)
        self.assertIn('backend.equals("jeg") ? "scguns" : backend', pools)
        self.assertIn("GunSpawnPools.CONFIG_SECTIONS", (PKG / "SplitConfigFiles.java").read_text(encoding="utf-8"))
        self.assertNotIn("jeg", DEFAULT) # one configuration section, distinct runtime provider

    def test_regular_guns_only_in_default_pools(self):
        configured = DEFAULT["scguns"]["jeg_gun_types"]
        for rank in ("roamer", "tier1", "tier2", "tier3"):
            guns = DEFAULT["tiers"][rank]["scguns"]["gun_whitelist"]
            jeg = {k:v for k,v in guns.items() if k.startswith("jeg:")}
            self.assertTrue(jeg)
            self.assertTrue(all(k in configured and v > 0 for k,v in jeg.items()))
            self.assertFalse(any(k.endswith(("bow", "rocket_launcher", "holy_shotgun", "book_of_psalms")) for k in jeg))

    @unittest.skipUnless(JAR, "Set JEG_API_JAR for actual upstream bytecode and gun data")
    def test_native_public_contract_and_hook_counts(self):
        sys.path.insert(0, str(ROOT / "tools"))
        from audit_jeg_compatibility import audit
        self.assertGreater(audit(Path(JAR), Path(r"D:\Java\17\bin\javap.exe")), 25)

    @unittest.skipUnless(JAR, "Set JEG_API_JAR for actual gun data")
    def test_shipped_pool_uses_real_ballistic_gun_definitions(self):
        ammo = {"jeg:pistol_ammo", "jeg:rifle_ammo", "jeg:shotgun_shell", "jeg:handmade_shell"}
        with zipfile.ZipFile(JAR) as jar:
            for rank in ("roamer", "tier1", "tier2", "tier3"):
                for gun in DEFAULT["tiers"][rank]["scguns"]["gun_whitelist"]:
                    if not gun.startswith("jeg:"): continue
                    data = json.loads(jar.read("data/jeg/guns/" + gun.split(":")[1] + ".json"))
                    self.assertIn(data["projectile"]["item"], ammo)
                    self.assertGreater(data["projectile"]["damage"], 0)

    def test_no_new_tactical_goal_or_stage_damage_multiplier(self):
        source = (PKG / "JegIntegration.java").read_text(encoding="utf-8")
        self.assertIn("new GunnerGoal<>(human)", source)
        self.assertIn("HumanGrowth.scaleGunDamage", source)
        self.assertIn("HumanGrowth.gunSpreadMultiplier", source)
        self.assertNotIn("stagePercent", source)
        self.assertIn("HumanAmmo.consumeMagazines", source)
        self.assertIn("GunFireAuthorization.allows", source)

    def test_inventory_supply_uses_separate_reserve_and_native_capacity(self):
        self.assertEqual(DEFAULT["scguns"]["jeg_inventory_fed_capacity"], 100)
        source = (PKG / "JegIntegration.java").read_text(encoding="utf-8")
        self.assertIn('tag.contains(VIRTUAL_AMMO) ? VIRTUAL_AMMO : "AmmoCount"', source)
        self.assertIn('putInt(VIRTUAL_AMMO, bounded)', source)
        self.assertIn('putInt("AmmoCount", Math.min(bounded,', source)
        self.assertIn('GunModifierHelper.getModifiedAmmoCapacity(stack, definition(stack))', source)

    @unittest.skipUnless(JAR, "Set JEG_API_JAR for expanded combat gun coverage")
    def test_extended_combat_guns_have_supported_profiles(self):
        import re
        code = (PKG / "JegIntegration.java").read_text(encoding="utf-8")
        ammo_block = code.split("SUPPORTED_COMBAT_AMMO = Set.of(", 1)[1].split(");", 1)[0]
        supported = {ns + ":" + name for ns,name in re.findall(r'new ResourceLocation\("([a-z_]+)", "([a-z_]+)"\)', ammo_block)}
        expected = {"blossom_rifle", "holy_shotgun", "hollenfire_mk2", "soulhunter_mk2", "subsonic_rifle", "supersonic_shotgun", "hypersonic_cannon", "flamethrower", "grenade_launcher", "rocket_launcher", "atlantean_spear", "typhoonee"}
        with zipfile.ZipFile(JAR) as jar:
            for name in expected:
                gun = json.loads(jar.read("data/jeg/guns/" + name + ".json"))
                self.assertIn(gun["projectile"]["item"], supported, name)
                self.assertIn("jeg:" + name, DEFAULT["scguns"]["jeg_gun_types"], name)
        self.assertTrue(supported.isdisjoint({"minecraft:bread", "minecraft:air", "minecraft:arrow", "jeg:flare"}))

    def test_area_effects_filter_before_status_and_keep_damage_source_type(self):
        collision = (PKG / "mixin/JegWaterAreaCollisionMixin.java").read_text(encoding="utf-8")
        damage = (PKG / "mixin/JegSplashDamageAttributionMixin.java").read_text(encoding="utf-8")
        self.assertIn("passesThroughFromOwner(getOwner(), candidate)", collision)
        self.assertIn("original.typeHolder()", damage)
        self.assertIn("owner instanceof Human", damage)
        relations = (PKG / "HumanProjectileRelations.java").read_text(encoding="utf-8")
        self.assertIn("JegCompat.projectileOwner(direct)", relations)
        self.assertIn("passesThroughFromOwner(nativeOwner, candidate)", relations)

    def test_short_lived_projectile_reach_is_shared_with_final_fire_permission(self):
        code = (PKG / "GunRangePolicy.java").read_text(encoding="utf-8")
        self.assertIn("support.maximumProjectileReach(weapon)", code)
        for name in ("GunFireAuthorization", "BodyguardProtection", "AdaptiveCombatGoal", "GunnerGoal"):
            text = (PKG / (name + ".java")).read_text(encoding="utf-8")
            self.assertTrue("GunRangePolicy.forWeapon(" in text or "GunRangePolicy.firingReach(weapon," in text or "GunRangePolicy.firingReach(" in text, name)

    def test_human_ai_ownership_blocks_unpaid_native_actions_only_for_humans(self):
        ownership = (PKG / "mixin/JegHumanAiOwnershipMixin.java").read_text(encoding="utf-8")
        native = (PKG / "mixin/JegNativeGunGoalMixin.java").read_text(encoding="utf-8")
        config = json.loads((ROOT / "src/main/resources/hostile_humans_unified.mixins.json").read_text(encoding="utf-8"))
        self.assertIn("JegHumanAiOwnershipMixin", config["mixins"])
        self.assertIn("JegNativeGunGoalMixin", config["mixins"])
        for entry in ("reassessWeaponGoal", "onLivingUpdate", "onEntityJoinWorld", "onSpecialSpawn"):
            self.assertIn(entry, ownership)
        self.assertEqual(ownership.count("instanceof Human"), 4)
        self.assertIn('method = "m_8036_()Z"', native)
        self.assertIn('method = "m_8037_()V"', native)
        self.assertIn("if (shooter instanceof Human) cir.setReturnValue(false)", native)
        self.assertIn("if (shooter instanceof Human) ci.cancel()", native)
        self.assertNotIn("new Throwable", native)
        self.assertNotIn("UnifiedConfig", ownership) # ownership is enforced even when integration is disabled

    def test_paid_throwables_use_one_scheduler_and_no_foreign_types_in_core(self):
        facade = (PKG / "TacticalThrowables.java").read_text(encoding="utf-8")
        planner = (PKG / "ScgunsThrowables.java").read_text(encoding="utf-8")
        main = (PKG / "HumanGunner.java").read_text(encoding="utf-8")
        self.assertEqual(main.count("TacticalThrowables.tick(human)"), 1)
        self.assertNotIn("ScgunsCompat.tick(human)", main)
        self.assertEqual(facade.count("ScgunsThrowables.tick(human)"), 1)
        for text in (facade, planner):
            self.assertNotIn("import ttv.migami", text)
            self.assertNotIn("import top.ribs", text)
        self.assertLess(planner.index("if (!human.level().addFreshEntity(projectile)) return"), planner.index("remainder.shrink(1)"))
        self.assertIn("human.getData().setInventoryItem(slot, remainder)", planner)
        self.assertIn("TacticalThrowables.supported(stack)", (PKG / "HumanLootManager.java").read_text(encoding="utf-8"))
        self.assertIn("TacticalThrowables.supported(stack)", (PKG / "SpawnPotionConfig.java").read_text(encoding="utf-8"))

    def test_only_grenade_and_water_bomb_spawn_while_other_hand_items_are_manual(self):
        allowed = set(DEFAULT["scguns"]["throwables"]["allowed_items"])
        self.assertEqual({item for item in allowed if item.startswith("jeg:")}, {"jeg:grenade", "jeg:water_bomb", "jeg:molotov_cocktail", "jeg:smoke_grenade", "jeg:stun_grenade"})
        for rank in ("roamer", "tier1", "tier2", "tier3"):
            pool = DEFAULT["tiers"][rank]["potions"]["tactical"]["pool"]
            self.assertEqual({row["item"]:row["weight"] for row in pool if row["item"].startswith("jeg:")}, {"jeg:grenade":3, "jeg:water_bomb":3})
        backend = (PKG / "JegThrowables.java").read_text(encoding="utf-8")
        self.assertIn("!(item instanceof ExplosiveChargeItem)", backend)
        self.assertIn(".create(stack.copyWithCount(1), human.level(), human, fuse)", backend)
        self.assertIn(".05F, true, 20", backend)
        self.assertIn(".05F, false, 120", backend)
        self.assertIn(".05F, false, 60", backend)

    def test_water_bomb_preserves_human_effect_owner_without_changing_players(self):
        code = (PKG / "mixin/JegWaterBombOwnerMixin.java").read_text(encoding="utf-8")
        self.assertIn("owner instanceof Human human ? human : original", code)
        self.assertIn("WhirpoolEntity;createLandWhirpool", code)
        self.assertIn("index = 1, require = 1, allow = 1", code)
