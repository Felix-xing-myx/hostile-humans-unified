package dev.felix.hostilehumans.behavior.combat.melee;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.behavior.combat.gun.GunSupport;
import dev.felix.hostilehumans.config.UnifiedConfig;
import dev.felix.hostilehumans.faction.HumanRelations;
import dev.felix.hostilehumans.progression.EffectiveAttributes;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import dev.felix.hostilehumans.common.CachedReflection;

/** Optional server-side adapter for Better Combat's weapon profiles and melee hitboxes. */
public final class BetterCombatMeleeCombat {
    private static final Logger LOGGER = LogUtils.getLogger();
    static final int PROFILE_RETRY_TICKS = 4, PROFILE_REFRESH_TICKS = 20, MAX_PROFILE_FAILURES = 3;
    private static final java.util.UUID DAMAGE_MODIFIER_ID = java.util.UUID.fromString(
            "9b8bc786-6829-4ac0-9b88-e5260f39e422");
    private static final java.util.UUID SWEEP_MODIFIER_ID = java.util.UUID.fromString(
            "67c45dc7-170d-4c66-9b57-09ff2c9e8c4f");
    private static final Map<Human, ComboState> COMBOS = new WeakHashMap<>();
    private static volatile Api api;
    private static volatile boolean apiLookupAttempted;
    private static volatile boolean warnedAboutApi;
    private static volatile Object betterCombatConfig;

    private BetterCombatMeleeCombat() {}

    public record Profile(double range, Shape shape, double damageMultiplier, double angleDegrees,
                          int cooldownTicks, String animationName, float animationUpswing, boolean offhandAttack) {
        public Profile(double range, Shape shape, double damageMultiplier, double angleDegrees,
                       int cooldownTicks, String animationName, float animationUpswing) {
            this(range, shape, damageMultiplier, angleDegrees, cooldownTicks, animationName, animationUpswing, false);
        }
        public net.minecraft.world.entity.EquipmentSlot attackSlot() {
            return offhandAttack ? net.minecraft.world.entity.EquipmentSlot.OFFHAND : net.minecraft.world.entity.EquipmentSlot.MAINHAND;
        }
    }
    public enum Shape { FORWARD_BOX, VERTICAL_PLANE, HORIZONTAL_PLANE }
    public record AttackPlan(Profile profile, List<LivingEntity> targets) {}
    public record CreeperForecast(Profile subsequent, double firstDamageMultiplier) {}

    private static final class ComboState {
        private ItemStack weapon = ItemStack.EMPTY;
        private ItemStack offhand = ItemStack.EMPTY;
        private ItemStack primary = ItemStack.EMPTY;
        private Object attributes;
        private boolean attributesResolved;
        private boolean lookupAvailable;
        private int lookupFailures;
        private int lastAttributeLookupTick;
        private int index;
        private int lastAttackTick;
    }

    private record Api(Method getAttributes, Field configField) {}
    private record AttributesRead(Object attributes, boolean available) { }
    public enum Backend {
        NATIVE, BETTER_COMBAT, WAIT;
        static Backend choose(boolean configured, boolean bridgeAvailable, boolean lookupAvailable, boolean profile, int failures) {
            if (!configured || !bridgeAvailable) return NATIVE;
            if (!lookupAvailable) return profile || failures < MAX_PROFILE_FAILURES ? WAIT : NATIVE;
            return profile ? BETTER_COMBAT : NATIVE;
        }
    }
    /** Unsupported weapons may use native attacks; a failed lookup must not become a fast native hit. */
    public static Backend backend(Human human) {
        if (!canReadWeaponProfile(human) || human.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) return Backend.NATIVE;
        if (resolveApi() == null) return Backend.NATIVE; // Permanently unsupported optional bridge, logged once.
        Object attributes = weaponAttributes(human);
        ComboState state = comboState(human);
        return Backend.choose(true, true, state.lookupAvailable, attributes != null, state.lookupFailures);
    }

    public static boolean isEnabled(Human human) {
        return human != null && !human.level().isClientSide
                && human.hhu$canMeleeAttack()
                && isConfiguredEnabled();
    }

