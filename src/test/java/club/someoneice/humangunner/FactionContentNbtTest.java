package club.someoneice.humangunner;

import net.minecraft.SharedConstants;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.BooleanOp;

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
        checkBossDoorwayClearance();
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
    private static void checkBossDoorwayClearance() {
        var ordinary = EntityDimensions.scalable(.6F, 1.8F);
        var boss = FactionBossHuman.collisionDimensions(ordinary);
        check(Math.abs(boss.width - .6F) < 1.0E-6F && Math.abs(boss.height - 1.95F) < 1.0E-6F,
                "boss uses doorway-sized physical bounds");
        check(Mth.floor(boss.width + 1) == 1
                && Mth.floor(boss.height + 1) == 2,
                "vanilla node volume stays one wide and two high");
        check(Mth.floor(2.0F + 1) == 3, "old exact two-block cap required three path layers");
        var body = Shapes.create(boss.makeBoundingBox(.5, 0, .5));
        var oldBody = Shapes.create(
                EntityDimensions.scalable(ordinary.width * HumanFactions.BOSS_BODY_SCALE, 2).makeBoundingBox(.5, 0, .5));
        for (var direction : Direction.Plane.HORIZONTAL) {
            for (var hinge : DoorHingeSide.values()) {
                var door = Blocks.OAK_DOOR.defaultBlockState()
                        .setValue(DoorBlock.FACING, direction)
                        .setValue(DoorBlock.HINGE, hinge)
                        .setValue(DoorBlock.OPEN, true);
                var openShape = door.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                check(!Shapes.joinIsNotEmpty(body, openShape, BooleanOp.AND),
                        "new body clears open door " + direction + "/" + hinge);
                check(Shapes.joinIsNotEmpty(oldBody, openShape, BooleanOp.AND),
                        "old expanded width collides with open door " + direction + "/" + hinge);
                var closedShape = door.setValue(DoorBlock.OPEN, false)
                        .getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                // A closed door lies at the cell edge; the swept body across the full cell must hit it.
                var swept = Shapes.create(boss.makeBoundingBox(.5, 0, .5).inflate(.5, 0, .5));
                check(Shapes.joinIsNotEmpty(swept, closedShape, BooleanOp.AND),
                        "closed doors still obstruct passage");
            }
        }
        var roof = Shapes.box(0, 2, 0, 1, 3, 1);
        check(!Shapes.joinIsNotEmpty(body, roof, BooleanOp.AND),
                "two-block roof leaves real collision clearance");
        var low = FactionBossHuman.collisionDimensions(EntityDimensions.scalable(.2F, .2F));
        check(low.width < .6F && low.height < 1.95F, "short poses are never expanded to standing bounds");
        check(HumanFactions.BOSS_BODY_SCALE == 1.2F, "visual boss scale retained");
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
