"""Regression guards for consuming detail capability before reopening a parent."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
JAVA = ROOT / "src/main/java/club/someoneice/humangunner"


def source(name):
    return (JAVA / (name + ".java")).read_text(encoding="utf-8-sig")


class DetailNavigation(unittest.TestCase):
    def test_return_wire_is_bounded_and_server_directed(self):
        network = source("AttributeDetailNetwork")
        self.assertIn('() -> "2", "2"::equals,"2"::equals', network)
        registration = network.split("CHANNEL.registerMessage(5,Return.class", 1)[1].split("MinecraftForge", 1)[0]
        self.assertIn("new Return(b.readUUID(),b.readBoolean());end(b)", registration)
        self.assertIn("NetworkDirection.PLAY_TO_SERVER", registration)

    def test_return_consumes_one_matching_live_capability_before_open(self):
        handler = source("AttributeDetailNetwork").split("private static void returnRequest", 1)[1].split("private static void receive", 1)[0]
        for destination in ("SoldierRosterNetwork.open(viewer)", "HumanCommandNetwork.openFor(viewer,human)"):
            for guard in ("session.token().equals(packet.token())", "target(viewer,session)", "SESSIONS.remove(viewer)", "AttributeDetailPolicy.expired(now(viewer),session.heartbeat())"):
                self.assertLess(handler.index(guard), handler.index(destination))
        self.assertNotIn("new Session", handler)
        target = source("AttributeDetailNetwork").split("private static Human target", 1)[1].split("private static void send", 1)[0]
        for check in ("session.dimension()", "session.entityId()", "human.getUUID().equals(session.target())", "permitted(viewer,human,session.wildTool())"):
            self.assertIn(check, target)

    def test_roster_return_requires_actual_held_roster(self):
        handler = source("AttributeDetailNetwork").split("private static void returnRequest", 1)[1]
        self.assertIn("viewer.getMainHandItem().getItem() instanceof SoldierRosterItem", handler)
        self.assertIn("viewer.getOffhandItem().getItem() instanceof SoldierRosterItem", handler)
        self.assertIn("if(holdsRoster)SoldierRosterNetwork.open(viewer)", handler)

    def test_manual_return_never_restores_closed_command_screen(self):
        screen = source("AttributeDetailScreen")
        close = screen.split("public void onClose()", 1)[1].split("private int left", 1)[0]
        managed = close.split("} else", 1)[0]
        self.assertIn("!suppressClose&&minecraft.getConnection()!=null", managed)
        self.assertLess(managed.index("returnToParent(token"), managed.index("minecraft.setScreen(null)"))
        self.assertNotIn("setScreen(parent)", managed)
        # Opening details still closes the original parent; no hidden session remains.
        self.assertIn("HumanCommandNetwork.closeScreen(sessionToken)", source("SoldierCommandScreen"))
        self.assertIn("SoldierRosterNetwork.request(token,9,NONE)", source("SoldierRosterScreen"))

    def test_server_rejection_and_disconnect_cannot_reopen_parent(self):
        screen = source("AttributeDetailScreen")
        server_close = screen.split("void serverClosed()", 1)[1].split("private static Component text", 1)[0]
        self.assertIn("suppressClose=true;minecraft.setScreen(managedParent()?null:parent)", server_close)
        self.assertIn("void disconnect(){suppressClose=true;}", server_close)
        self.assertNotIn("returnToParent", server_close)

    def test_unknown_persisted_faction_is_bounded_before_snapshot_and_encode(self):
        command = source("HumanCommandNetwork")
        self.assertIn("HumanFactions.wireId(HumanFactions.id(h))", command)
        self.assertIn("b.writeUtf(HumanFactions.wireId(p.faction),256)", command)
        roster = source("SoldierRosterNetwork")
        self.assertIn('HumanFactions.wireId(live!=null?HumanFactions.id(live):lastData.getString("hostile_humans_unified:faction"))', roster)
        self.assertIn("buffer.writeUtf(HumanFactions.wireId(entry.faction),256)", roster)


if __name__ == "__main__":
    unittest.main()
