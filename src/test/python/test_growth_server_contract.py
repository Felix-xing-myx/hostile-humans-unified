"""Read-only integration checks. These inspect sources and do not execute Java or Minecraft."""
import json
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
JAVA = ROOT / "src/main/java/club/someoneice/humangunner"


def read(name):
    return (JAVA / name).read_text(encoding="utf-8")


class GrowthServerContractTest(unittest.TestCase):
    def test_one_global_switch_and_single_health_default(self):
        data = json.loads((ROOT / "src/main/resources/defaults/hostile_humans_unified.json").read_text(encoding="utf-8"))
        self.assertNotIn("equipment_growth", data)
        self.assertTrue(data["growth"]["enabled"])
        self.assertEqual(data['spawning']['progression']['first_spawn_day_by_tier']['roamer'], 2)
        self.assertEqual(data["growth"]["stage_percent"], [50, 60, 70, 80, 90, 100])
        self.assertEqual(data["growth"]["damage_stage_percent"], [75, 80, 85, 90, 95, 100])
        for rank, hp in zip(("roamer", "tier1", "tier2", "tier3"), (40, 40, 60, 80)):
            attributes = data["tiers"][rank]["attributes"]
            self.assertEqual(attributes["max_health"], hp)
            self.assertNotIn("health_min", attributes)
            self.assertNotIn("health_max", attributes)
            self.assertEqual(data['tiers'][rank]['growth_profile']['duration_days'], 50)
        for rank, attack, trident in zip(("roamer", "tier1", "tier2", "tier3"),
                                         (1.2, 1.4, 1.6, 1.8), (1, 1.1, 1.2, 1.3)):
            damage = data['tiers'][rank]['damage_multipliers']
            self.assertEqual(damage['melee_damage_multiplier'], attack)
            self.assertEqual(damage['bow_damage_multiplier'], dict(roamer=1.1, tier1=1.2, tier2=1.3, tier3=1.4)[rank])
            self.assertEqual(damage['crossbow_damage_multiplier'], dict(roamer=1.4, tier1=1.5, tier2=1.6, tier3=1.7)[rank])
            self.assertNotIn('arrow_damage_multiplier', damage)
            self.assertEqual(damage['thrown_trident_damage_multiplier'], trident)
            self.assertEqual(damage['damage_taken_multiplier'], 1)

    def test_vanilla_mode_suppresses_one_shared_growth_policy(self):
        self.assertIn("enabled = configuredEnabled && !vanillaMode", read("GrowthConfig.java"))
        self.assertIn('object(root, "tiers"), defaults, vanillaMode)', read("UnifiedConfig.java"))
        self.assertIn("new EquipmentGrowthPolicy(enabled,", read("GrowthConfig.java"))
        self.assertIn("new GrowthSnapshot(config.enabled()", read("HumanGrowth.java"))
        self.assertIn("GrowthRewards.configurationReloaded(instance.growth().enabled())", read("UnifiedConfig.java"))

    def test_damage_curve_is_separate_from_health_and_never_used_by_firearms(self):
        growth = read("HumanGrowth.java")
        damage = growth.split('static double multiplier(Human human)', 1)[1].split('static double gunSpreadMultiplier', 1)[0]
        health = growth.split('static double fullHealth(Human human)', 1)[1].split('static void tick', 1)[0]
        self.assertIn('config.damageStagePercent(', damage)
        self.assertNotIn('config.stagePercent(', damage)
        self.assertIn('config.stagePercent(', health)
        self.assertNotIn('damageStagePercent', health)
        for backend in ('TaczIntegration.java', 'ScgunsIntegration.java'):
            text = read(backend)
            self.assertEqual(text.count('HumanGrowth.scaleGunDamage(attacker,'), 1)
            self.assertNotIn('HumanGrowth.gunDamageMultiplier(attacker)', text)
            self.assertNotIn('HumanGrowth.multiplier(', text)
            self.assertNotIn('damageStagePercent', text)
        gun_curve = read('GrowthConfig.java').split('public double gunDamageMultiplier(int percent)', 1)[1].split('public double damageUnit()', 1)[0]
        self.assertIn('Math.max(0, Math.min(1,', gun_curve)
        self.assertNotIn('stagePercent', gun_curve)
        self.assertIn('!growth.has("damage_stage_percent")', read('SplitConfigFiles.java'))

    def test_birth_snapshot_not_recomputed_from_current_date(self):
        source = read("HumanGrowth.java")
        self.assertIn("loadedFromDisk", source)
        self.assertIn('if (!LOADED.remove(human)) markBirth', source)
        self.assertIn('human.hasOwner() || !human.getPersistentData().contains(birthday)', source)
        tick = source.split("static void tick(Human human)", 1)[1].split("static void award", 1)[0]
        self.assertNotIn("timeNear", tick)
        self.assertNotIn("stageAt", tick)

    def test_only_server_ticks_and_existing_nonfirearm_branches_scale(self):
        source = read("HumanGunner.java")
        self.assertIn("human && !human.level().isClientSide", source)
        self.assertEqual(source.count("HumanDamageScaling.outgoing("), 1)
        hurt = read("HumanDamageScaling.java")
        self.assertEqual(hurt.count("HumanGrowth.multiplier(attacker)"), 1)
        self.assertLess(hurt.index("GunSupport.get().isBullet"), hurt.index("HumanGrowth.multiplier"))
        for branch in ("meleeDamage()", "tridentDamage()", "bowDamage()"):
            self.assertIn(branch, hurt)
        self.assertIn("GrowthCombat::onTick", source)

    def test_actual_health_boundary_registered_with_srg_signature(self):
        config = json.loads((ROOT / "src/main/resources/hostile_humans_unified.mixins.json").read_text(encoding="utf-8"))
        self.assertIn("LivingEntityGrowthDamageMixin", config["mixins"])
        mixin = read("mixin/LivingEntityGrowthDamageMixin.java")
        self.assertEqual(mixin.count('m_6469_(Lnet/minecraft/world/damagesource/DamageSource;F)Z'), 2)
        self.assertIn('@At("HEAD")', mixin)
        self.assertIn('@At("RETURN")', mixin)
        combat = read("GrowthCombat.java")
        self.assertIn("hit.before - target.getHealth()", combat)
        self.assertIn("target.getHealth()) - hit.nested", combat)
        self.assertIn("hit.observedHealth ? hit.healthLoss", combat)
        self.assertEqual(mixin.count('m_21153_(F)V'), 2)
        self.assertIn("beforeHealthChange", mixin)
        self.assertIn("afterHealthChange", mixin)

    def test_first_lethal_hit_and_death_cancellation_confirmation(self):
        combat = read("GrowthCombat.java")
        death = combat.split("static void onDeath", 1)[1].split("static void onLeave", 1)[0]
        self.assertIn("event.isCanceled()", death)
        self.assertIn("event.getSource().getEntity() instanceof Human", death)
        leave = combat.split("static void onLeave", 1)[1].split("static void settle", 1)[0]
        self.assertIn("Entity.RemovalReason.KILLED", leave)
        self.assertIn("CONFIRMED.add(target)", leave)
        settle = combat.split("static void settle", 1)[1].split("static void onTick", 1)[0]
        self.assertIn("data.getBoolean(SETTLED)", settle)
        self.assertLess(settle.index("data.putBoolean(SETTLED, true)"), settle.index("store.enqueue"))
        self.assertIn("ledger.isOwnedRecord", settle)

    def test_rule_snapshot_and_strict_bonus_boundary(self):
        combat = read("GrowthCombat.java")
        self.assertIn('root.putDouble("damage_unit", config.damageUnit())', combat)
        self.assertIn('double damageUnit = root.getDouble("damage_unit")', combat)
        self.assertIn("total > bonusThreshold", combat)
        self.assertNotIn("isBoss", combat)
        self.assertNotIn("getEntities", combat)

    def test_immediate_player_respawn_confirms_old_death_without_rewarding_generic_discard(self):
        combat = read("GrowthCombat.java")
        clone = combat.split("static void onPlayerClone", 1)[1].split("static void settle", 1)[0]
        self.assertIn("event.isWasDeath()", clone)
        self.assertIn("CONFIRMED.add(event.getOriginal())", clone)
        self.assertIn("GrowthCombat::onPlayerClone", read("HumanGunner.java"))
        self.assertIn("target instanceof net.minecraft.server.level.ServerPlayer", combat)

    def test_disable_invalidates_unfinished_fights_without_destroying_earned_rewards(self):
        rewards = read("GrowthRewards.java")
        self.assertIn("configurationReloaded(boolean enabled)", rewards)
        self.assertIn("observedDisabledRevision != disabledRevision", rewards)
        self.assertNotIn("pending.clear()", rewards)
        self.assertIn("if (!store.enabled) return false", rewards)
        self.assertIn("GrowthRewards.configurationReloaded", read("UnifiedConfig.java"))

    def test_external_health_ownership_and_no_free_healing(self):
        health = read("GrowthHealth.java")
        self.assertIn('state.putBoolean("owns_health", false)', health)
        self.assertIn("MaxHealthCompatibilityPolicy.retainsOwnership", health)
        policy = read("MaxHealthCompatibilityPolicy.java")
        self.assertIn("return owned &&", policy)
        self.assertIn("VanillaModePolicy.clampedHealth", health)
        self.assertNotIn("removeModifier", health)

    def test_server_reload_reapplies_weapon_only_base_without_resetting_other_attributes(self):
        source = read("HumanGrowth.java")
        self.assertIn("if (changed) TierAttributes.applyCombatBase(human)", source)
        helper = read("TierAttributes.java").split("static void applyCombatBase", 1)[1].split("private static void set", 1)[0]
        self.assertIn("Attributes.ATTACK_DAMAGE", helper)
        self.assertIn("VanillaModePolicy.attackBase", helper)
        self.assertNotIn("Attributes.MOVEMENT_SPEED", helper)

    def test_firearm_growth_uses_existing_damage_and_both_accuracy_paths(self):
        tacz = read("TaczIntegration.java")
        self.assertEqual(tacz.count("HumanGrowth.scaleGunDamage(attacker,"), 1)
        self.assertNotIn("HumanGrowth.multiplier", tacz)
        self.assertNotIn("gunDamageMultiplier", read("HumanGunner.java"))
        self.assertIn("HumanGrowth.gunSpreadMultiplier(human)", read("RangedAccuracy.java"))
        for goal in ("GunnerGoal.java", "AdaptiveCombatGoal.java"):
            self.assertIn("RangedAccuracy.gunSpreadDegrees", read(goal))
        self.assertIn('config.experienceCost(state.getInt("experience_percent"))', read("HumanGrowth.java"))

    def test_banded_cost_and_gun_defaults_are_global(self):
        data = json.loads((ROOT / "src/main/resources/defaults/hostile_humans_unified.json").read_text(encoding="utf-8"))
        experience = data["growth"]["experience"]
        self.assertNotIn("cost", experience)
        self.assertEqual(experience["below_base_cost"], 3)
        self.assertEqual(experience["cost_by_band"], [3, 5, 8, 12, 16])
        firearms = data["growth"]["firearms"]
        self.assertEqual(firearms["spread_reduction_per_band_percent"], 5)
        self.assertEqual(firearms["minimum_spread_percent"], 75)
        self.assertEqual(firearms["maximum_damage_bonus_percent"], 10)
        for tier in data["tiers"].values():
            self.assertNotIn("growth", tier)

    def test_progress_is_discrete_and_experience_is_one_percent(self):
        policy = read("GrowthProgression.java")
        self.assertIn("level++", policy)
        self.assertIn("config.stageCost(level)", policy)
        self.assertIn("experience++", policy)
        self.assertIn("config.experienceCost(experience)", policy)
        self.assertIn("GrowthProgression.advance", read("HumanGrowth.java"))


if __name__ == "__main__":
    unittest.main(verbosity=2)
