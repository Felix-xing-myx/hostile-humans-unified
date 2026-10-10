"""Read-only storage and configuration ownership contracts, not runtime validation."""
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[3]
JAVA = ROOT / 'src/main/java/club/someoneice/humangunner'


class GrowthConfigLayoutTest(unittest.TestCase):
    def test_common_policy_and_rank_details_are_separated(self):
        data = json.loads((ROOT / 'src/main/resources/defaults/hostile_humans_unified.json').read_text(encoding='utf-8'))
        self.assertNotIn('profiles', data['growth'])
        self.assertEqual(data['growth']['experience']['band_width_percent'], 5)
        for rank in ('roamer', 'tier1', 'tier2', 'tier3'):
            profile = data['tiers'][rank]['growth_profile']
            for field in ('start_day', 'duration_days', 'progress_nodes', 'early_baseline_gear_chance',
                          'early_gun_chance_multiplier', 'early_enchantment_keep_chance',
                          'early_trident_keep_chance', 'early_totem_keep_chance'):
                self.assertIn(field, profile)
        source = (JAVA / 'GrowthConfig.java').read_text(encoding='utf-8')
        self.assertIn('group(UnifiedConfig.object(tiers, rank), "growth_profile"', source)
        self.assertNotIn('group(data, "profiles"', source)
        self.assertIn('(percent - 100) / experienceBandWidth', source)
        self.assertIn('(percent - 100) / (double) experienceBandWidth', source)

    def test_relocation_is_before_loading_and_source_removal_is_after_writes(self):
        loader = (JAVA / 'SplitConfigFiles.java').read_text(encoding='utf-8')
        self.assertLess(loader.index('GrowthProfileFiles.relocate'), loader.index('JsonObject result = loadFile'))
        move = (JAVA / 'GrowthProfileFiles.java').read_text(encoding='utf-8')
        self.assertLess(move.index('SplitConfigFiles.writeCurrent(file.path()'), move.index('profiles.remove(rank)'))
        self.assertIn('if (!profile.has(entry.getKey()))', move)
        self.assertIn('SplitConfigFiles.read(path, 2)', move)

    def test_all_save_paths_share_readable_strict_json_formatter(self):
        source = (JAVA / 'SplitConfigFiles.java').read_text(encoding='utf-8')
        self.assertEqual(source.count('writer.write(format(data))'), 2)
        self.assertIn('format(arranged).getBytes(StandardCharsets.UTF_8)', source)
        self.assertNotIn('GSON.toJson(data, writer)', source)


if __name__ == '__main__':
    unittest.main()