    public static boolean isConfiguredEnabled() {
        return UnifiedConfig.get().betterCombatSoldierMeleeEnabled() && ModList.get().isLoaded("bettercombat");
    }

    /** Gets the effective Better Combat reach, or zero when its optional path is inactive. */
    public static double attackRange(Human human) {
        if (!canReadWeaponProfile(human)) return 0.0D;
        Object attributes = weaponAttributes(human);
        if (attributes == null || human.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) return 0.0D;
        double range = number(call(attributes, "attackRange"), 3.0D);
        return Double.isFinite(range) ? Math.min(16.0D, Math.max(0.1D, range)) : 3.0D;
    }

    public static int cooldownTicks(Human human) {
        if (!hasWeaponProfile(human)) return 0;
        return profile(human).cooldownTicks();
    }
    /** Air swings use the same selected combo/profile without fabricating a living target or damage. */
    public static Profile manualSwingProfile(Human human) {
        return dev.felix.hostilehumans.behavior.combat.HumanCombatAuthority.live(human) && hasWeaponProfile(human) ? profile(human) : null;
    }

    public static boolean hasWeaponProfile(Human human) {
        return canReadWeaponProfile(human) && !human.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()
                && weaponAttributes(human) != null;
    }

    /** Defensive spacing still needs the weapon's reach while attacks are temporarily blocked. */
    private static boolean canReadWeaponProfile(Human human) {
        return human != null && !human.level().isClientSide
                && !GunSupport.isGunItem(human.getItemBySlot(EquipmentSlot.MAINHAND)) && isConfiguredEnabled();
    }

    /** Single-target geometry check; never performs the sweep's entity query. */
    public static boolean canStrike(Human human, LivingEntity target) {
        if (target == null || !hasWeaponProfile(human)) return false;
        Vec3 origin = MeleeCombatRange.attackOrigin(human);
        Vec3 aimed = MeleeCombatRange.aimPoint(origin, target.getBoundingBox()).subtract(origin);
        // Reach/spacing asks whether we can face and hit, not whether a previous
        // navigation/look tick already happened to face this elevation.
        return canBeHit(human, target, profile(human),
                aimed.lengthSqr() > 1.0E-8D ? aimed.normalize() : human.getViewVector(1.0F));
    }

    /** Resolve the active combo step and collect the hostile entities in its oriented hitbox. */
    public static AttackPlan createAttackPlan(Human human, LivingEntity requestedTarget) {
        if (!isEnabled(human) || !hasWeaponProfile(human) || requestedTarget == null) return null;
        return createAttackPlan(human, requestedTarget, profile(human));
    }

    /** Re-evaluate victims using the profile committed at swing start, never select another combo at impact. */
    public static AttackPlan createAttackPlan(Human human, LivingEntity requestedTarget, Profile profile) {
        if (!isEnabled(human) || requestedTarget == null || profile == null) return null;
        if (profile.damageMultiplier() <= 0) return new AttackPlan(profile, List.of());
        List<LivingEntity> targets = collectTargets(human, profile);
        if (!targets.contains(requestedTarget) && canBeHit(human, requestedTarget, profile)) {
            targets.add(requestedTarget);
        }
        targets.sort(Comparator.comparingDouble(human::distanceToSqr));
        return new AttackPlan(profile, List.copyOf(targets));
    }

    /** Same selected weapon/combo timing as the eventual attack; never advances the combo. */
    public static int upswingTicks(Human human) {
        if (!isEnabled(human) || !hasWeaponProfile(human)) return 0;
        Profile current = profile(human);
        return Math.max(1, Math.round(current.cooldownTicks() * current.animationUpswing()));
    }

