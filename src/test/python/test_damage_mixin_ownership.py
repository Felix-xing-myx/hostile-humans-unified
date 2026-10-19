"""Check method declarations as well as call owners; neither compilation nor a call-owner test binds @Shadow."""
import os
from pathlib import Path
import sys
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "tools"))
from verify_package import ClassFile

PKG = ROOT / "src/main/java/club/someoneice/humangunner"

class DamageMixinOwnership(unittest.TestCase):
    def test_player_calls_inherited_armor_without_a_player_shadow(self):
        text = (PKG / "mixin/PlayerDamagePipelineMixin.java").read_text(encoding="utf-8")
        self.assertIn("PlayerDamagePipelineMixin extends LivingEntity", text)
        self.assertNotRegex(text, r"(?m)^\s*@Shadow\b")
        self.assertIn("super(type, level)", text)
        self.assertIn("normal -> getDamageAfterArmorAbsorb(source, (float) normal)", text)
        self.assertEqual(text.count("HumanDamagePipeline.armor("), 1)
        self.assertIn("require = 1, allow = 1", text)

    def test_actual_runtime_parent_owns_the_armor_declaration(self):
        path = Path(os.environ.get("HHU_FORGE_RUNTIME_JAR", r"E:\MC\game\.minecraft\libraries\net\minecraftforge\forge\1.20.1-47.4.21\forge-1.20.1-47.4.21-client.jar"))
        if not path.exists(): self.skipTest("Supply HHU_FORGE_RUNTIME_JAR for declaration ownership checks")
        with zipfile.ZipFile(path) as jar:
            living = ClassFile(jar.read("net/minecraft/world/entity/LivingEntity.class"))
            player = ClassFile(jar.read("net/minecraft/world/entity/player/Player.class"))
        method = ("m_21161_", "(Lnet/minecraft/world/damagesource/DamageSource;F)F")
        self.assertIn(method, living.methods)
        self.assertNotIn(method, player.methods)
        self.assertIn(living.name, player.parents)
        self.assertIn(("m_6475_", "(Lnet/minecraft/world/damagesource/DamageSource;F)V"), player.methods)

    def test_other_new_shadows_are_declared_by_their_actual_target(self):
        path = Path(os.environ.get("SCGUNS_API_JAR", r"E:\MC\game\.minecraft\versions\1.20.1-Forge_47.4.21\mods\ScorchedGuns-0.5.5-1.20.1.jar"))
        if not path.exists(): self.skipTest("Supply SCGUNS_API_JAR for optional target declaration checks")
        with zipfile.ZipFile(path) as jar:
            target = ClassFile(jar.read("top/ribs/scguns/entity/projectile/ProjectileEntity.class"))
        self.assertIn(("getShooter", "()Lnet/minecraft/world/entity/LivingEntity;"), target.methods)
        self.assertIn(("getWeapon", "()Lnet/minecraft/world/item/ItemStack;"), target.methods)

if __name__ == "__main__": unittest.main()
