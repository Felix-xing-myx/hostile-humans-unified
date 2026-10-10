"""Static integration contracts only; no compilation or Minecraft runtime claims."""
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
JAVA = ROOT / "src/main/java/club/someoneice/humangunner"
NET = (JAVA / "SoldierRosterNetwork.java").read_text(encoding="utf-8")
UI = (JAVA / "SoldierRosterScreen.java").read_text(encoding="utf-8")
GROWTH = (JAVA / "HumanGrowth.java").read_text(encoding="utf-8")

class RosterGrowthContract(unittest.TestCase):
    def test_nullable_wire_snapshot_and_protocol(self):
        self.assertIn('PROTOCOL = "6"', NET)
        self.assertIn("GrowthSnapshot growth) {}", NET)
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
        self.assertIn("entry.growth().enabled() ? 56 : 0", UI)
        self.assertIn("HEIGHT + growthHeight()", UI)
        render = UI.split("private void renderDetail", 1)[1].split("public boolean mouseClicked", 1)[0]
        click = UI.split("private boolean clickDetail", 1)[1]
        for source in (render, click):
            self.assertIn("y + 201 + growthHeight()", source)
            self.assertIn("y + 223 + growthHeight()", source)
        self.assertIn("if (growthHeight() == 0) return", UI)
        self.assertIn('roster.last_known"', UI)
        self.assertIn("statusRefreshTicks >= 100", UI)

    def test_translations_and_full_progress_display(self):
        for lang in ("en_us", "zh_cn"):
            labels = json.loads((ROOT / "src/main/resources/assets/hostile_humans_unified/lang" / (lang + ".json")).read_text(encoding="utf-8"))
            self.assertIn("screen.hostile_humans_unified.roster.growth_summary", labels)
        for value in ("stageProgress()", "stageRequired()", "experienceProgress()", "experienceRequired()"):
            self.assertIn(value, UI)

if __name__ == "__main__":
    unittest.main()
