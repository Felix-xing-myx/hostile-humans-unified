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
        self.assertEqual(data["growth"]["stage_percent"], [50, 60, 70, 80, 90, 100])
        for rank, hp in zip(("roamer", "tier1", "tier2", "tier3"), (40, 60, 80, 100)):
            attributes = data["tiers"][rank]["attributes"]
            self.assertEqual(attributes["max_health"], hp)
            self.assertNotIn("health_min", attributes)
            self.assertNotIn("health_max", attributes)

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
        self.assertEqual(source.count("HumanGrowth.multiplier(humanAttacker)"), 3)
        hurt = source.split("private static void onLivingHurt", 1)[1].split("private static void onLivingDamage", 1)[0]
        self.assertLess(hurt.index("GunSupport.get().isBullet"), hurt.index("HumanGrowth.multiplier"))
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

    def test_progress_is_discrete_and_experience_is_one_percent(self):
        policy = read("GrowthProgression.java")
        self.assertIn("level++", policy)
        self.assertIn("config.stageCost(level)", policy)
        self.assertIn("experience += gains", policy)
        self.assertIn("config.experienceCost()", policy)
        self.assertIn("GrowthProgression.advance", read("HumanGrowth.java"))


if __name__ == "__main__":
    unittest.main(verbosity=2)