    /** Conservative combo forecast; only queried during a nearby creeper's active fuse. */
    public static CreeperForecast creeperForecast(Human human, LivingEntity target) {
        Profile current = profile(human);
        double minimumDamage = current.damageMultiplier();
        float maximumUpswing = current.animationUpswing();
        Object attacks = call(weaponAttributes(human), "attacks");
        if (attacks != null && attacks.getClass().isArray()) {
            int length = Array.getLength(attacks);
            if (length > 64) return new CreeperForecast(new Profile(current.range(), current.shape(), 0,
                    current.angleDegrees(), current.cooldownTicks(), "", 1, current.offhandAttack()), 0);
            for (int i = 0; i < length; i++) {
                Object step = Array.get(attacks, i);
                minimumDamage = Math.min(minimumDamage, Math.max(0, number(call(step, "damageMultiplier"), 1)));
                maximumUpswing = Math.max(maximumUpswing,
                        (float) Math.min(1, Math.max(0.05, number(call(step, "upswing"), 0.5))));
            }
        }
        // Reuse the actual attack hitbox rather than penalizing an isolated creeper as a full crowd.
        // Avoidance rechecks this estimate as the fuse advances and nearby targets change.
        AttackPlan plan = createAttackPlan(human, target);
        int targets = plan == null ? 65 : Math.max(1, plan.targets().size());
        double sweep = sweepDamageMultiplier(human, targets);
        return new CreeperForecast(new Profile(current.range(), current.shape(), minimumDamage * sweep,
                current.angleDegrees(), current.cooldownTicks(), "", maximumUpswing, current.offhandAttack()),
                current.damageMultiplier() * sweep);
    }

