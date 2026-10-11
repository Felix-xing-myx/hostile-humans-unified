"""Optional-client and shared-renderer boundaries; no Minecraft execution."""
import json
import os
import unittest
import zipfile
from pathlib import Path
from test_scguns_contract import public_methods

ROOT = Path(__file__).resolve().parents[3]
JAVA = ROOT / 'src/main/java'
RENDER = JAVA / 'com/craftix/hostile_humans/client/renderer'
ADAPTER = JAVA / 'club/someoneice/humangunner'


class TaczEffectsContract(unittest.TestCase):
    @unittest.skipUnless(os.environ.get('TACZ_API_JAR'), 'Set TACZ_API_JAR for installed API verification')
    def test_installed_native_effect_and_aim_contracts(self):
        contracts = {
            'api/entity/IGunOperator': [('getSynIsAiming', '()Z')],
            'api/event/common/GunShootEvent': [('getShooter', '()Lnet/minecraft/world/entity/LivingEntity;'),
                                             ('getLogicalSide', '()Lnet/minecraftforge/fml/LogicalSide;')],
            'client/model/functional/MuzzleFlashRender': [('render', '(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/item/ItemDisplayContext;II)V')],
            'client/model/SlotModel': [('<init>', '(Z)V')],
            'api/item/IGun': [('getBuiltinAttachment', '(Lnet/minecraft/world/item/ItemStack;Lcom/tacz/guns/api/item/attachment/AttachmentType;)Lnet/minecraft/world/item/ItemStack;')],
        }
        with zipfile.ZipFile(os.environ['TACZ_API_JAR']) as jar:
            for name, methods in contracts.items():
                available = public_methods(jar.read('com/tacz/guns/' + name + '.class'))
                for method in methods:
                    self.assertIn(method, available, name)

    def test_flash_is_optional_client_hook_with_capability_gate(self):
        config = json.loads((ROOT / 'src/main/resources/hostile_humans_unified.mixins.json').read_text(encoding='utf-8'))
        self.assertIn('TaczNpcMuzzleFlashMixin', config['client'])
        self.assertNotIn('TaczNpcMuzzleFlashMixin', config['mixins'])
        plugin = (ADAPTER / 'compat/OptionalMixinPlugin.java').read_text(encoding='utf-8')
        self.assertIn('.TaczNpcMuzzleFlashMixin', plugin)
        self.assertIn('render(Lcom/mojang/blaze3d/vertex/PoseStack;', plugin)
        integration = (ADAPTER / 'TaczIntegration.java').read_text(encoding='utf-8')
        self.assertLess(integration.index('if (FMLEnvironment.dist == Dist.CLIENT)'),
                        integration.index('TaczNpcEffects.register()'))

    def test_flash_does_not_mutate_native_player_state_or_explode_twice(self):
        effects = (RENDER / 'TaczNpcEffects.java').read_text(encoding='utf-8')
        self.assertNotIn('MuzzleFlashRender.onShoot(', effects)
        self.assertNotIn('MuzzleFlashRender.isSelf', effects)
        self.assertNotIn('createExplosion(', effects)
        self.assertIn('event.getLogicalSide().isClient()', effects)
        self.assertIn('instanceof Human human', effects)
        self.assertIn('OculusCompat.isRenderShadow()', effects)
        self.assertIn('getBuiltinAttachment', effects)

    def test_model_delegate_only_captures_and_drawing_follows_item_render(self):
        effects = (RENDER / 'TaczNpcEffects.java').read_text(encoding='utf-8')
        capture = effects.split('private static void captureMuzzle(', 1)[1].split('private static void drawFlash(', 1)[0]
        self.assertIn('captured.last().pose().set(poses.last().pose())', capture)
        self.assertNotIn('getBuffer(', capture)
        layer = (RENDER / 'HumanAnimatedItemInHandLayer.java').read_text(encoding='utf-8')
        self.assertIn('finally { TaczNpcEffects.end(previous); }', layer)

    def test_aim_reads_sustained_server_intent_and_shield_default_keeps_40_percent(self):
        sample = (RENDER / 'TaczNativeAnimation.java').read_text(encoding='utf-8')
        self.assertIn('operator.getSynIsAiming()', sample)
        animator = (RENDER / 'TaczNpcAnimator.java').read_text(encoding='utf-8')
        self.assertIn('TaczAnimationPolicy.blendAim', animator)
        self.assertNotIn('if (!action || missingAction || sample.bolting()', animator)
        defaults = json.loads((ROOT / 'src/main/resources/defaults/hostile_humans_unified.json').read_text(encoding='utf-8'))
        self.assertEqual(defaults['tacz']['shield_damage_multiplier'], .4)
        self.assertEqual(defaults['tacz']['offhand_shield_damage_reduction'], .2)
