"""Native SC shield preemption must yield to the single hurt call for human AP bullets."""
import os
from source_paths import SourcePath as Path
import re
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[3]
PKG = ROOT / "src/main/java/dev/felix/hostilehumans"

class ScgunsPiercingShieldContract(unittest.TestCase):
    def test_adapter_preserves_native_path_without_replaying_damage(self):
        code = (PKG / "mixin/ScgunsHumanShieldMixin.java").read_text(encoding="utf-8")
        for gate in ("getShooter() instanceof Human", "GunSupport.isScgunsGunItem(getWeapon())",
                     'damageFlag("armor_penetration_enabled", true)', "Double.isFinite(fraction) && fraction > 0"):
            self.assertIn(gate, code)
        self.assertIn("ProjectileEntity.ProjectileHelper.handleShieldHit(victim, projectile, amount)", code)
        self.assertIn("require = 1, allow = 1", code)
        for forbidden in (".hurt(", "new DamageSource", ".setHealth(", ".setCanceled("):
            self.assertNotIn(forbidden, code)

    def test_actual_native_signature_and_preemptive_control_flow(self):
        jar = os.environ.get("SCGUNS_API_JAR")
        if not jar:
            self.skipTest("Scguns API jar not supplied")
        def bytecode(name):
            return subprocess.check_output(["D:/Java/17/bin/javap.exe", "-p", "-c", "-classpath", jar, name], text=True)
        projectile = bytecode("top.ribs.scguns.entity.projectile.ProjectileEntity")
        hit = projectile.split("protected void onHitEntity(net.minecraft.world.entity.Entity, net.minecraft.world.phys.Vec3, net.minecraft.world.phys.Vec3, net.minecraft.world.phys.Vec3, boolean);", 1)[1]
        hit = re.split(r"\n  (?:public|protected|private) ", hit, maxsplit=1)[0]
        native = hit.index("ProjectileHelper.handleShieldHit:(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;F)Z")
        hurt = hit.index("net/minecraft/world/entity/Entity.m_6469_:")
        self.assertLess(native, hurt)
        self.assertRegex(hit[native:hurt], r"ifne\s+361")
        helper = bytecode("top.ribs.scguns.entity.projectile.ProjectileEntity$ProjectileHelper")
        self.assertIn("handleShieldHit(net.minecraft.world.entity.Entity, net.minecraft.world.entity.Entity, float);", helper)
        self.assertIn("handleShieldHit(net.minecraft.world.entity.Entity, net.minecraft.world.entity.Entity, float, float);", helper)
        self.assertIn("class net/minecraft/world/entity/player/Player", helper)
        self.assertIn("float 0.3f", helper)
        self.assertIn("float 0.5f", helper)
        self.assertIn("bipush        12", helper)

if __name__ == "__main__": unittest.main()
