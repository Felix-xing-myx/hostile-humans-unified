"""Regression guards for consuming detail capability before reopening a parent."""
import unittest
import re
from source_paths import SourcePath as Path

ROOT = Path(__file__).resolve().parents[3]
JAVA = ROOT / "src/main/java/dev/felix/hostilehumans"


def source(name):
    return (JAVA / (name + ".java")).read_text(encoding="utf-8-sig")


def compact(code):
 return re.sub(r"\s+", "", code)

def java_block(code, marker):
 """Read the real balanced Java body, ignoring braces in strings/comments."""
 pattern=r"\s*".join(re.escape(token) for token in re.findall(r"\w+|[^\w\s]",marker))
 match=re.search(pattern,code)
 if match is None:raise AssertionError("Missing Java contract marker: "+marker)
 start=code.index("{",match.end());depth=0
 tokens=re.finditer(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*.*?\*/|[{}]',code[start:],re.S)
 for token in tokens:
  value=token.group()
  if value=="{":depth+=1
  elif value=="}":
   depth-=1
   if depth==0:return code[start:start+token.end()]
 raise AssertionError("Unclosed Java block: "+marker)

class DetailNavigation(unittest.TestCase):
    def test_return_wire_is_bounded_and_server_directed(self):
        network = source("AttributeDetailNetwork")
        self.assertIn('() -> "3", "3"::equals,"3"::equals', network)
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
        screen=source("AttributeDetailScreen")
        close=java_block(screen,"public void onClose()")
        managed=compact(java_block(close,"if(managedParent())"))
        self.assertIn("!session.suppressed()&&minecraft.getConnection()!=null",managed)
        self.assertLess(managed.index("returnToParent(token()"),managed.index("minecraft.setScreen(null)"))
        self.assertNotIn("setScreen(parent)",managed)
        self.assertIn("elseminecraft.setScreen(parent)",compact(close))
        self.assertIn("HumanCommandNetwork.closeScreen(sessionToken)",source("SoldierCommandScreen"))
        self.assertIn("SoldierRosterNetwork.request(token,9,NONE)",compact(source("SoldierRosterScreen")))
    def test_server_rejection_and_disconnect_cannot_reopen_parent(self):
        screen=source("AttributeDetailScreen")
        server_close=compact(java_block(screen,"void serverClosed()"))
        self.assertLess(server_close.index("session.suppressClose()"),server_close.index("minecraft.setScreen(managedParent()?null:parent)"))
        disconnect=compact(java_block(screen,"void disconnect()"))
        self.assertIn("session.suppressClose()",disconnect)
        for code in (server_close,disconnect):self.assertNotIn("returnToParent",code)
        self.assertIn("session.removed()",java_block(screen,"public void removed()"))
        self.assertIn("newPanelSessionView(token,20,()->AttributeDetailNetwork.keepAlive(token),()->AttributeDetailNetwork.close(token))",compact(screen))
        session=source("PanelSessionView")
        removed=compact(java_block(session,"public void removed()"))
        self.assertIn("if(!closed){closed=true;if(!suppressed)close.run();}",removed)
        self.assertIn("suppressed=true",compact(java_block(session,"public void suppressClose()")))
        self.assertIn("!closed&&!suppressed",compact(java_block(session,"public void tick()")))
    def test_unknown_persisted_faction_is_bounded_before_snapshot_and_encode(self):
        command = source("HumanCommandNetwork")
        self.assertIn("HumanFactions.wireId(HumanFactions.id(h))", command)
        self.assertIn("b.writeUtf(HumanFactions.wireId(p.faction),256)", command)
        roster = source("SoldierRosterNetwork")
        self.assertIn('HumanFactions.wireId(live!=null?HumanFactions.id(live):lastData.getString("hostile_humans_unified:faction"))', roster)
        self.assertIn("buffer.writeUtf(HumanFactions.wireId(entry.faction),256)", roster)


if __name__ == "__main__":
    unittest.main()