    public static boolean applyDamageMultiplier(Human human, Profile profile, int targetCount,
                                                BooleanSupplier attack) {
        AttributeInstance damage = human.getAttribute(Attributes.ATTACK_DAMAGE);
        double comboMultiplier = profile.damageMultiplier();
        double sweepMultiplier = sweepDamageMultiplier(human, targetCount);
        if (damage == null || (Math.abs(comboMultiplier - 1.0D) < 1.0E-6D
                && Math.abs(sweepMultiplier - 1.0D) < 1.0E-6D)) {
            return attack.getAsBoolean();
        }
        damage.removeModifier(DAMAGE_MODIFIER_ID);
        damage.removeModifier(SWEEP_MODIFIER_ID);
        if (Math.abs(comboMultiplier - 1.0D) >= 1.0E-6D) {
            damage.addTransientModifier(new AttributeModifier(DAMAGE_MODIFIER_ID,
                    "Better Combat NPC combo multiplier", comboMultiplier - 1.0D,
                    AttributeModifier.Operation.MULTIPLY_BASE));
        }
        if (Math.abs(sweepMultiplier - 1.0D) >= 1.0E-6D) {
            damage.addTransientModifier(new AttributeModifier(SWEEP_MODIFIER_ID,
                    "Better Combat NPC sweeping multiplier", sweepMultiplier - 1.0D,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        try {
            return attack.getAsBoolean();
        } finally {
            damage.removeModifier(DAMAGE_MODIFIER_ID);
            damage.removeModifier(SWEEP_MODIFIER_ID);
        }
    }

    private static double sweepDamageMultiplier(Human human, int targetCount) {
        if (targetCount <= 1 || !booleanValue(field(getConfig(), "allow_reworked_sweeping"), true)) {
            return 1.0D;
        }
        int extraTargetLimit = Math.max(1, (int) number(field(getConfig(),
                "reworked_sweeping_extra_target_count"), 4.0D));
        double maxPenalty = Math.max(0.0D, number(field(getConfig(),
                "reworked_sweeping_maximum_damage_penalty"), 0.5D));
        double enchantRestore = Math.max(0.0D, number(field(getConfig(),
                "reworked_sweeping_enchant_restores"), 0.5D));
        int sweepingLevel = EnchantmentHelper.getItemEnchantmentLevel(
                Enchantments.SWEEPING_EDGE, NpcDualWield.attackWeapon(human));
        double perTargetPenalty = maxPenalty / extraTargetLimit
                * Math.min(extraTargetLimit, targetCount - 1);
        double enchantBonus = enchantRestore / Enchantments.SWEEPING_EDGE.getMaxLevel()
                * sweepingLevel;
        return Math.min(1.0D, Math.max(0.0D, 1.0D - perTargetPenalty + enchantBonus));
    }

    public static void completeAttack(Human human) {
        ComboState state = comboState(human);
        state.index++;
        state.lastAttackTick = human.tickCount;
        NpcDualWield.completed(human);
    }
    public static void clear(Human human) { COMBOS.remove(human); NpcDualWield.clear(human); }
    /** Verified API capability: unavailable or unknown is not one-handed. */
    public static boolean supportsOneHand(ItemStack stack) {
        if (!isConfiguredEnabled() || stack.isEmpty() || stack.getCount() != 1) return false;
        Object attributes = getWeaponAttributes(stack);
        Object twoHanded = call(attributes, "isTwoHanded");
        Object attacks = call(attributes, "attacks");
        return Boolean.FALSE.equals(twoHanded) && attacks != null && attacks.getClass().isArray()
                && Array.getLength(attacks) > 0 && field(getConfig(), "dual_wielding_attack_speed_multiplier") instanceof Number;
    }

    private static Profile profile(Human human) {
        double defaultRange = 3.0D;
        Shape shape = Shape.FORWARD_BOX;
        double damageMultiplier = 1.0D;
        double angleDegrees = 0.0D;
        int cooldown = attackCooldown(human);
        Object attributes = weaponAttributes(human);
        if (attributes == null) return new Profile(defaultRange, shape, damageMultiplier,
                angleDegrees, cooldown, "", 0.5F);

        boolean twoHanded = booleanValue(call(attributes, "isTwoHanded"), false);
        String animationName = resolveAnimationName(null, twoHanded);
        float animationUpswing = 0.5F;
        double configuredRange = number(call(attributes, "attackRange"), defaultRange);
        if (configuredRange > 0.0D) defaultRange = Math.min(configuredRange, 16.0D);
        Object attacks = call(attributes, "attacks");
        if (attacks != null && attacks.getClass().isArray() && Array.getLength(attacks) > 0) {
            Object selected = selectAttack(human, attributes, attacks);
            if (selected == null) return new Profile(defaultRange, shape, 0, angleDegrees, cooldown, "", .5F);
            if (selected != null) {
                animationName = resolveAnimationName(call(selected, "animation"), twoHanded);
                animationUpswing = (float) Math.max(0.05D, Math.min(0.95D,
                        number(call(selected, "upswing"), 0.5D)));
                Object hitbox = call(selected, "hitbox");
                try {
                    shape = Shape.valueOf(String.valueOf(hitbox));
                } catch (IllegalArgumentException ignored) {
                    shape = Shape.FORWARD_BOX;
                }
                damageMultiplier = Math.max(0.0D, Math.min(10.0D,
                        number(call(selected, "damageMultiplier"), 1.0D)));
                angleDegrees = Math.max(0.0D, Math.min(360.0D,
                        number(call(selected, "angle"), 0.0D)));
            }
        }
        if (NpcDualWield.active(human)) {
            String field = NpcDualWield.offhandTurn(human) ? "dual_wielding_off_hand_damage_multiplier"
                    : "dual_wielding_main_hand_damage_multiplier";
            damageMultiplier *= Math.max(0, number(field(getConfig(), field), 1));
        }
        return new Profile(defaultRange, shape, damageMultiplier, angleDegrees, cooldown,
                animationName, animationUpswing, NpcDualWield.offhandTurn(human));
    }

    static String resolveAnimationName(Object value, boolean twoHanded) {
        return value instanceof String name && !name.isBlank() ? name
                : twoHanded ? "bettercombat:two_handed_slash_horizontal_right"
                : "bettercombat:one_handed_slash_horizontal_right";
    }

    private static int attackCooldown(Human human) {
        // Human had no attack-speed attribute in its original attribute
        // supplier. Keep this adapter safe for old/custom entity suppliers;
        // new Human instances register the player-equivalent base value (4).
        AttributeInstance attackSpeed = human.getAttribute(Attributes.ATTACK_SPEED);
        double speed = attackSpeed == null ? 4.0D
                : NpcMeleeWeaponContext.attackSpeed(human, NpcDualWield.attackWeapon(human));
        double multiplier = EffectiveAttributes.attackSpeedMultiplier(human);
        Object config = getConfig();
        if (NpcDualWield.active(human)) multiplier *= Math.max(.01,
                number(field(config, "dual_wielding_attack_speed_multiplier"), 1));
        return MeleeJumpPolicy.cooldown(speed, multiplier, (int)number(field(config, "attack_interval_cap"), 2));
    }

    private static Object selectAttack(Human human, Object attributes, Object attacks) {
        ComboState state = comboState(human);
        double comboReset = Math.max(1.0D, number(field(getConfig(), "combo_reset_rate"), 2.0D));
        if (state.lastAttackTick > 0
                && human.tickCount - state.lastAttackTick > attackCooldown(human) * comboReset) {
            state.index = 0;
        }

        List<Object> eligible = new ArrayList<>();
        for (int i = 0; i < Array.getLength(attacks); i++) {
            Object candidate = Array.get(attacks, i);
            if (candidate != null && conditionsMatch(human, attributes, call(candidate, "conditions"))) {
                eligible.add(candidate);
            }
        }
        int index = NpcDualWield.active(human) ? state.index / 2 : state.index;
        return eligible.isEmpty() ? null : eligible.get(Math.floorMod(index, eligible.size()));
    }

    private static boolean conditionsMatch(Human human, Object mainAttributes, Object conditions) {
        if (conditions == null || !conditions.getClass().isArray() || Array.getLength(conditions) == 0) return true;
        ItemStack offhand = human.getOffhandItem();
        boolean dualWield = NpcDualWield.active(human);
        boolean offhandAttack = NpcDualWield.offhandTurn(human);
        Object offAttributes = getWeaponAttributes(offhandAttack ? human.getItemBySlot(EquipmentSlot.MAINHAND) : offhand);
        for (int i = 0; i < Array.getLength(conditions); i++) {
            Object condition = Array.get(conditions, i);
            String name = String.valueOf(condition);
            boolean accepted = switch (name) {
                case "NOT_DUAL_WIELDING" -> !dualWield;
                case "DUAL_WIELDING_ANY" -> dualWield;
                case "DUAL_WIELDING_SAME" -> dualWield
                        && ItemStack.isSameItem(human.getItemBySlot(EquipmentSlot.MAINHAND), offhand);
                case "DUAL_WIELDING_SAME_CATEGORY" -> dualWield
                        && nonEmptyString(call(mainAttributes, "category"))
                        && nonEmptyString(call(offAttributes, "category"))
                        && objectsEqual(call(mainAttributes, "category"), call(offAttributes, "category"));
                case "NO_OFFHAND_ITEM" -> offhand.isEmpty();
                case "OFF_HAND_SHIELD" -> offhand.getItem() instanceof ShieldItem
                        && !booleanValue(call(mainAttributes, "isTwoHanded"), false);
                case "MAIN_HAND_ONLY" -> !offhandAttack;
                case "OFF_HAND_ONLY" -> offhandAttack;
                case "MOUNTED" -> human.isPassenger();
                case "NOT_MOUNTED" -> !human.isPassenger();
                default -> false;
            };
            if (!accepted) return false;
        }
        return true;
    }

    private static boolean objectsEqual(Object first, Object second) {
        return first != null && first.equals(second);
    }

    private static boolean nonEmptyString(Object value) {
        return value instanceof String string && !string.isEmpty();
    }

    private static List<LivingEntity> collectTargets(Human human, Profile profile) {
        double reach = profile.range();
        double searchMultiplier = Math.max(1.0D, Math.min(8.0D,
                number(field(getConfig(), "target_search_range_multiplier"), 2.0D)));
        double searchRange = reach * searchMultiplier + 1.0D;
        AABB search = human.getBoundingBox().inflate(searchRange, searchRange, searchRange);
        return human.level().getEntitiesOfClass(LivingEntity.class, search,
                target -> target != human && human.canAttack(target)
                        && canBeHit(human, target, profile));
    }

    private static boolean canBeHit(Human human, LivingEntity target, Profile profile) {
        return canBeHit(human, target, profile, human.getViewVector(1.0F));
    }

    private static boolean canBeHit(Human human, LivingEntity target, Profile profile, Vec3 view) {
        if (!target.isAlive() || !human.canAttack(target)
                || (!booleanValue(field(getConfig(), "allow_attacking_thru_walls"), false)
                && !human.hasLineOfSight(target))) return false;
        if (target instanceof Human other && HumanRelations.allied(human, other)) return false;

        Vec3 origin = MeleeCombatRange.attackOrigin(human);
        Vec3 forward = view.normalize();
        Vec3 right = forward.cross(new Vec3(0.0D, 1.0D, 0.0D)).normalize();
        if (right.lengthSqr() < 1.0E-6D) right = new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 up = right.cross(forward).normalize();
        AABB box = target.getBoundingBox().inflate(target.getPickRadius());
        Vec3 center = box.getCenter();
        Vec3 delta = center.subtract(origin);
        double depth = delta.dot(forward);
        double lateral = delta.dot(right);
        double vertical = delta.dot(up);
        double hx = box.getXsize() * 0.5D;
        double hy = box.getYsize() * 0.5D;
        double hz = box.getZsize() * 0.5D;
        double depthRadius = Math.abs(forward.x) * hx + Math.abs(forward.y) * hy + Math.abs(forward.z) * hz;
        double sideRadius = Math.abs(right.x) * hx + Math.abs(right.y) * hy + Math.abs(right.z) * hz;
        double upRadius = Math.abs(up.x) * hx + Math.abs(up.y) * hy + Math.abs(up.z) * hz;
        double range = profile.range();

        Vec3 closestPoint = new Vec3(
                Math.max(box.minX, Math.min(box.maxX, origin.x)),
                Math.max(box.minY, Math.min(box.maxY, origin.y)),
                Math.max(box.minZ, Math.min(box.maxZ, origin.z)));
        Vec3 closestDelta = closestPoint.subtract(origin);
        if (closestDelta.lengthSqr() > range * range) {
            return false;
        }
        if (profile.angleDegrees() > 0.0D && profile.angleDegrees() < 360.0D
                && !insideAttackAngle(delta, forward, profile.angleDegrees())
                && !insideAttackAngle(closestDelta, forward, profile.angleDegrees())) return false;

        double sideHalf = switch (profile.shape()) {
            case FORWARD_BOX -> range * 0.25D;
            case VERTICAL_PLANE -> range / 6.0D;
            case HORIZONTAL_PLANE -> range;
        };
        double verticalHalf = switch (profile.shape()) {
            case FORWARD_BOX -> range * 0.25D;
            case VERTICAL_PLANE -> range;
            case HORIZONTAL_PLANE -> range / 6.0D;
        };
        boolean spinningPlane = profile.angleDegrees() > 180.0D
                && profile.shape() != Shape.FORWARD_BOX;
        double minDepth = spinningPlane ? -range - depthRadius : -depthRadius;
        double maxDepth = range + depthRadius;
        return depth >= minDepth && depth <= maxDepth
                && Math.abs(lateral) <= sideHalf + sideRadius
                && Math.abs(vertical) <= verticalHalf + upRadius;
    }

    private static boolean insideAttackAngle(Vec3 delta, Vec3 forward, double angleDegrees) {
        if (delta.lengthSqr() < 1.0E-12D) return true;
        double distance = Math.max(1.0E-6D, delta.length());
        double dot = Math.max(-1.0D, Math.min(1.0D, delta.dot(forward) / distance));
        return Math.toDegrees(Math.acos(dot)) <= angleDegrees * 0.5D;
    }

    private static Object getWeaponAttributes(ItemStack stack) { return readWeaponAttributes(stack).attributes(); }
    private static AttributesRead readWeaponAttributes(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return new AttributesRead(null, true);
        Api bridge = resolveApi();
        if (bridge == null) return new AttributesRead(null, false);
        try {
            return new AttributesRead(bridge.getAttributes().invoke(null, stack), true);
        } catch (ReflectiveOperationException | LinkageError error) {
            warnApiOnce(error);
            return new AttributesRead(null, false);
        }
    }
    private static void refreshAttributes(ComboState state, ItemStack weapon, int tick) {
        AttributesRead read = readWeaponAttributes(weapon);
        // A transient read cannot revoke a verified weapon or turn it into an instant native attack.
        if (read.available()) state.attributes = read.attributes();
        state.lookupAvailable = read.available();
        state.lookupFailures = read.available() ? 0 : Math.min(MAX_PROFILE_FAILURES, state.lookupFailures+1);
        state.attributesResolved = true; state.lastAttributeLookupTick = tick;
    }

    private static Object weaponAttributes(Human human) {
        ComboState state = comboState(human);
        ItemStack weapon = NpcDualWield.attackWeapon(human);
        synchronized (state) {
            // Alternating the attacking hand is not changing physical equipment or restarting a combo.
            if (state.primary != human.getItemBySlot(EquipmentSlot.MAINHAND) || state.offhand != human.getOffhandItem()) state.index = 0;
            if (!state.attributesResolved || state.weapon != weapon) {
                state.weapon = weapon;
                state.lookupFailures = 0;
                state.attributes = null; // Never carry another physical weapon's verified profile.
                refreshAttributes(state, weapon, human.tickCount);
            } else if (human.tickCount - state.lastAttributeLookupTick >=
                    (state.lookupAvailable || state.lookupFailures >= MAX_PROFILE_FAILURES ? PROFILE_REFRESH_TICKS : PROFILE_RETRY_TICKS)) {
                // The weapon registry can synchronize after this Human first
                // appears. A missing profile must not be cached forever.
                refreshAttributes(state, weapon, human.tickCount);
            }
            state.primary = human.getItemBySlot(EquipmentSlot.MAINHAND);
            state.offhand = human.getOffhandItem();
            return state.attributes;
        }
    }

    private static Object getConfig() {
        Api bridge = resolveApi();
        if (bridge == null) return null;
        Object cached = betterCombatConfig;
        if (cached != null) return cached;
        try {
            Object resolved = bridge.configField().get(null);
            if (resolved != null) betterCombatConfig = resolved;
            return resolved;
        } catch (ReflectiveOperationException | LinkageError error) {
            warnApiOnce(error);
            return null;
        }
    }

    private static Api resolveApi() {
        if (!ModList.get().isLoaded("bettercombat")) return null;
        if (!apiLookupAttempted) synchronized (BetterCombatMeleeCombat.class) {
            if (!apiLookupAttempted) {
                try {
                    ClassLoader loader = BetterCombatMeleeCombat.class.getClassLoader();
                    Class<?> registry = Class.forName("net.bettercombat.logic.WeaponRegistry", false, loader);
                    Method getAttributes = registry.getMethod("getAttributes", ItemStack.class);
                    Class<?> betterCombat = Class.forName("net.bettercombat.BetterCombat", false, loader);
                    Field config = betterCombat.getField("config");
                    api = new Api(getAttributes, config);
                } catch (ReflectiveOperationException | LinkageError error) {
                    warnApiOnce(error);
                } finally {
                    apiLookupAttempted = true;
                }
            }
        }
        return api;
    }

    private static Object call(Object instance, String methodName) {
        return CachedReflection.call(instance, methodName);
    }

    private static Object field(Object instance, String fieldName) {
        return CachedReflection.field(instance, fieldName);
    }

    private static double number(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private static boolean booleanValue(Object value, boolean fallback) {
        return value instanceof Boolean bool ? bool : fallback;
    }

    private static ComboState comboState(Human human) {
        synchronized (COMBOS) {
            return COMBOS.computeIfAbsent(human, ignored -> new ComboState());
        }
    }

    private static void warnApiOnce(Throwable error) {
        if (!warnedAboutApi) synchronized (BetterCombatMeleeCombat.class) {
            if (!warnedAboutApi) {
                LOGGER.warn("Better Combat NPC melee profile lookup failed; transient failures wait for retry, verified weapons retain BC authority; unsupported bridges or repeatedly unreadable new weapons use native fallback", error);
                warnedAboutApi = true;
            }
        }
    }
}
