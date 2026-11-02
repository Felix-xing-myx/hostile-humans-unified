"""Static integration contracts only; no compilation or Minecraft runtime claims."""
import json
import re
import unittest
from source_paths import SourcePath as Path

ROOT = Path(__file__).resolve().parents[3]
JAVA = ROOT / "src/main/java/dev/felix/hostilehumans"
NET = (JAVA / "SoldierRosterNetwork.java").read_text(encoding="utf-8")
UI = (JAVA / "SoldierRosterScreen.java").read_text(encoding="utf-8")
GROWTH = (JAVA / "HumanGrowth.java").read_text(encoding="utf-8")

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

class RosterGrowthContract(unittest.TestCase):
    def test_nullable_wire_snapshot_and_protocol(self):
        self.assertIn('PROTOCOL = "8"', NET)
        self.assertIn("GrowthSnapshot growth, int squad, String faction, int entityId) {}", NET)
        self.assertIn("buffer.writeBoolean(entry.growth != null)", NET)
        self.assertIn("if (entry.growth != null) entry.growth.encode(buffer)", NET)
        self.assertIn("buffer.readBoolean() ? GrowthSnapshot.decode(buffer) : null", NET)

    def test_server_owned_live_and_saved_values(self):
        self.assertIn("HumanGrowth.snapshot(live) : HumanGrowth.savedSnapshot(lastData)", NET)
        self.assertIn("stored.getPersistedCommandData()", NET)
        for forbidden in ("UnifiedConfig", "HumanGrowth", "getPersistentData"):
            self.assertNotIn(forbidden, UI)
        request = NET.split("private record Request", 1)[1].split(";", 1)[0]
        self.assertNotIn("Growth", request)

    def test_saved_snapshot_does_not_reroll_or_modify_birth(self):
        saved = GROWTH.split("static GrowthSnapshot savedSnapshot", 1)[1]
        self.assertIn("net.minecraft.nbt.Tag.TAG_COMPOUND", saved)
        self.assertIn(": null", saved)
        self.assertIn("CompoundTag state = saved.copy()", saved)
        self.assertIn("config.enabled()", saved)
        self.assertNotIn("markBirth", saved)
        self.assertNotIn("award(", saved)

    def test_hidden_layout_and_controls_share_offset(self):
        detail=java_block(UI,'protected void buildRight(')
        growth=java_block(detail,'if(e.growth()!=null&&e.growth().enabled())')
        self.assertIn('new PanelMeter(',growth)
        self.assertLess(detail.index(growth),detail.index('UiThemes.MARKER_MOVE'))
        self.assertNotIn('new PanelMeter(',detail.replace(growth,'',1))
        self.assertTrue(compact(detail[detail.index(growth)+len(growth):]).startswith('out.add(node(body(newPanelGroup(cmd("movement")'))
        self.assertIn('UiLayout.column(6,right)',compact(java_block((JAVA/'PanelScreen.java').read_text(encoding='utf-8'),'protected void init(')))
        shared=(JAVA/'PanelScreen.java').read_text(encoding='utf-8')
        button=(JAVA/'PanelButton.java').read_text(encoding='utf-8')
        leaf=compact(java_block(shared,'UiLayout.Node node(PanelButton b)'))
        self.assertIn('UiLayout.leaf(b::measureHeight,b::place)',leaf)
        layout=compact(java_block(shared,'void relayout('))
        self.assertLess(layout.index('content.measure(viewport.width())'),layout.index('content.place('))
        self.assertIn('viewport.y()-scroll',layout)
        place=compact(java_block(button,'void place('))
        for setter in ('setX(b.x())','setY(b.y())','setWidth(b.width())','setHeight(b.height())'):self.assertIn(setter,place)
        self.assertIn('inViewport(x,y)&&super.clicked(x,y)',compact(java_block(button,'protected boolean clicked(')))
        self.assertIn('enableScissor(rect.x(),rect.y(),rect.right(),rect.bottom())',compact(java_block(shared,'private void drawBody(')))
        self.assertIn('viewportOf(widget)',shared)
        self.assertIn('leftViewport',shared);self.assertIn('rightViewport',shared)
        self.assertIn('PanelGeometry.Size.M',UI)
        self.assertIn('roster("last_known")',detail)
        self.assertIn('refreshTicks',UI)
    def test_translations_and_full_progress_display(self):
        for lang in ("en_us", "zh_cn"):
            labels = json.loads((ROOT / "src/main/resources/assets/hostile_humans_unified/lang" / (lang + ".json")).read_text(encoding="utf-8"))
            self.assertIn("screen.hostile_humans_unified.roster.growth_summary", labels)
        for value in ("stageProgress()", "stageRequired()", "experienceProgress()", "experienceRequired()"):
            self.assertIn(value, UI)

if __name__ == "__main__":
    unittest.main()
