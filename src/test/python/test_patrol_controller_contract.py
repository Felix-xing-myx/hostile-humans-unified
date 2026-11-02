import json
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
def source(path):return (ROOT/'src/main/java/dev/felix/hostilehumans'/path).read_text(encoding='utf-8')
class ControllerContract(unittest.TestCase):
    def test_controller_mutations_require_the_controller_session(self):
        network=source('soldier/PatrolToolNetwork.java')
        self.assertIn('s.controller()?PatrolControlService.mayControl(p,h):PatrolRouteEditor.mayConfigure(p,h)',network)
        guarded=network.split('else if(s.controller())',1)[1].split('Session fresh=',1)[0]
        for action in ('PatrolControlService.mode','PatrolControlService.settings','PatrolRoutes.editPoint'):
            self.assertIn(action,guarded)
        self.assertIn('NaturalState.hasState(h)',guarded)
        self.assertIn('HumanRelations.effectiveOwner(h)==null',source('soldier/PatrolControlService.java'))
    def test_draft_overlay_has_no_world_queries_and_uses_shared_native_storage(self):
        renderer=source('client/ui/PatrolDraftClient.java')
        for forbidden in ('new BufferBuilder','getBlockState(','.createPath('):self.assertNotIn(forbidden,renderer)
        for gate in ('draft.dimension().equals','getMainHandItem()','getOffhandItem()','128*128','finally','LoggingOut'):
            self.assertIn(gate,renderer)
        server=source('soldier/PatrolRouteEditor.java')
        self.assertIn('d.points.add(waypoint);preview(p)',server)
        self.assertIn('PatrolToolNetwork.clearPreview(p)',server)
        self.assertIn('point.block().above()',source('soldier/PatrolToolNetwork.java'))
    def test_free_mode_preview_cannot_write_suspended_author_data(self):
        storage=source('soldier/NaturalState.java')
        view=storage.split('public static ControllerData controllerData',1)[1].split('public static boolean hasState',1)[0]
        self.assertNotIn('.put(',view)
        self.assertEqual(view.count('.copy()'),3)
        self.assertIn('NaturalRoute.controllerDefinition',source('soldier/PatrolToolNetwork.java'))
    def test_combat_experience_preserves_the_growth_level_label(self):
        data=json.loads((ROOT/'src/main/resources/assets/hostile_humans_unified/lang/zh_cn.json').read_text(encoding='utf-8'))
        self.assertTrue(data['screen.hostile_humans_unified.soldier.growth.experience'].startswith('战斗经验'))
        self.assertTrue(any('成长等级' in value for key,value in data.items() if 'growth.level' in key))
if __name__=='__main__':unittest.main()
