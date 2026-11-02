package dev.felix.hostilehumans.behavior.combat.melee;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.inventory.HumanInventorySession;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

/** Native mob strikes read their attacking weapon as main hand. Supply the real offhand
 * only during that synchronous calculation, without swapping or broadcasting equipment.
 * Attribute substitution follows BC's offhand-attribute model, isolated from its Player-only API. */
public final class NpcMeleeWeaponContext {
    private static final ThreadLocal<Map<Human, ItemStack>> ACTIVE = ThreadLocal.withInitial(IdentityHashMap::new);
    private record Change(AttributeInstance attribute, UUID id, AttributeModifier before,
                          boolean permanent, AttributeModifier during) { }
    private NpcMeleeWeaponContext() { }

    public static ItemStack effectiveMainHand(Human human, ItemStack actual) {
        return ACTIVE.get().getOrDefault(human, actual);
    }
    static double attackSpeed(Human human, ItemStack weapon) {
        return withWeapon(human, weapon, human.getAttributeValue(Attributes.ATTACK_SPEED),
                () -> human.getAttributeValue(Attributes.ATTACK_SPEED));
    }
    public static boolean strike(Human human, ItemStack weapon,
            dev.felix.hostilehumans.behavior.ActionCoordinator.Token expected, BooleanSupplier strike) {
        return withWeapon(human, weapon, false, () -> MeleeActionControl.impactValid(human, expected)
                && strike.getAsBoolean());
    }
    private static <T> T withWeapon(Human human, ItemStack weapon, T unavailable, Supplier<T> action) {
        ItemStack main = human.getItemBySlot(EquipmentSlot.MAINHAND);
        ItemStack off = human.getItemBySlot(EquipmentSlot.OFFHAND);
        if (human.level().isClientSide || weapon.isEmpty() || (weapon != main && weapon != off)) return unavailable;
        var active = ACTIVE.get();
        ItemStack nested = active.get(human);
        if (nested != null) return nested == weapon ? action.get() : unavailable;
        if (weapon == main) return action.get();

        return withAttributeModifiers(human.getAttributes(), main.getAttributeModifiers(EquipmentSlot.MAINHAND),
                weapon.getAttributeModifiers(EquipmentSlot.MAINHAND), () -> {
                    // Attribute hooks may edit equipment or revoke the action while resolving modifiers.
                    if (main != human.getItemBySlot(EquipmentSlot.MAINHAND)
                            || off != human.getItemBySlot(EquipmentSlot.OFFHAND) || weapon.isEmpty()
                            || !human.isAlive() || human.isRemoved() || HumanInventorySession.isEditing(human))
                        return unavailable;
                    active.put(human, weapon);
                    try { return action.get(); }
                    finally {
                        active.remove(human);
                        if (active.isEmpty()) ACTIVE.remove();
                    }
                });
    }
    /** Testable synchronous attribute transaction; no slots, inventory, health or base values are written. */
    public static <T> T withAttributeModifiers(net.minecraft.world.entity.ai.attributes.AttributeMap attributes,
            com.google.common.collect.Multimap<Attribute, AttributeModifier> main,
            com.google.common.collect.Multimap<Attribute, AttributeModifier> weapon, Supplier<T> action) {
        Map<Attribute, Map<UUID, AttributeModifier>> replacements = new LinkedHashMap<>();
        main.entries().forEach(entry ->
                replacements.computeIfAbsent(entry.getKey(), ignored -> new LinkedHashMap<>())
                        .put(entry.getValue().getId(), null));
        weapon.entries().forEach(entry ->
                replacements.computeIfAbsent(entry.getKey(), ignored -> new LinkedHashMap<>())
                        .put(entry.getValue().getId(), entry.getValue()));
        var changes = new ArrayList<Change>();
        replacements.forEach((attribute, modifiers) -> {
            AttributeInstance instance = attributes.getInstance(attribute);
            if (instance == null) return;
            modifiers.forEach((id, modifier) -> changes.add(new Change(instance, id, instance.getModifier(id), false, modifier)));
        });
        int applied = 0;
        try {
            for (int i = 0; i < changes.size(); i++) {
                Change change = changes.get(i);
                applied++;
                boolean permanent = change.attribute().removePermanentModifier(change.id());
                changes.set(i, new Change(change.attribute(), change.id(), change.before(), permanent, change.during()));
                change.attribute().removeModifier(change.id());
                if (change.during() != null) change.attribute().addTransientModifier(change.during());
            }
            return action.get();
        } finally {
            for (int i = applied - 1; i >= 0; i--) {
                Change change = changes.get(i);
                AttributeModifier current = change.attribute().getModifier(change.id());
                // A foreign callback's new modifier is not ours to overwrite.
                if (current != null && current != change.during()) continue;
                change.attribute().removeModifier(change.id());
                if (change.before() != null) {
                    if (change.permanent()) change.attribute().addPermanentModifier(change.before());
                    else change.attribute().addTransientModifier(change.before());
                }
            }
        }
    }
}
