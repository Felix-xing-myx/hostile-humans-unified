package club.someoneice.humangunner;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.Bootstrap;

/** Spawn adapters preserve legal author payload and spawner tuning while fixing identity. */
public final class FactionContentNbtTest {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        // Standalone JavaExec lacks Forge's event-bus transformation; skip the network hook.
        var bootstrapFlag=Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrapFlag.setAccessible(true);
        bootstrapFlag.setBoolean(null,true);
        BuiltInRegistries.bootStrap();
        CompoundTag entity=new CompoundTag(), forge=new CompoundTag();
        entity.putString("CustomName","{\"text\":\"Author NPC\"}");entity.putString("id","minecraft:pig");
        ListTag hands=new ListTag();CompoundTag sword=new CompoundTag();sword.putString("id","minecraft:iron_sword");sword.putByte("Count",(byte)1);hands.add(sword);entity.put("HandItems",hands);
        forge.putString("author:custom","preserve");forge.putString(HumanFactions.FACTION,"hostile_humans_unified:crimson");entity.put("ForgeData",forge);
        CompoundTag original=entity.copy();
        var fixed=FactionSpawnEggItem.identityTag(entity,"hostile_humans_unified:human_tier2","hostile_humans_unified:azure",false,true);
        check(entity.equals(original),"source author NBT unchanged");
        check(fixed.getString("id").equals("hostile_humans_unified:human_tier2"),"fixed type");
        check(fixed.getCompound("ForgeData").getString(HumanFactions.FACTION).equals("hostile_humans_unified:azure"),"fixed faction");
        check(!fixed.getCompound("ForgeData").getBoolean(HumanFactions.BOSS),"ordinary cannot inherit boss flag");
        check(fixed.getCompound("ForgeData").getBoolean(FactionContent.SPAWNER_BIRTH),"spawner birth marker");
        check(fixed.get("HandItems").equals(hands)&&fixed.getString("CustomName").equals(entity.getString("CustomName")),"explicit equipment/name retained");
        check(fixed.getCompound("ForgeData").getString("author:custom").equals("preserve"),"other ForgeData retained");
        var direct=FactionSpawnEggItem.identityTag(fixed,"hostile_humans_unified:human_tier2","hostile_humans_unified:azure",false,false);
        check(!direct.getCompound("ForgeData").contains(FactionContent.SPAWNER_BIRTH),"ordinary egg has no spawner lease");
        CompoundTag spawner=new CompoundTag();spawner.putShort("Delay",(short)123);spawner.putShort("SpawnCount",(short)2);spawner.putShort("SpawnRange",(short)9);
        CompoundTag data=new CompoundTag(), rules=new CompoundTag();rules.putString("author_rule","keep");data.put("custom_spawn_rules",rules);spawner.put("SpawnData",data);spawner.put("SpawnPotentials",new ListTag());
        var saved=spawner.copy();var replaced=FactionSpawnEggItem.spawnerTag(spawner,fixed);
        check(spawner.equals(saved),"source spawner unchanged");
        check(replaced.getShort("Delay")==123&&replaced.getShort("SpawnCount")==2&&replaced.getShort("SpawnRange")==9,"spawner timing/count/range preserved");
        check(replaced.getCompound("SpawnData").getCompound("custom_spawn_rules").equals(rules),"spawn rules preserved");
        check(replaced.getCompound("SpawnData").getCompound("entity").equals(fixed),"selected identity saved");
        check(!replaced.contains("SpawnPotentials"),"old identity pool replaced by engine single-current fallback");
        System.out.println("FactionContentNbtTest: all checks passed");
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
