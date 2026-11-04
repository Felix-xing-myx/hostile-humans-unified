"""Checks for the handoff from manual template placement to the settlement index."""
from pathlib import Path
import unittest

SETTLEMENT = Path(__file__).resolve().parents[3] / "src/main/java/dev/felix/hostilehumans/settlement"


class SettlementManualPlacementContract(unittest.TestCase):
    def test_normal_and_batched_paths_register_only_after_success(self):
        files = (SETTLEMENT / "SettlementTemplateFiles.java").read_text(encoding="utf-8")
        direct = files.split("static String confirm(ServerPlayer player, UUID wandToken)", 1)[1].split(
            "private static String placed", 1
        )[0]
        self.assertLess(direct.index("pending.template().placeInWorld"), direct.index("return placed"))
        self.assertIn('throw new IllegalArgumentException("template placement failed', direct)
        self.assertIn('if (job.work().advance()) {', files)
        self.assertIn('captureMessage(player, "placement_result", placed(player, preview))', files)

    def test_registration_uses_placed_author_and_shared_index_write(self):
        files = (SETTLEMENT / "SettlementTemplateFiles.java").read_text(encoding="utf-8")
        placed = files.split("private static String placed", 1)[1].split("static void keepPreview", 1)[0]
        for check in ("!author.kind().root()", "bounds.minX() - offset.x()",
                      "info.author().equals(SettlementCodec.template(author))",
                      "explicitTransform(author, position, new CompoundTag())"):
            self.assertLess(placed.index(check), placed.index("SettlementAuthoring.registerRoot"))
        authoring = (SETTLEMENT / "SettlementAuthoring.java").read_text(encoding="utf-8")
        self.assertIn("return registerRoot(player.serverLevel(), info, author,", authoring)
        self.assertIn("if (result.changed()) info.bind(id, key);", authoring)
