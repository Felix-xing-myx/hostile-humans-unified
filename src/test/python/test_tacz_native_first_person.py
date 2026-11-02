"""Compile-baseline resources and independently discovered actual runtime resources; no game/Gradle launch."""
import os
from pathlib import Path
import unittest
import zipfile
import json
import re

ROOT = Path(__file__).resolve().parents[3]
PACKAGE = ROOT / 'src/main/java/dev/felix/hostilehumans/client/render/tacz'
JAVA = Path(os.environ.get('JAVA_HOME', 'D:/Java/17')) / 'bin'
JAR = Path(os.environ.get('TACZ_API_JAR', 'E:/MC/Create-Delight/versions/Create-Delight-Remake/mods/tacz-1.20.1-1.1.8-hotfix.jar'))


def runtime_tacz():
    explicit = os.environ.get('TACZ_RUNTIME_JAR')
    if explicit:
        return Path(explicit)
    try:
        import tomllib
    except ImportError:
        return None
    settings = ROOT.parent / 'maintenance-tools/release-settings.json'
    if not settings.is_file():
        settings = ROOT.parent.parent / 'maintenance-tools/release-settings.json'
    if not settings.is_file():
        return None
    mods = Path(json.loads(settings.read_text(encoding='utf-8'))['instance']) / 'mods'
    found = []
    for candidate in mods.glob('*.jar'):
        with zipfile.ZipFile(candidate) as jar:
            if 'META-INF/mods.toml' in jar.namelist():
                try:
                    text = jar.read('META-INF/mods.toml').decode('utf-8-sig').replace('\r\n', '\n').replace('\r', '\n')
                    data = tomllib.loads(text)
                except tomllib.TOMLDecodeError:
                    continue
                if any(mod.get('modId') == 'tacz' for mod in data.get('mods', [])):
                    found.append(candidate)
    return found[0] if len(found) == 1 else None


RUNTIME_JAR = runtime_tacz()


class TaczNativeFirstPersonTest(unittest.TestCase):
    @unittest.skipUnless(RUNTIME_JAR and RUNTIME_JAR.is_file(), 'Actual TaCZ runtime JAR not available')
    def test_actual_runtime_resources_have_native_reload_bones_and_hands(self):
        prefix = 'assets/tacz/custom/tacz_default_gun/assets/tacz/'
        with zipfile.ZipFile(RUNTIME_JAR) as jar:
            animation = json.loads(jar.read(prefix + 'animations/ak47.animation.json'))['animations']
            for name in ('reload_empty', 'reload_tactical'):
                self.assertIn('lefthand', animation[name]['bones'])
                self.assertIn('righthand', animation[name]['bones'])
            model = jar.read('com/tacz/guns/client/model/BedrockGunModel.class')
            self.assertIn(b'lefthand_pos', model)
            self.assertIn(b'righthand_pos', model)

    @unittest.skipUnless(JAR.exists(), 'Compile-baseline TaCZ API JAR not available')
    def test_compile_baseline_resources_have_hands_and_magazine_and_cycle_clips(self):
        prefix = 'assets/tacz/custom/tacz_default_gun/assets/tacz/'
        with zipfile.ZipFile(JAR) as jar:
            ak = json.loads(jar.read(prefix + 'animations/ak47.animation.json'))['animations']
            for name in ('reload_empty', 'reload_tactical', 'reload_empty_xmag', 'reload_tactical_xmag'):
                self.assertIn('lefthand', ak[name]['bones'])
                self.assertIn('righthand', ak[name]['bones'])
            shotgun = json.loads(jar.read(prefix + 'animations/m1014.animation.json'))['animations']
            for name in ('reload_intro_empty', 'reload_intro', 'reload_loop', 'reload_loop_2', 'reload_end'):
                self.assertIn(name, shotgun)
            model = jar.read('com/tacz/guns/client/model/BedrockGunModel.class')
            for node in (b'lefthand_pos', b'righthand_pos', b'muzzle_flash'):
                self.assertIn(node, model)

    @unittest.skipUnless(JAR.exists(), 'Compile-baseline TaCZ API JAR not available')
    def test_compile_baseline_m1014_script_timing_is_not_data_type_or_cooldown(self):
        with zipfile.ZipFile(JAR) as jar:
            text = jar.read('assets/tacz/custom/tacz_default_gun/data/tacz/data/guns/m1014_data.json').decode('utf-8')
            data = json.loads(re.sub(r'//[^\n]*', '', text))
            self.assertEqual(data['reload']['type'], 'magazine')
            param = data['script_param']
            self.assertEqual(param['intro_empty'], 1.8)
            self.assertEqual(param['loop_2'], .7)
            self.assertEqual(param['ending'], .36)
            self.assertGreater(param['intro_empty'], data['reload']['cooldown']['empty'])
            script = jar.read('assets/tacz/custom/tacz_default_gun/data/tacz/scripts/m1014_gun_logic.lua').decode('utf-8')
            self.assertIn('total_time - reload_time', script)
            self.assertIn('EMPTY_RELOAD_FINISHING, ending - int_time', script)

    def test_visual_and_audio_share_actual_native_phase_clock_and_safe_audio_fallback(self):
        plan = (PACKAGE / 'NativeReloadPlan.java').read_text(encoding='utf-8')
        self.assertNotIn('request.manual()', plan)
        resources = (PACKAGE / 'TaczPresentationResources.java').read_text(encoding='utf-8')
        for field in ('PhaseStartWork', 'PhaseEndWork', 'PhaseIndex', 'PhaseSuppliedStart'):
            self.assertIn(field, resources)
        audio = (PACKAGE / 'NativeReloadAudio.java').read_text(encoding='utf-8')
        self.assertLess(audio.index('pending.oneShot = sound'), audio.index('FILES.computeIfAbsent'))
        self.assertIn('if (!cues.isEmpty()) pending.oneShot = null', audio)
        self.assertIn('TaczPresentationResources.phase(timeline)', audio)
        self.assertIn('phasePresentationStart(phase)', audio)
        timeline = (ROOT / 'src/main/java/dev/felix/hostilehumans/compat/tacz/TaczReloadVisualTimeline.java').read_text(encoding='utf-8')
        self.assertIn('tag.putLong("PhaseRemaining", remainingMillis)', timeline)
        self.assertIn('tag.putInt("Supplied"', timeline)
        self.assertNotIn('setCurrentAmmoCount', timeline)
        self.assertNotIn('setBulletInBarrel', timeline)

    def test_common_optional_boundary_has_no_tacz_descriptors(self):
        source = (PACKAGE / 'TaczSpectatorFirstPerson.java').read_text(encoding='utf-8')
        self.assertNotIn('com.tacz', source)
        backend = (PACKAGE / 'TaczNativePresentation.java').read_text(encoding='utf-8')
        for forbidden in ('IClientPlayerGunOperator', 'getAnimationStateMachine(', 'Minecraft.getInstance().player',
                          'setCurrentAmmoCount(', 'setBulletInBarrel(', 'GunAnimationStateContext'):
            self.assertNotIn(forbidden, backend)


if __name__ == '__main__':
    unittest.main()
