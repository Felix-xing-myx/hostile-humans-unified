"""Verify remote-view injection seams against actual 1.20.1 Forge bytecode.

No Gradle invocation or project compilation. Override HHU_MAPPED_JAR/JAVAP when needed.
These tests deliberately inspect the native methods rather than mirror our implementation.
"""
import os
import re
import subprocess
import unittest
from pathlib import Path

JAR = Path(os.environ.get("HHU_MAPPED_JAR", r"E:\GradleUserHome\caches\forge_gradle\minecraft_user_repo\net\minecraftforge\forge\1.20.1-47.4.16_mapped_official_1.20.1\forge-1.20.1-47.4.16_mapped_official_1.20.1.jar"))
JAVAP = Path(os.environ.get("JAVAP", r"D:\Java\17\bin\javap.exe"))


def methods(class_name):
    result = subprocess.run([str(JAVAP), "-classpath", str(JAR), "-p", "-c", "-s", class_name],
                            check=True, capture_output=True, text=True, encoding="utf-8")
    return re.split(r"(?m)(?=^  (?:public|private|protected|void|boolean))", result.stdout)


def method(blocks, declaration):
    matches = [block for block in blocks if block.startswith("  " + declaration)]
    if len(matches) != 1:
        raise AssertionError(f"Expected one {declaration}: {len(matches)}")
    return matches[0]


@unittest.skipUnless(JAR.is_file() and JAVAP.is_file(), "Mapped Forge jar / javap unavailable")
class RemoteViewNativeSeams(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.chunk_map = methods("net.minecraft.server.level.ChunkMap")
        cls.player = methods("net.minecraft.server.level.ServerPlayer")

    def test_chunk_center_sources_are_local_to_tracking(self):
        for declaration in ["public void move(net.minecraft.server.level.ServerPlayer);",
                            "void updatePlayerStatus(net.minecraft.server.level.ServerPlayer, boolean);"]:
            block = method(self.chunk_map, declaration)
            self.assertEqual(block.count("// Method net/minecraft/server/level/ServerPlayer.getBlockX:()I"), 1)
            self.assertEqual(block.count("// Method net/minecraft/server/level/ServerPlayer.getBlockZ:()I"), 1)
            self.assertEqual(block.count("// Method net/minecraft/core/SectionPos.of:(Lnet/minecraft/world/level/entity/EntityAccess;)Lnet/minecraft/core/SectionPos;"), 1)
        block = method(self.chunk_map, "private net.minecraft.core.SectionPos updatePlayerPos(net.minecraft.server.level.ServerPlayer);")
        self.assertIn("ServerPlayer.setLastSectionPos", block)
        self.assertIn("ClientboundSetChunkCacheCenterPacket", block)
        self.assertEqual(block.count("// Method net/minecraft/core/SectionPos.of:"), 1)

    def test_native_entity_pairing_has_separate_view_origin(self):
        block = method(methods("net.minecraft.server.level.ChunkMap$TrackedEntity"),
                       "public void updatePlayer(net.minecraft.server.level.ServerPlayer);")
        self.assertEqual(block.count("// Method net/minecraft/server/level/ServerPlayer.position:()Lnet/minecraft/world/phys/Vec3;"), 1)
        self.assertEqual(block.count("// Method net/minecraft/world/entity/Entity.position:()Lnet/minecraft/world/phys/Vec3;"), 1)
        self.assertIn("Entity.broadcastToPlayer", block)
        self.assertIn("ServerEntity.addPairing", block)
        self.assertIn("ServerEntity.removePairing", block)

    def test_native_send_waits_for_block_ticking_chunk(self):
        block = method(self.chunk_map, "protected void updateChunkTracking(")
        self.assertIn("ChunkHolder.getTickingChunk", block)
        self.assertIn("playerLoadedChunk", block)
        self.assertIn("ServerPlayer.untrackChunk", block)

    def test_server_camera_is_not_a_safe_body_preserving_entry(self):
        block = method(self.player, "public void setCamera(net.minecraft.world.entity.Entity);")
        self.assertIn("Method teleportTo:", block)
        options = method(self.player, "public void updateOptions(net.minecraft.network.protocol.game.ServerboundClientInformationPacket);")
        self.assertNotIn("ServerboundClientInformationPacket.viewDistance", options)


if __name__ == "__main__":
    unittest.main()
