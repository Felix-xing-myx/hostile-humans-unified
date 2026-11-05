package dev.felix.hostilehumans.loot.client;

import com.google.gson.JsonParser;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.Minecraft;

/** Local author drafts only; server sessions, permissions and publication revisions are never restored. */
final class LootDraftCache {
    private static Path file(){
        var mc=Minecraft.getInstance();
        String scope=mc.getSingleplayerServer()!=null
                ? mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().toString()
                : mc.getCurrentServer()!=null?mc.getCurrentServer().ip:"disconnected";
        return mc.gameDirectory.toPath().resolve("hostile_humans_unified/loot-drafts")
                .resolve(java.util.UUID.nameUUIDFromBytes(scope.getBytes(StandardCharsets.UTF_8))+".json");
    }
    static void save(LootEditorScreen screen){
        try{
            Path file=file();String json=screen.cacheState().toString();
            if(Files.exists(file)&&Files.readString(file).equals(json))return;
            Files.createDirectories(file.getParent());Path pending=file.resolveSibling(file.getFileName()+".tmp");
            Files.writeString(pending,json,StandardCharsets.UTF_8);
            try{Files.move(pending,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException unsupported){Files.move(pending,file,StandardCopyOption.REPLACE_EXISTING);}
        }catch(Exception error){com.mojang.logging.LogUtils.getLogger().warn("Cannot cache loot author draft",error);}
    }
    static void restore(LootEditorScreen screen){
        try{Path file=file();if(Files.exists(file)&&Files.size(file)<=2_000_000)screen.restoreCache(JsonParser.parseString(Files.readString(file)).getAsJsonObject());}
        catch(Exception error){
            try{Path bad=file();if(Files.exists(bad))Files.copy(bad,bad.resolveSibling(bad.getFileName()+".unreadable-"+System.currentTimeMillis()));}
            catch(Exception backup){error.addSuppressed(backup);}
            com.mojang.logging.LogUtils.getLogger().warn("Unreadable loot draft backed up when possible",error);
        }
    }
}
