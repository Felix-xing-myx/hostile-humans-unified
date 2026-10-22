"""Static integration guards; policy execution and in-game acceptance are separate checks."""
from pathlib import Path
import unittest

BASE = Path(__file__).resolve().parents[3] / "src/main/java/club/someoneice/humangunner"


def source(name):
    return (BASE / f"{name}.java").read_text(encoding="utf-8-sig")


class FiniteOrdersContract(unittest.TestCase):
    def test_legacy_ordinals_and_logical_adapter(self):
        s = source("SoldierOrder")
        self.assertIn("HOLD_POSITION, GUARD, FOLLOW, PATROL, BODYGUARD, GARRISON", s)
        self.assertIn("return logical(raw(h))", s)
        self.assertNotIn("FOLLOW_COMBAT_RANGE_SQR", s)
        self.assertNotIn("PATROL_RANGE_SQR", s)

    def test_player_radius_has_one_write_authority(self):
        s = source("GarrisonSettings")
        self.assertIn('tag.putInt("patrol_radius", radius)', s)
        for alias in ("guard", "patrol", "garrison"):
            self.assertIn(f'radii.remove("{alias}")', s)
        reader = s[s.index("public static GarrisonSettings player"):s.index("public static GarrisonSettings natural")]
        self.assertNotIn(".put", reader)

    def test_normal_load_never_reattaches_origin(self):
        s = source("NaturalState")
        join = s[s.index("public static String onJoin"):]
        self.assertIn("boolean loadedFromDisk", join)
        self.assertEqual(join.count("if (!loadedFromDisk"), 2)
        self.assertIn('contains(ORIGIN, Tag.TAG_COMPOUND)', s)
        self.assertIn('contains("mirror", Tag.TAG_STRING)', join)
        self.assertNotIn("teleport", s)
        self.assertNotIn("getChunk(", s)

    def test_author_export_cannot_leak_instance_state(self):
        s = source("NaturalState")
        export = s[s.index("public static CompoundTag authorOnly"):s.index("public static String onJoin")]
        self.assertIn("new CompoundTag()", export)
        self.assertIn("result.put(STATE", export)
        self.assertNotIn("result.put(ORIGIN", export)
        self.assertNotIn("result.put(RUNTIME", export)

    def test_route_completion_pause_and_floor_contract(self):
        s = source("NaturalRoute")
        self.assertLess(s.index('if(state.getBoolean("completed"))'), s.index("if(a.resume)"))
        self.assertIn("RouteProgressPolicy.executedTicks", s)
        self.assertIn("routeVerticalTolerance()", s)
        self.assertIn('missing_transform_context', s)
        self.assertIn('contains("transform_context")', s)
        self.assertIn("BasicNavigationBudget.tryAcquire(h,1)", s)
        self.assertIn("SoldierOrder.allowsDailyPath(h,path)", s)
        self.assertIn("PLAYER_RUNTIME", s)

    def test_sound_cannot_borrow_excursion_or_mark_command_rejection_unreachable(self):
        s = source("PerceptionInvestigationGoal")
        self.assertIn("SoldierOrder.allowsDailyPosition(h,candidate)", s)
        self.assertIn("SoldierOrder.allowsDailyPath(h,candidate)", s)
        rejection = s[s.index("if(candidate!=null&&!SoldierOrder.allowsDailyPath"):s.index("if(candidate==null||!candidate.canReach()")]
        self.assertNotIn("SoundInvestigationMemory.failed", rejection)

    def test_squad_retains_unloaded_and_rechecks_formal_ownership(self):
        data, commands = source("SquadData"), source("SquadCommands")
        self.assertIn("RecruitmentLedger.get(server).isOwnedRecord(id,owner)", data)
        self.assertIn("return live==null||", data)
        self.assertIn("human==null){unloaded++;continue;}", commands)
        self.assertIn("player.getUUID().equals(human.getOwnerUUID())", commands)
        self.assertIn("CommandView.apply(human,update)", commands)
        self.assertIn("SoldierOrder.apply(human,update.order(),settings,update.followDistance())", source("CommandView"))
        self.assertNotIn("requestRecall", commands)
        self.assertNotIn("getChunk(", commands)

    def test_squad_session_and_token_are_owner_bound(self):
        s = source("SquadCommands")
        self.assertIn("session.token().equals(token)&&session.squad()==number", s)
        self.assertIn("player.getUUID().equals(tag.getUUID(OWNER))", s)
        self.assertIn("existingOrAllowed(player.getUUID(),number)", s)

    def test_control_handoffs_and_no_anchored_teleport(self):
        s = source("SoldierOrderGoal")
        self.assertIn("!NavigationDebugService.controls(human)", s)
        self.assertIn("NaturalRoute.stopOwned(human)", s)
        s = source("SoldierStuckRecovery")
        eligible = s[s.index("static boolean eligibleOrder"):s.index("static void tick")]
        self.assertNotIn("SoldierOrder.GARRISON", eligible)
        self.assertNotIn("SoldierOrder.GUARD", eligible)


if __name__ == "__main__":
    unittest.main()
