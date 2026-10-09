"""Read-only source/protocol contract checks; does not compile or simulate Minecraft."""
import json
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
JAVA = ROOT / "src/main/java/club/someoneice/humangunner"
NETWORK = (JAVA / "HumanCommandNetwork.java").read_text(encoding="utf-8")
SCREEN = (JAVA / "SoldierCommandScreen.java").read_text(encoding="utf-8")
SNAPSHOT = (JAVA / "GrowthSnapshot.java").read_text(encoding="utf-8")


def section(source, start, end):
    return source.split(start, 1)[1].split(end, 1)[0]


class GrowthUiContractTest(unittest.TestCase):
    def test_snapshot_wire_order_and_client_safe_dependencies(self):
        expected = ["Boolean"] + ["VarInt"] * 5 + ["Double"] * 4
        self.assertEqual(re.findall(r"buffer\.write(\w+)\(", SNAPSHOT), expected)
        self.assertEqual(re.findall(r"buffer\.read(\w+)\(", SNAPSHOT), expected)
        self.assertNotIn("UnifiedConfig", SNAPSHOT)
        self.assertNotIn("HumanGrowth", SNAPSHOT)
        self.assertNotIn("net.minecraft.server", SNAPSHOT)
        self.assertNotIn("net.minecraft.client", SNAPSHOT)

    def test_growth_packets_are_server_to_client_only(self):
        registration = section(NETWORK, "static void register()", "static void openFor")
        for packet in ("OpenScreenPacket", "GrowthUpdatePacket", "InvalidateScreenPacket"):
            self.assertRegex(registration, rf"registerMessage\(\d+, {packet}\.class,[\s\S]*?PLAY_TO_CLIENT")
        self.assertEqual(registration.count("GrowthUpdatePacket.class"), 1)
        self.assertNotRegex(NETWORK, r"sendToServer\(new \w*Growth")

    def test_only_server_produces_snapshots(self):
        self.assertEqual(NETWORK.count("HumanGrowth.snapshot(human)"), 2)
        for forbidden in ("UnifiedConfig", "HumanGrowth", "getPersistentData"):
            self.assertNotIn(forbidden, SCREEN)
        self.assertNotIn("UnifiedConfig", NETWORK)

    def test_subscription_revalidates_all_identity_and_access_constraints(self):
        validate = section(NETWORK, "private static Human humanForSession", "static void closeScreen")
        for constraint in ("player.isAlive()", "dimension().equals(session.dimension())",
                           "getEntity(session.entityId())", "human.level() == player.level()",
                           "human.isAlive()", "session.human().equals(human.getUUID())",
                           "HumanRelations.isOwnedBy(human, player)", "distanceToSqr(human) <= 64.0D * 64.0D"):
            self.assertIn(constraint, validate)

    def test_tick_is_bounded_and_change_only(self):
        tick = section(NETWORK, "public static void tickServer", "private static Human humanForSession")
        self.assertIn("server.getTickCount() % 10 != 0", tick)
        self.assertIn("server.getPlayerList().getPlayer(player.getUUID()) != player", tick)
        self.assertIn("humanForSession(player, session)", tick)
        self.assertIn("if (!growth.equals(session.growth()))", tick)
        self.assertIn("iterator.remove()", tick)

    def test_old_update_and_close_cannot_touch_new_panel(self):
        update = section(SCREEN, "static void updateGrowth", "static void invalidate")
        for identity in ("screen.sessionToken.equals(token)", "screen.humanId.equals(humanId)",
                         "screen.entityId == entityId"):
            self.assertIn(identity, update)
        close = section(NETWORK, "private record CloseScreenPacket", "private record InvalidateScreenPacket")
        self.assertIn("session.token().equals(packet.token)", close)
        removed = section(SCREEN, "public void removed()", "private static String amount")
        self.assertIn("HumanCommandNetwork.closeScreen(sessionToken)", removed)
        self.assertIn("screen.sessionToken.equals(token)", section(SCREEN, "static void invalidate", "private float panelScale"))

    def test_disabled_growth_hides_block_and_removes_layout_space(self):
        self.assertIn("return growth.enabled() ? 96 : 0", SCREEN)
        self.assertIn("if (!growth.enabled()) return", SCREEN)
        self.assertIn("top += growthHeight()", SCREEN)
        click = section(SCREEN, "public boolean mouseClicked", "public boolean isPauseScreen")
        self.assertIn("top = top() + growthHeight()", click)
        self.assertNotIn("growth.", click)

    def test_existing_command_and_dismiss_validation_remain(self):
        validate = section(NETWORK, "private static Human ownedHumanForCommand", "private record OpenScreenPacket")
        for constraint in ("human.isAlive()", "HumanRelations.isOwnedBy(human, player)",
                           "distanceToSqr(human) <= 64.0D * 64.0D"):
            self.assertIn(constraint, validate)
        self.assertEqual(NETWORK.count("ownedHumanForCommand(player, packet.entityId)"), 3)
        dismiss = section(NETWORK, "private record DismissPacket", "private record GrowthUpdatePacket")
        for constraint in ("session.token().equals(packet.token)", "session.human().equals(human.getUUID())",
                           "humanForSession(player, session) != human", "CommandRateLimit.allow(player)"):
            self.assertIn(constraint, dismiss)

    def test_growth_translations_complete_and_parameter_compatible(self):
        base = ROOT / "src/main/resources/assets/hostile_humans_unified/lang"
        tables = [json.loads((base / name).read_text(encoding="utf-8")) for name in ("zh_cn.json", "en_us.json")]
        keys = set(re.findall(r'"(screen\.hostile_humans_unified\.soldier\.growth\.[^"]+)"', SCREEN))
        self.assertEqual(len(keys), 6)
        for key in keys:
            self.assertIn(key, tables[0])
            self.assertIn(key, tables[1])
            self.assertEqual(re.findall(r"%[sd]", tables[0][key]), re.findall(r"%[sd]", tables[1][key]))


if __name__ == "__main__":
    unittest.main(verbosity=2)
