"""Static filtering and real-defense handoff checks; live mob behavior is separate."""
from source_paths import SourcePath as Path
import unittest

PKG = Path(__file__).resolve().parents[3] / "src/main/java/dev/felix/hostilehumans"

class PassiveCreatureAwarenessContract(unittest.TestCase):
    def test_background_creatures_filtered_before_budget_and_alert(self):
        awareness = (PKG / "HumanAwareness.java").read_text(encoding="utf-8")
        classify = awareness.split("public static boolean passiveCreature", 1)[1].split("public static void investigate", 1)[0]
        for item in ("LivingEntity", "Player", "Human", "monster.Enemy", "MobCategory.MONSTER"):
            self.assertIn(item, classify)
        sample = awareness.split("public static void sampleMovement", 1)[1]
        candidate = awareness.split("static boolean movementCandidate", 1)[1].split("static Comparator", 1)[0]
        self.assertIn("passiveCreature(actor)", candidate)
        self.assertLess(sample.index("!movementCandidate(listener,actor)"), sample.index('PerceptionBudget.claim(actor,"sources"'))
        emit = awareness.split("private static void emit", 1)[1].split("private static void enqueue", 1)[0]
        self.assertLess(emit.index("passiveCreature(actor)"), emit.index("enqueue("))
        perception = (PKG / "HumanPerception.java").read_text(encoding="utf-8")
        self.assertIn("if ((!reduced || BattlePerceptionDetail.closeHuman(h,e)) && HumanAwareness.movementCandidate(h,e)) sounds.offer(e)", perception)
        notice = perception.split("public static void notice", 1)[1].split("public static void ownerClue", 1)[0]
        self.assertLess(notice.index("allowsCreatureClue"), notice.index("alert(h,s"))
        self.assertIn("if(invalid)clue.completed=now", perception)

    def test_real_self_hit_has_bounded_exception_without_granting_identity(self):
        source = (PKG / "HumanPerception.java").read_text(encoding="utf-8")
        hurt = source.split("public static void hurt", 1)[1].split("private static void trimQueue", 1)[0]
        self.assertLess(hurt.index("actual<=0"), hurt.index("recordCreatureDefense"))
        self.assertIn("if(!periodic&&owner instanceof LivingEntity defenderSource)recordCreatureDefense", hurt)
        self.assertLess(hurt.index("recordCreatureDefense"), hurt.index("!valid(h,attacker)"))
        response = source.split("private static boolean allowsCreatureResponse", 1)[1].split("private static void recordCreatureDefense", 1)[0]
        self.assertIn("PerceptionPolicy.defensiveCreature", response)
        policy = (PKG.parents[2] / "dev/felix/hostilehumans/perception/PerceptionPolicy.java").read_text(encoding="utf-8")
        self.assertIn("defenseUntil>=0", policy)
        self.assertIn("now<defenseUntil", policy)
        record = source.split("private static void recordCreatureDefense", 1)[1].split("private static boolean allowsCreatureClue", 1)[0]
        self.assertIn('c().integer("memory.familiar_ticks")', record)
        self.assertNotIn(".marked=true", record)
        self.assertNotIn("liveUntil=", record)
        self.assertNotIn("setTarget", record)

    def test_owner_defense_is_distinct_from_owner_assault(self):
        source = (PKG / "HumanRelations.java").read_text(encoding="utf-8")
        defense = source.split("private static void defend(", 1)[1].split("static void followOwnerAssault", 1)[0]
        self.assertIn("authorizeOwnerDefense", defense)
        self.assertIn("assignGuardTarget(human, attacker, level.getGameTime(), true)", defense)
        assault = source.split("static void followOwnerAssault", 1)[1].split("private static List", 1)[0]
        self.assertLess(assault.index("HumanAwareness.passiveCreature(target)"), assault.index("authorizeOwnerAssault"))
        self.assertIn("assignGuardTarget(human, target, level.getGameTime(), false)", assault)
        self.assertIn("HumanPerception.ownerClue(human,target,defendingOwner)", source)
        perception = (PKG / "HumanPerception.java").read_text(encoding="utf-8")
        self.assertIn("if(defendingOwner)recordCreatureDefense(h,target)", perception)
