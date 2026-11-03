from source_paths import SourcePath as Path
import unittest

ROOT=Path(__file__).resolve().parents[3]/'src/main/java/dev/felix/hostilehumans'
def source(name): return (ROOT/(name+'.java')).read_text(encoding='utf-8')

class BackgroundAudioContract(unittest.TestCase):
    def test_background_work_never_discards_authoritative_audio_timeline(self):
        transactions=source('TaczHumanReloadTransactions')
        self.assertNotIn('GunCombatProfile.silent', transactions)
        self.assertIn('TaczReloadVisualTimeline.complete(human, clock.work)', transactions)
        self.assertIn('finish(human,data,completed)', transactions)
        self.assertIn('VISUAL_OWNERS.remove(data); // Retain the terminal receipt', transactions)

    def test_audio_tracks_original_session_not_held_item(self):
        for name in ('NativeReloadAudio','TaczReloadSoundClient'):
            body=source(name)
            if name == 'NativeReloadAudio':
                body = body.split('public static void tickBolt(', 1)[0] + body.split('private static void cancel(', 1)[1]
            self.assertNotIn('human.getMainHandItem()', body)
            self.assertNotIn('SpartanEquipmentCompat.isShield', body)
            self.assertIn('timeline.getLong("Session")', body)
            self.assertIn('timeline.getBoolean("Complete")', body)
            self.assertIn('pending.sounds.clear(); iterator.remove();', body)
            self.assertIn('stop(pending); iterator.remove();', body.replace('iterator.remove();continue','iterator.remove(); continue'))

    def test_shield_background_start_still_publishes_reload_notification(self):
        mixin=source('mixin/TaczHumanReloadMixin')
        start=mixin.split('private boolean hhu$paidStart(',1)[1].split('return started;',1)[0]
        self.assertIn('if (started && entity instanceof Human)', start)
        self.assertNotIn('SpartanEquipmentCompat',start)
        self.assertLess(start.index('TaczHumanReloadTransactions.start'),start.index('new ServerMessageGunReload'))

    def test_complete_receipt_freezes_clock_and_preserves_cancellation(self):
        timeline=source('TaczReloadVisualTimeline')
        self.assertIn('tag.putBoolean("Complete", true)', timeline)
        self.assertIn('human.setTaczReloadVisual(new CompoundTag())', timeline)
        resources=source('TaczPresentationResources')
        self.assertIn('partialTick,timeline.getBoolean("Complete")', resources)
        self.assertIn('TaczPresentationResources.workSeconds(human, 0)', source('TaczReloadSoundClient'))

if __name__=='__main__': unittest.main()
