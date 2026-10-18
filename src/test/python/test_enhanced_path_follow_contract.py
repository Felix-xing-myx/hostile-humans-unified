"""Execution-only navigation boundaries. Not a world collision or game acceptance test."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[3]
JAVA=ROOT/'src/main/java'
PKG=JAVA/'club/someoneice/humangunner'


class EnhancedPathFollowContract(unittest.TestCase):
    def test_native_arrival_and_stuck_detection_remain_before_shortcut(self):
        nav=(JAVA/'com/craftix/hostile_humans/entity/ai/HumanNavigation.java').read_text(encoding='utf-8')
        follow=nav.split('@Override protected void followThePath()',1)[1].split('/** An airborne',1)[0]
        self.assertLess(follow.index('super.followThePath()'),follow.index('HumanPathFollower.follow'))
        tick=nav.split('@Override public void tick()',1)[1].split('@Override protected void followThePath',1)[0]
        self.assertLess(tick.index('HumanPathFollower.beforeTick'),tick.index('super.tick()'))

    def test_corridor_is_full_body_and_continuously_supported(self):
        code=(PKG/'HumanPathFollower.java').read_text(encoding='utf-8')
        prove=code.split('private static Map<BlockPos,BlockState> prove(',1)[1].split('private static boolean sensitive',1)[0]
        self.assertLess(prove.index('hasChunksAt'),prove.index('getBlockState'))
        for guard in ('LocalPathFollowPolicy.corridor','allowed(h,point)','HumanTerrainSafety.flatSupport',
                      'h.level().noCollision(h,body', 'expandTowards(stepX,0,stepZ)', 'MAX_BLOCKS'):
            self.assertIn(guard,prove)
        for sensitive in ('DoorBlock','FenceGateBlock','TrapDoorBlock','BlockTags.CLIMBABLE','RetreatPathSafety.isHazard'):
            self.assertIn(sensitive,code)

    def test_route_identity_endpoint_and_physics_are_not_replaced(self):
        code=(PKG/'HumanPathFollower.java').read_text(encoding='utf-8')
        for forbidden in ('createPath(', 'new Path(', 'setPos(', 'setDeltaMovement(', 'setTarget(', 'teleport'):
            self.assertNotIn(forbidden,code)
        self.assertIn('s.path!=path',code)
        self.assertEqual(code.count('path.setNextNodeIndex('),2)
        self.assertLess(code.index('Map<BlockPos,BlockState> proof=prove'),code.index('path.setNextNodeIndex(to)'))
        self.assertIn('path.setNextNodeIndex(s.original)',code)
        self.assertIn('sensitive(h,pos,current)',code)
        self.assertIn('SoldierOrder.allowsNavigationPosition',code)
        orders=(PKG/'SoldierOrder.java').read_text(encoding='utf-8')
        dispatch=orders.split('public static boolean allowsNavigationPosition',1)[1].split('private static boolean allowsPath',1)[0]
        for boundary in ('allowsLootPosition','allowsDailyPosition','allowsCombatPosition'):
            self.assertIn(boundary,dispatch)

    def test_budget_waits_preserve_native_nodes_and_use_independent_pool(self):
        code=(PKG/'HumanPathFollower.java').read_text(encoding='utf-8')
        wait=code.split('if(budget==0)',1)[1].split('s.nextProbe=h.tickCount+INTERVAL',1)[0]
        self.assertNotIn('setNextNodeIndex',wait)
        budget=(PKG/'OptionalPathBudget.java').read_text(encoding='utf-8')
        self.assertIn('if(kind==Kind.PATH_FOLLOW)return pools.follow()',budget)
        self.assertIn('new FairWorkBudget<>(16,2,8)',budget)
        self.assertIn('AdaptiveActivityBudget.navigationCapacity(human,16,2)',budget)


if __name__=='__main__': unittest.main()
