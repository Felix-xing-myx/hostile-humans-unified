"""Sound classification, static positions and deferral authority; not acoustic gameplay proof."""
from source_paths import SourcePath as Path
import unittest
ROOT=Path(__file__).resolve().parents[3]
PKG=ROOT/"src/main/java/dev/felix/hostilehumans"
def source(name):return (PKG/(name+".java")).read_text(encoding="utf-8")
class HearingRelevanceContract(unittest.TestCase):
    def test_background_and_important_sources_are_distinct(self):
        code=source("HearingRelevance")
        self.assertIn("actor instanceof Player",code)
        self.assertIn("mob.getTarget() instanceof Human",code)
        self.assertIn("return step(kind)?0:2",code)
        self.assertIn('c.number("hearing.background_step_radius")',code)
        self.assertIn('c.number("hearing.wall_radius_factor")',code)
        self.assertNotIn("setTarget",code)
        self.assertNotIn("getAttributeValue",code)
    def test_pending_hearing_does_not_advance_or_repeat_finished_ray(self):
        code=source("HumanAwareness")
        delivery=code.split("var relevance=",1)[1].split("q.remove(n)",1)[0]
        self.assertLess(delivery.index("relevance.deferred())return"),delivery.index("n.index++"))
        self.assertIn("n.evaluatedIndex=n.index",delivery)
        self.assertIn("n.evaluatedIndex==n.index?n.evaluated",code)
        self.assertIn("priority < notice.importance", code)
        self.assertIn("lanes.get(priority).pollLast()", code)
        self.assertIn("lanes.get(notice.importance).addLast(notice)", code)
        self.assertIn("lanes.get(priority).peekFirst()", code)
        self.assertNotIn("level.getEntity(n.source).position()",code)
    def test_ignored_sounds_cannot_refresh_alert(self):
        code=source("HumanPerception")
        notice=code.split("static void notice(Human h,String kind,Vec3 position,UUID source,int ttl,int priority)",1)[1].split("public static void ownerClue",1)[0]
        self.assertIn("priority<2",notice)
        self.assertLess(notice.index("match.completed>=0"),notice.index("alert(h,s"))
        self.assertIn("match.completed=now",notice)

    def test_filter_and_fair_priority_apply_before_bounded_retention(self):
        scan=source("HumanPerception")
        self.assertIn("new BoundedSelection<>(cap*4,HumanAwareness.movementOrder(h))",scan)
        self.assertIn("if ((!reduced || BattlePerceptionDetail.closeHuman(h,e)) && HumanAwareness.movementCandidate(h,e)) sounds.offer(e)",scan)
        awareness=source("HumanAwareness")
        eligibility=awareness.split("static boolean movementCandidate",1)[1].split("static Comparator",1)[0]
        self.assertIn('hearing.background_vertical',eligibility)
        self.assertIn('hearing.background_step_radius',eligibility)
        order=awareness.split("static Comparator<LivingEntity> movementOrder",1)[1].split("static void sampleMovement",1)[0]
        self.assertLess(order.index("importance"),order.index("previous.tick"))
        self.assertLess(order.index("previous.tick"),order.index("thenComparingDouble"))
        self.assertIn("HumanAwareness.sampleMovement(h,sounds.values())", scan)
        self.assertNotIn("ordered.sort", awareness)
    def test_lower_priority_cannot_extend_higher_priority_lifetime(self):
        notice=source("HumanPerception").split("static void notice(Human h,String kind,Vec3 position,UUID source,int ttl,int priority)",1)[1].split("public static void ownerClue",1)[0]
        guard="priority<match.priority&&now<match.until)return"
        self.assertIn(guard,notice)
        self.assertLess(notice.index(guard),notice.index("match.until=now+ttl"))
        self.assertIn("match.completed<0&&priority==match.priority",notice)
