package club.someoneice.humangunner;

import com.craftix.hostile_humans.Config;
import com.craftix.hostile_humans.entity.data.HumanData;
import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Server-only dialogue. Does not acquire targets, navigate, or modify inventory. */
public final class SoldierDialogue {
    private static final String FOOD_WARNED = "humangunner:dialogue_food_empty";
    private static final String NEXT_IDLE = "humangunner:dialogue_next_idle";
    private static final String REPORT_OWNER = "humangunner:dialogue_report_owner";
    private static final Map<Human, State> STATES = new WeakHashMap<>();
    private static final class State {
        UUID owner;
        boolean fighting;
        long quietUntil;
    }

    private SoldierDialogue() {}

    static void hired(ServerPlayer owner, Human human) {
        human.getPersistentData().remove(FOOD_WARNED);
        human.getPersistentData().putLong(NEXT_IDLE, human.level().getGameTime() + 2400);
        STATES.remove(human);
        SoldierCommandFeedback.say(owner, human, Component.translatable(
                "dialogue.humangunner.hired." + human.getRandom().nextInt(3))
                .append(" ").append(Component.translatable("message.humangunner.hired")));
    }

    public static void hostile(Human human, ServerPlayer player) {
        if (human.level().isClientSide || human.hasOwner() || HumanRelations.protects(human, player)
                || !human.canAttack(player) || human.getTags().contains("greeted")
                || human.getRandom().nextDouble() >= Config.greetChance.get()) return;
        human.addTag("greeted");
        SoldierCommandFeedback.say(player, human, Component.translatable(
                "dialogue.humangunner.hostile." + human.getRandom().nextInt(6)));
    }

    static void tick(Human human) {
        // Spread supply scans and chatter over entities, once per five seconds.
        if (!human.hasOwner() || !human.isAlive() || human.level().isClientSide
                || (human.tickCount + (human.getId() & 127)) % 100 != 0) return;
        ServerPlayer owner = human.getServer().getPlayerList().getPlayer(human.getOwnerUUID());
        if (owner == null) return;
        State state = STATES.computeIfAbsent(human, ignored -> new State());
        long now = human.level().getGameTime();
        if (!owner.getUUID().equals(state.owner)) {
            state.owner = owner.getUUID();
            state.fighting = false;
            state.quietUntil = Math.max(state.quietUntil, now);
            if (!human.getPersistentData().hasUUID(REPORT_OWNER)
                    || !owner.getUUID().equals(human.getPersistentData().getUUID(REPORT_OWNER))) {
                human.getPersistentData().remove(FOOD_WARNED);
                human.getPersistentData().putUUID(REPORT_OWNER, owner.getUUID());
            }
        }
        HumanData data = human.getData();
        if (data != null && !RecoverySupplies.hasActiveUse(human)) {
            boolean food = edible(human.getMainHandItem()) || edible(human.getOffhandItem());
            for (int i = 0; !food && i < data.getInventoryItemsSize(); i++)
                food = edible(data.getInventoryItem(i));
            if (food) human.getPersistentData().remove(FOOD_WARNED);
            else if (!human.getPersistentData().getBoolean(FOOD_WARNED)) {
                SoldierMessageDispatcher.report(owner, human, "food_empty",
                        Component.translatable("dialogue.humangunner.food_empty"), false);
                human.getPersistentData().putBoolean(FOOD_WARNED, true);
            }
        }
        boolean fighting = human.getTarget() != null && human.getTarget().isAlive();
        if (fighting != state.fighting) {
            state.fighting = fighting;
            if (now >= state.quietUntil) {
                SoldierMessageDispatcher.chatter(owner, human, Component.translatable(fighting
                        ? "dialogue.humangunner.combat.start" : "dialogue.humangunner.combat.end"));
                state.quietUntil = now + 1200;
            }
        }
        if (fighting || human.isUsingItem() || human.isInWater() || human.isOnFire()
                || human.getHealth() < human.getMaxHealth() * 0.75F
                || owner.level() != human.level() || human.distanceToSqr(owner) > 64
                || now < state.quietUntil || !human.getSensing().hasLineOfSight(owner)) return;
        long next = human.getPersistentData().getLong(NEXT_IDLE);
        if (next == 0) {
            human.getPersistentData().putLong(NEXT_IDLE, now + 2400 + human.getRandom().nextInt(2400));
            return;
        }
        if (now < next) return;
        String key;
        if (human.level().isRainingAt(human.blockPosition())) key = "rain";
        else if (human.level().isNight()) key = "night";
        else if (human.getNavigation().getPath() != null && !human.getNavigation().getPath().isDone()
                && Math.abs(human.getNavigation().getPath().getNextNodePos().getY()
                - human.blockPosition().getY()) >= 1) key = "rough_terrain";
        else if (human.getNavigation().isDone() && human.getRandom().nextInt(4) == 0) key = "rest";
        else key = "idle." + human.getRandom().nextInt(12);
        SoldierMessageDispatcher.chatter(owner, human, Component.translatable("dialogue.humangunner." + key));
        human.getPersistentData().putLong(NEXT_IDLE, now + 2400 + human.getRandom().nextInt(2400));
    }

    private static boolean edible(ItemStack stack) {
        return !stack.isEmpty() && stack.isEdible();
    }

    static void retreat(Human human) {
        event(human, "retreat");
    }

    public static void event(Human human, String event) {
        ServerPlayer owner = owner(human);
        if (owner == null) return;
        State state = STATES.computeIfAbsent(human, ignored -> new State());
        long now = human.level().getGameTime();
        if (now < state.quietUntil) return;
        SoldierMessageDispatcher.chatter(owner, human, Component.translatable("dialogue.humangunner." + event));
        state.quietUntil = now + 1200;
    }

    static void broken(Human human, EquipmentSlot slot, Component itemName, boolean shield) {
        ServerPlayer owner = owner(human);
        if (owner == null) return;
        String type = slot.getType() == EquipmentSlot.Type.ARMOR ? "armor" : shield ? "shield" : "weapon";
        SoldierMessageDispatcher.report(owner, human, "broken_" + slot.name(),
                Component.translatable("dialogue.humangunner.broken." + type, itemName), false);
    }

    private static ServerPlayer owner(Human human) {
        return human.level().isClientSide || !human.hasOwner() || human.getServer() == null
                ? null : human.getServer().getPlayerList().getPlayer(human.getOwnerUUID());
    }
}
