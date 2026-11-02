package dev.felix.hostilehumans.perception;

import dev.felix.hostilehumans.bootstrap.HumanRuntime;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraftforge.event.VanillaGameEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;

/** Native audible actions feed clues, never target permission or movement ownership. */
@Mod.EventBusSubscriber(modid = HumanRuntime.MOD_ID)
public final class HumanSensoryEvents {
    private HumanSensoryEvents() { }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEvent(VanillaGameEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!level.getServer().isSameThread()) return; // World-generation work is not an audible live action.
        Entity actor = event.getCause();
        if (actor instanceof Projectile projectile) actor = projectile.getOwner();
        GameEvent type = event.getVanillaEvent();
        String kind = kind(type);
        if (type == GameEvent.PROJECTILE_SHOOT && actor instanceof LivingEntity shooter) {
            boolean crossbow = shooter.getMainHandItem().getItem() instanceof CrossbowItem
                    || shooter.getOffhandItem().getItem() instanceof CrossbowItem;
            boolean bow = shooter.getMainHandItem().getItem() instanceof BowItem
                    || shooter.getOffhandItem().getItem() instanceof BowItem;
            kind = crossbow ? "crossbowshot" : bow ? "bowshot" : "throw";
        }
        if(type==GameEvent.ITEM_INTERACT_START && actor instanceof LivingEntity user
                &&user.getUseItem().getItem() instanceof CrossbowItem)kind="reload";
        if (kind != null) HumanAwareness.sensory(level, actor, kind, event.getEventPosition());
    }
    static String kind(GameEvent event) {
        if (event == GameEvent.PROJECTILE_SHOOT) return "throw";
        if (event == GameEvent.PROJECTILE_LAND) return "impact";
        if (event == GameEvent.HIT_GROUND) return "landing";
        if (event == GameEvent.SPLASH) return "splash";
        if (event == GameEvent.CONTAINER_OPEN || event == GameEvent.CONTAINER_CLOSE) return "container";
        if (event == GameEvent.BLOCK_OPEN || event == GameEvent.BLOCK_CLOSE) return "door";
        if (event == GameEvent.BLOCK_ACTIVATE || event == GameEvent.BLOCK_DEACTIVATE
                || event == GameEvent.BLOCK_ATTACH || event == GameEvent.BLOCK_DETACH) return "mechanism";
        if (event == GameEvent.BLOCK_PLACE || event == GameEvent.BLOCK_DESTROY) return "block";
        if (event == GameEvent.FLUID_PICKUP || event == GameEvent.FLUID_PLACE) return "splash";
        if (event == GameEvent.EAT) return "eat";
        if (event == GameEvent.DRINK) return "drink";
        if (event == GameEvent.EQUIP || event == GameEvent.SHEAR) return "equipment";
        if (event == GameEvent.PRIME_FUSE) return "fuse";
        if (event == GameEvent.ENTITY_DIE) return "death";
        if (event == GameEvent.ENTITY_ROAR) return "roar";
        if (event == GameEvent.TELEPORT) return "teleport";
        if (event == GameEvent.INSTRUMENT_PLAY || event == GameEvent.NOTE_BLOCK_PLAY
                || event == GameEvent.JUKEBOX_PLAY) return "instrument";
        if (event == GameEvent.SHRIEK) return "shriek";
        if (event == GameEvent.LIGHTNING_STRIKE) return "thunder";
        return null; // No generic BLOCK_CHANGE, vibration-feedback or ambient-animal radar.
    }
}
