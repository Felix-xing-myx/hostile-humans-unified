import json
import unittest
from source_paths import SourcePath as Path
ROOT = Path(__file__).resolve().parents[3]
JAVA = ROOT / 'src/main/java/dev/felix/hostilehumans'

def source(name):
    return (JAVA / (name + '.java')).read_text(encoding='utf-8')

class ControlledShotAudio(unittest.TestCase):
    def test_only_live_controlled_camera_subject_is_replaced(self):
        s = source('TaczMonitorShotSoundClient')
        for guard in ['HumanCameraView.attached(human)', 'HumanMonitorClient.takingOver(human)',
                      'message.getEntityId() != human.getId()', 'isFirstPerson()', 'human.isSilent()',
                      'human.isRemoved()', 'human.level() != client.level']:
            self.assertIn(guard, s)
    def test_uses_authored_message_and_preserves_silencer_and_gun_identity(self):
        s = source('TaczMonitorShotSoundClient')
        for field in ['message.getGunDisplayId()', 'message.getGunId()', 'message.getVolume()',
                      'message.getPitch()', 'message.getDistance()', 'SoundManager.SILENCE_3P_SOUND',
                      'SoundManager.SILENCE_SOUND', 'display.getSounds(firstPerson)']:
            self.assertIn(field, s)
        for wrong_source in ['getMainHandItem()', 'GunShootEvent', 'GunFireEvent', 'queueWeaponInput', 'sendToServer']:
            self.assertNotIn(wrong_source, s)
    def test_success_consumes_one_message_and_missing_sound_retains_native(self):
        m = source('TaczMonitorShotSoundMixin')
        self.assertIn('if (TaczMonitorShotSoundClient.play(message)) ci.cancel()', m)
        self.assertIn('playMessageSound(Lcom/tacz/guns/network/message/ServerMessageSound;)V', m)
        self.assertIn('require = 1, allow = 1', m)
        s = source('TaczMonitorShotSoundClient')
        self.assertIn('getResource(new ResourceLocation(', s)
        self.assertIn('message.getDistance(), false) != null', s)
        self.assertIn('else return false;', s)
    def test_client_only_and_public_abi_guard(self):
        config = json.loads((ROOT/'src/main/resources/hostile_humans_unified.mixins.json').read_text(encoding='utf-8'))
        self.assertIn('TaczMonitorShotSoundMixin', config['client'])
        self.assertNotIn('TaczMonitorShotSoundMixin', config['mixins'])
        self.assertIn('SpectatorSoundContract.supportsShotPlayback(target)', source('OptionalMixinPlugin'))
        self.assertIn('Opcodes.ACC_PUBLIC', source('SpectatorSoundContract'))

if __name__ == '__main__': unittest.main()
