"""Source/API regression checks; no Java compilation or gameplay simulation."""
import os
from pathlib import Path
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[3]
PKG = ROOT / "src/main/java/club/someoneice/humangunner"


class GunMeleeProtectionContract(unittest.TestCase):
    def test_urgent_shot_uses_native_cancel_sentinel(self):
        source = (PKG / "TaczGunMechanics.java").read_text(encoding="utf-8")
        block = source.split("static void prepareUrgentCloseShot(", 1)[1].split("private static", 1)[0]
        self.assertIn("meleePrepTickCount = -1;", block)
        self.assertNotIn("meleePrepTickCount = 0;", source)

    def test_optional_native_event_is_independent_of_integration_toggle(self):
        source = (PKG / "GunMeleeProtection.java").read_text(encoding="utf-8")
        self.assertIn('ModList.get().isLoaded("tacz")', source)
        self.assertIn("TaczGunItemIdentity.registerMeleeProtection()", source)
        self.assertNotIn("taczEnabled", source)
        adapter = (PKG / "TaczGunItemIdentity.java").read_text(encoding="utf-8")
        self.assertIn("event.getShooter() instanceof Human", adapter)
        self.assertIn("event.setCanceled(true)", adapter)
        self.assertIn("GunMeleeProtection.register()", (PKG / "HumanGunner.java").read_text(encoding="utf-8"))

    def test_final_guard_does_not_cancel_bullets_or_effect_damage(self):
        source = (PKG / "GunMeleeProtection.java").read_text(encoding="utf-8")
        for condition in ("human.humanGunner$canMeleeAttack()", "source.getDirectEntity() != human",
                          "DamageTypeTags.IS_PROJECTILE", "DamageTypeTags.IS_EXPLOSION", "isBullet(source)",
                          "DamageTypes.MOB_ATTACK", '"human_with_name"', '"human"'):
            self.assertIn(condition, source)
        self.assertIn("EventPriority.HIGHEST", source)
        self.assertIn("!event.getEntity().level().isClientSide", source)
        self.assertNotIn("setHealth", source)

    def test_native_zero_count_really_executes_attack_and_event_can_cancel(self):
        jar = os.environ.get("TACZ_API_JAR")
        if not jar:
            self.skipTest("TaCZ API jar not supplied")
        javap = Path("D:/Java/17/bin/javap.exe")
        output = subprocess.check_output([str(javap), "-classpath", jar, "-p", "-c",
                                         "com.tacz.guns.entity.shooter.LivingEntityMelee"], text=True)
        scheduled = output.split("public void scheduleTickMelee();", 1)[1].split("public long", 1)[0]
        self.assertRegex(scheduled, r"meleePrepTickCount:I\s+\d+: ifne")
        self.assertIn("AbstractGunItem.melee:", scheduled)
        self.assertIn("iconst_m1", scheduled)
        start = output.split("public void melee();", 1)[1].split("public void scheduleTickMelee", 1)[0]
        self.assertLess(start.index("IEventBus.post:"), start.index("ServerMessageGunMelee"))
        api = subprocess.check_output([str(javap), "-classpath", jar, "-c",
                                       "com.tacz.guns.api.event.common.GunMeleeEvent"], text=True)
        cancel = api.split("boolean isCancelable();", 1)[1].split("public", 1)[0]
        self.assertIn("iconst_1", cancel)
