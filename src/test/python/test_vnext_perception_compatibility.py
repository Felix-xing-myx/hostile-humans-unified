"""Focused regression and installed-JAR boundary audit; actual Mixin application is a separate check."""
from pathlib import Path
import os
import subprocess
import tempfile
import unittest
import zipfile

ROOT=Path(__file__).resolve().parents[3]
PKG=ROOT/'src/main/java/dev/felix/hostilehumans'
def code(name): return (PKG/(name+'.java')).read_text(encoding='utf-8')
def disassemble(jar,clazz,verbose=False):
    javap=os.environ.get('HHU_JAVAP','javap')
    return subprocess.run([javap,'-p','-v' if verbose else '-c','-classpath',str(jar),clazz],check=True,capture_output=True,text=True).stdout

class VnextPerceptionCompatibility(unittest.TestCase):
    def test_discovery_still_requires_los_before_urgent_recognition_admission(self):
        s=code('perception/HumanPerception')
        check=s.split('private static void check(',1)[1].split('public static LivingEntity candidate',1)[0]
        self.assertLess(check.index('probe.visiblePoint==null'),check.index('preemptsRecognition'))
        self.assertIn('s.candidateCursor=candidateCursorBefore;s.recognitionCursor=recognitionCursorBefore',s)
        self.assertIn('ClipContext.Block.COLLIDER',s)
        self.assertNotIn('ProjectileUtil.getEntityHitResult',s)

    def test_collision_composes_after_third_party_broad_phase(self):
        s=code('mixin/TaczHumanProjectileCollisionMixin')
        self.assertNotIn('@Redirect',s)
        self.assertIn('HumanProjectileRelations.passesThrough(projectile,candidate)',s)
        self.assertEqual(s.count('@ModifyVariable'),2)
        self.assertIn('index = 6, require = 1, allow = 1',s)
        self.assertIn('index = 4, require = 1, allow = 1',s)

    def test_hearing_is_server_event_and_only_a_point_clue(self):
        s=code('compat/tacz/TaczHearingBridge')
        self.assertIn('GunFireEvent event',s)
        self.assertIn('event.isCanceled()',s)
        self.assertIn('LogicalSide.SERVER',s)
        self.assertIn('level.getServer().isSameThread()',s)
        self.assertIn('HumanAwareness.gunshot(shooter,origin)', ''.join(s.split()))
        self.assertNotIn('setTarget',s)

    @unittest.skipUnless(os.environ.get('HHU_TACZ_JAR'),'set HHU_TACZ_JAR for actual installed API audit')
    def test_installed_tacz_slots_and_actual_fire_event(self):
        jar=os.environ['HHU_TACZ_JAR']
        s=disassemble(jar,'com.tacz.guns.util.EntityUtil')
        single=s.split('findEntityOnPath(',1)[1].split('findEntitiesOnPath(',1)[0]
        multiple=s.split('findEntitiesOnPath(',1)[1].split('getHitResult(',1)[0]
        self.assertRegex(single,r'invokevirtual[^\n]+Level\.m_6249_[\s\S]*?astore\s+6')
        self.assertRegex(multiple,r'invokevirtual[^\n]+Level\.m_6249_[\s\S]*?astore\s+4')
        fire=disassemble(jar,'com.tacz.guns.item.ModernKineticGunScriptAPI')
        self.assertIn('com/tacz/guns/api/event/common/GunFireEvent',fire)
        api=disassemble(jar,'com.tacz.guns.api.event.common.GunFireEvent')
        for name in ('getShooter','getLogicalSide','isCancelable'): self.assertIn(name,api)

    @unittest.skipUnless(os.environ.get('HHU_ARCANA_JAR'),'set HHU_ARCANA_JAR for installed Arcana audit')
    def test_installed_arcana_redirect_keeps_proximity_fuze(self):
        with zipfile.ZipFile(os.environ['HHU_ARCANA_JAR']) as outer, tempfile.TemporaryDirectory(dir=ROOT/'build/task-evidence') as work:
            nested=Path(work)/'arcana.jar';nested.write_bytes(outer.read('META-INF/mod.jar'))
            s=disassemble(nested,'group.taczexpands.server.mixin.MixinEntityUtil',True)
        self.assertIn('org.spongepowered.asm.mixin.injection.Redirect',s)
        self.assertIn('Level;getEntities',s)
        self.assertIn('taczexpands$prevProximityFuzeRange',s)
        self.assertIn('method=["getHitResult"]',s)

if __name__=='__main__': unittest.main()
