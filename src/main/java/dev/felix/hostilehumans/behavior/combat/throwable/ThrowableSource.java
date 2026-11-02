package dev.felix.hostilehumans.behavior.combat.throwable;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.compat.jeg.JegCompat;
import dev.felix.hostilehumans.config.UnifiedConfig;
import dev.felix.hostilehumans.damage.HumanProjectileRelations;
import dev.felix.hostilehumans.faction.HumanRelations;
import dev.felix.hostilehumans.faction.HumanRelationshipPolicy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;

/** Server-authored origin facts, not a frozen victim/alliance list. Native live-owner policy wins. */
public final class ThrowableSource {
    private static final String SOURCE = "hostile_humans_unified:throw_origin";
    private ThrowableSource() { }
    static void capture(Human human, Projectile projectile, ItemStack consumed) {
        if (human.level().isClientSide || projectile.level() != human.level() || projectile.getOwner() != human) return;
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", 1); tag.putUUID("projectile", projectile.getUUID()); tag.putUUID("human", human.getUUID());
        tag.put("identity", HumanRelations.originIdentity(human).save());
        tag.putString("item", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(consumed.getItem()).toString());
        projectile.getPersistentData().put(SOURCE, tag);
    }
    /** Native child factories call this while the originating parent is still available. */
    public static void inherit(Entity parent, Entity child) {
        if (parent == null || child == null || parent.level().isClientSide || parent.level() != child.level()) return;
        CompoundTag source = read(parent);
        if (source == null) return;
        CompoundTag copy = source.copy(); copy.putUUID("projectile", child.getUUID());
        child.getPersistentData().put(SOURCE, copy);
    }
    public static boolean recordedHuman(Entity direct) { return read(direct) != null; }
    public static Entity nativeOwner(Entity entity) {
        if (entity == null) return null;
        if (entity instanceof Projectile projectile) return projectile.getOwner();
        if (entity instanceof net.minecraft.world.entity.AreaEffectCloud cloud) return cloud.getOwner();
        return JegCompat.projectileOwner(entity);
    }
    private static CompoundTag read(Entity entity) {
        if (entity == null || entity.level().isClientSide || !entity.getPersistentData().contains(SOURCE, 10)) return null;
        CompoundTag tag = entity.getPersistentData().getCompound(SOURCE);
        if (tag.getInt("schema") != 1 || !tag.hasUUID("projectile") || !tag.hasUUID("human")
                || !tag.getUUID("projectile").equals(entity.getUUID())) return null;
        // An ordinary player's projectile cannot gain NPC permissions by carrying copied item/entity NBT.
        Entity owner = nativeOwner(entity);
        if (owner != null && (!(owner instanceof Human) || !owner.getUUID().equals(tag.getUUID("human")))) return null;
        return tag;
    }
    /** Fallback only when a native owner cannot be resolved; source remains independently saved on the projectile. */
    public static boolean protectsWithoutOwner(Entity direct, Entity candidate) {
        if (direct == null || candidate == null) return false;
        Entity nativeOwner = nativeOwner(direct);
        if (nativeOwner != null) return false;
        CompoundTag tag = read(direct);
        if (tag == null) return false;
        if (direct.level() instanceof net.minecraft.server.level.ServerLevel level
                && level.getEntity(tag.getUUID("human")) instanceof Human live)
            return HumanProjectileRelations.passesThroughFromOwner(live, candidate);
        if (direct.level().getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) return true;
        HumanRelations.OriginIdentity identity = HumanRelations.OriginIdentity.load(tag.getCompound("identity"));
        if (identity == null || !identity.human().equals(tag.getUUID("human"))) return false;
        if (candidate instanceof Player player)
            return HumanRelationshipPolicy.ownedHumanAlliedToPlayer(identity.effectiveOwner(direct.level().getGameTime()),
                    player.getUUID(), UnifiedConfig.get().allowHiredPvpDamage());
        return candidate instanceof Human target && HumanRelations.originAlliedToHuman(identity, target);
    }
}
