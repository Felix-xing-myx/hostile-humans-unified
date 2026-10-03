package com.craftix.hostile_humans;

import com.craftix.hostile_humans.Config;
import java.util.Arrays;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public class HumanUtil {
    public static ItemStack[] EDIBLE_ITEMS = new ItemStack[]{Items.APPLE.getDefaultInstance(), Items.BREAD.getDefaultInstance(), Items.COOKED_PORKCHOP.getDefaultInstance(), Items.COOKED_COD.getDefaultInstance(), Items.COOKED_SALMON.getDefaultInstance(), Items.COOKIE.getDefaultInstance(), Items.MELON_SLICE.getDefaultInstance(), Items.COOKED_BEEF.getDefaultInstance(), Items.COOKED_CHICKEN.getDefaultInstance(), Items.CARROT.getDefaultInstance(), Items.POTATO.getDefaultInstance(), Items.BAKED_POTATO.getDefaultInstance(), Items.GOLDEN_CARROT.getDefaultInstance(), Items.PUMPKIN_PIE.getDefaultInstance(), Items.RABBIT.getDefaultInstance(), Items.COOKED_RABBIT.getDefaultInstance(), Items.RABBIT_STEW.getDefaultInstance(), Items.MUTTON.getDefaultInstance(), Items.COOKED_MUTTON.getDefaultInstance(), Items.BEETROOT.getDefaultInstance(), Items.DRIED_KELP.getDefaultInstance(), Items.SWEET_BERRIES.getDefaultInstance(), Items.GLOW_BERRIES.getDefaultInstance()};
    public static ItemStack[] EDIBLE_ITEMS_2 = new ItemStack[]{Items.APPLE.getDefaultInstance(), Items.BREAD.getDefaultInstance(), Items.COOKED_PORKCHOP.getDefaultInstance(), Items.COOKED_COD.getDefaultInstance(), Items.COOKED_SALMON.getDefaultInstance(), Items.COOKIE.getDefaultInstance(), Items.MELON_SLICE.getDefaultInstance(), Items.COOKED_BEEF.getDefaultInstance(), Items.COOKED_CHICKEN.getDefaultInstance(), Items.CARROT.getDefaultInstance(), Items.POTATO.getDefaultInstance(), Items.BAKED_POTATO.getDefaultInstance(), Items.GOLDEN_CARROT.getDefaultInstance(), Items.PUMPKIN_PIE.getDefaultInstance(), Items.COOKED_RABBIT.getDefaultInstance(), Items.RABBIT_STEW.getDefaultInstance(), Items.COOKED_MUTTON.getDefaultInstance(), Items.BEETROOT.getDefaultInstance(), Items.DRIED_KELP.getDefaultInstance(), Items.SWEET_BERRIES.getDefaultInstance(), Items.GLOW_BERRIES.getDefaultInstance()};

    public static boolean isStructureDisabled(String value) {
        return Arrays.asList(((String)Config.disabledStructures.get()).replace(" ", "").split(",")).contains(value);
    }

    public static boolean isRangedWeapon(ItemStack value) {
        if (club.someoneice.humangunner.GunSupport.get().isGun(value) || club.someoneice.humangunner.SpartanEquipmentCompat.isSpartanRangedWeapon(value)) return true;

        return value.is(Items.CROSSBOW) || value.is(Items.BOW);
    }

    public static boolean isMeleeWeapon(ItemStack value) {
        if (club.someoneice.humangunner.SpartanEquipmentCompat.isSpartanMeleeWeapon(value)) return true;

        return !value.isEmpty() && (value.getItem() instanceof SwordItem || value.getItem() instanceof AxeItem);
    }

    public static boolean isTrident(ItemStack value) {
        return !value.isEmpty() && value.getItem() instanceof TridentItem;
    }

    public static boolean isShield(ItemStack value) {
        if (club.someoneice.humangunner.SpartanEquipmentCompat.isShield(value)) return true;

        return !value.isEmpty() && value.getItem() instanceof ShieldItem;
    }

    public static boolean isLookingAtTarget(LivingEntity mob, Entity target) {
        Vec3 vec3 = mob.getViewVector(1.0f).normalize();
        Vec3 vec31 = new Vec3(target.getX() - mob.getX(), target.getEyeY() - mob.getEyeY(), target.getZ() - mob.getZ());
        double d0 = vec31.length();
        double d1 = vec3.dot(vec31 = vec31.normalize());
        return d1 > 1.0 - 0.4 / d0 && mob.hasLineOfSight(target);
    }

    @NotNull
    public static ItemStack createSwordBanner() {
        ItemStack banner = Items.WHITE_BANNER.getDefaultInstance();
        CompoundTag blockData = banner.getOrCreateTagElement("BlockEntityTag");
        ListTag patterns = new ListTag();
        CompoundTag c1 = new CompoundTag();
        c1.putInt("Color", 4);
        c1.putString("Pattern", "flo");
        patterns.add(c1);
        c1 = new CompoundTag();
        c1.putInt("Color", 7);
        c1.putString("Pattern", "hh");
        patterns.add(c1);
        c1 = new CompoundTag();
        c1.putInt("Color", 0);
        c1.putString("Pattern", "cs");
        patterns.add(c1);
        c1 = new CompoundTag();
        c1.putInt("Color", 7);
        c1.putString("Pattern", "br");
        patterns.add(c1);
        c1 = new CompoundTag();
        c1.putInt("Color", 7);
        c1.putString("Pattern", "bl");
        patterns.add(c1);
        c1 = new CompoundTag();
        c1.putInt("Color", 7);
        c1.putString("Pattern", "cbo");
        patterns.add(c1);
        blockData.put("Patterns", (Tag)patterns);
        return banner;
    }

    public static boolean isLadder(BlockState state, LivingEntity entity, BlockPos pos) {
        return state.isLadder((LevelReader)entity.level(), pos, entity);
    }

    public static int createLadderNodeFor(int nodeID, Node[] nodes, Node origin, Function<BlockPos, Node> nodeGetter, BlockGetter getter, Mob mob) {
        Node node;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(origin.x, origin.y + 1, origin.z);
        if (HumanUtil.isLadder(getter.getBlockState((BlockPos)pos), (LivingEntity)mob, (BlockPos)pos) && (node = nodeGetter.apply((BlockPos)pos)) != null && !node.closed) {
            node.costMalus = 0.0f;
            node.type = BlockPathTypes.WALKABLE;
            if (nodeID + 1 < nodes.length) {
                nodes[nodeID++] = node;
            }
        }
        pos.set(pos.getX(), pos.getY() - 2, pos.getZ());
        if (HumanUtil.isLadder(getter.getBlockState((BlockPos)pos), (LivingEntity)mob, (BlockPos)pos) && (node = nodeGetter.apply((BlockPos)pos)) != null && !node.closed) {
            node.costMalus = 0.0f;
            node.type = BlockPathTypes.WALKABLE;
            if (nodeID + 1 < nodes.length) {
                nodes[nodeID++] = node;
            }
        }
        return nodeID;
    }

    public static boolean isLowHp(LivingEntity human) {
        if (human.getOffhandItem().getItem() == Items.TOTEM_OF_UNDYING) {
            return false;
        }
        return (double)human.getHealth() < (double)human.getMaxHealth() * (Double)Config.fleeHpPercent.get();
    }

}

