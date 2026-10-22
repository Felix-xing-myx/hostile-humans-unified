"""Read-only progress wiring. No compiler, backend execution or game acceptance."""
from pathlib import Path
import re
import unittest
ROOT = Path(__file__).resolve().parents[3]
PKG = ROOT / 'src/main/java/club/someoneice/humangunner'
def source(name): return (PKG / (name + '.java')).read_text(encoding='utf-8')
def body(text, signature):
    start = text.index('{', text.index(signature)) + 1
    depth = 1
    for end in range(start, len(text)):
        depth += (text[end] == '{') - (text[end] == '}')
        if depth == 0: return text[start:end]
    raise AssertionError('unclosed method')
class ExecutionProgressContract(unittest.TestCase):
    def test_producers_cannot_advance_or_resolve_during_observation(self):
        for name, signature in [('GunReloadSession', 'progress(Human'), ('TaczHumanReloadTransactions', 'progress(Human'),
                ('RecoverySupplies', 'progress(Human'), ('OffhandFoodUse', 'progress(Human'), ('GuardedRangedCharge', 'progress(Human')]:
            code = body(source(name), signature)
            for call in ['actions', 'acquire', 'heartbeat', 'tick', 'advance', 'locate', 'bind', 'matches', 'getOrCreateTag', 'put', 'remove', 'tickReload']:
                self.assertNotRegex(code, r'\b' + call + r'\s*\(', (name, call))
            self.assertIn('peekActions(human)', code)
    def test_capture_uses_real_producers_and_no_placeholder_progress(self):
        capture = body(source('BehaviorDebugSnapshot'), 'capture(Human')
        for call in ['RecoverySupplies.progress(human)', 'rangedChargeProgress()', 'reloadProgress(human)', 'humanGunner$peekShieldDefense()']:
            self.assertIn(call, capture)
        self.assertNotIn('缺少', capture)
        self.assertIn('cut |= progress.size() > MAX_PROGRESS', capture)
        self.assertIn('progress.stream().limit(MAX_PROGRESS)', capture)
    def test_original_weapon_and_native_unknown_total_are_preserved(self):
        native = body(source('TaczHumanReloadTransactions'), 'progress(Human')
        self.assertIn('PAYMENTS.peekWeapon', native)
        self.assertIn('CLOCKS.get(data)==clock', native)
        self.assertIn('full==null?-1:full', native)
        self.assertIn('clock.phaseRemainingMillis/(50*rate)', native)
        self.assertIn('authority.session()==clock.session', native)
        for name in ['ScgunsIntegration', 'JegIntegration']:
            self.assertIn('GunReloadSession.progress(human, RELOAD)', source(name))
        peek = body(source('ReloadPaymentLedger'), 'peekWeapon(')
        self.assertNotIn('remove(', peek)
        self.assertIn('now < cycle.deadline()', peek)
    def test_wire_fields_and_finite_values(self):
        codec = source('NavigationDebugFrameCodec')
        for field in ['location', 'state', 'sampledTick', 'work', 'totalWork', 'workPerTick']:
            self.assertIn('p.' + field + '()', codec)
        self.assertIn('in.readDouble(), in.readDouble(), in.readDouble()', codec)
        self.assertIn('Double.isFinite(value)', source('ActionProgressSnapshot'))
        self.assertIn('() -> "9", "9"::equals, "9"::equals', source('NavigationDebugNetwork'))
