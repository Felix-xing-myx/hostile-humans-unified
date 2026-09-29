package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
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

/** Optional server-side adapter for Better Combat's weapon profiles and melee hitboxes. */
public final class BetterCombatMeleeCombat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final java.util.UUID DAMAGE_MODIFIER_ID = java.util.UUID.fromString(
            "9b8bc786-6829-4ac0-9b88-e5260f39e422");
    private static final java.util.UUID SWEEP_MODIFIER_ID = java.util.UUID.fromString(
            "67c45dc7-170d-4c66-9b57-09ff2c9e8c4f");
    private static final Map<Human, ComboState> COMBOS = new WeakHashMap<>();
    private static volatile Api api;
    private static volatile boolean apiLookupAttempted;
    private static volatile boolean warnedAboutApi;
    private static volatile boolean betterCombatSettingResolved;
    private static volatile boolean betterCombatSettingEnabled;
    private static volatile Object betterCombatConfig;

    private BetterCombatMeleeCombat() {}

    public record Profile(double range, Shape shape, double damageMultiplier, double angleDegrees,
                          int cooldownTicks, String animationName, float animationUpswing) {}
    public enum Shape { FORWARD_BOX, VERTICAL_PLANE, HORIZONTAL_PLANE }
    public record AttackPlan(Profile profile, List<LivingEntity> targets) {}

    private static final class ComboState {
        private ItemStack weapon = ItemStack.EMPTY;
        private Object attributes;
        private boolean attributesResolved;
        private int lastAttributeLookupTick;
        private int index;
        private int lastAttackTick;
    }

    private record Api(Method getAttributes, Field configField) {}

    public static boolean isEnabled(Human human) {
        return human != null && !human.level().isClientSide
                && isConfiguredEnabled();
    }

    public static boolean isConfiguredEnabled() {
        if (!betterCombatSettingResolved) synchronized (BetterCombatMeleeCombat.class) {
            if (!betterCombatSettingResolved) {
                betterCombatSettingEnabled = UnifiedConfig.get().betterCombatSoldierMeleeEnabled()
                        && ModList.get().isLoaded("bettercombat");
                betterCombatSettingResolved = true;
            }
        }
        return betterCombatSettingEnabled;
    }

    /** Gets the effective Better Combat reach, or zero when its optional path is inactive. */
    public static double attackRange(Human human) {
        if (!isEnabled(human)) return 0.0D;
        Object attributes = weaponAttributes(human);
        double range = number(call(attributes, "attackRange"), 3.0D);
        return Double.isFinite(range) ? Math.min(16.0D, Math.max(0.1D, range)) : 3.0D;
    }

    public static int cooldownTicks(Human human) {
        if (!isEnabled(human)) return 0;
        return profile(human).cooldownTicks();
    }

    /** Resolve the active combo step and collect the hostile entities in its oriented hitbox. */
    public static AttackPlan createAttackPlan(Human human, LivingEntity requestedTarget) {
        if (!isEnabled(human) || requestedTarget == null) return null;
        Profile profile = profile(human);
        List<LivingEntity> targets = collectTargets(human, profile);
        if (!targets.contains(requestedTarget) && canBeHit(human, requestedTarget, profile)) {
            targets.add(requestedTarget);
        }
        targets.sort(Comparator.comparingDouble(human::distanceToSqr));
        return new AttackPlan(profile, List.copyOf(targets));
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
                Enchantments.SWEEPING_EDGE, human.getMainHandItem());
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

        String animationName = "";
        float animationUpswing = 0.5F;
        double configuredRange = number(call(attributes, "attackRange"), defaultRange);
        if (configuredRange > 0.0D) defaultRange = Math.min(configuredRange, 16.0D);
        Object attacks = call(attributes, "attacks");
        if (attacks != null && attacks.getClass().isArray() && Array.getLength(attacks) > 0) {
            Object selected = selectAttack(human, attributes, attacks);
            if (selected != null) {
                Object animation = call(selected, "animation");
                if (animation instanceof String name) animationName = name;
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
        return new Profile(defaultRange, shape, damageMultiplier, angleDegrees, cooldown,
                animationName, animationUpswing);
    }

    private static int attackCooldown(Human human) {
        // Human had no attack-speed attribute in its original attribute
        // supplier. Keep this adapter safe for old/custom entity suppliers;
        // new Human instances register the player-equivalent base value (4).
        AttributeInstance attackSpeed = human.getAttribute(Attributes.ATTACK_SPEED);
        double speed = Math.max(0.1D, attackSpeed == null ? 4.0D : attackSpeed.getValue());
        int cooldown = (int) Math.ceil(20.0D / speed);
        Object config = getConfig();
        Object intervalCap = field(config, "attack_interval_cap");
        return Math.max(Math.max(1, (int) number(intervalCap, 2.0D)), cooldown);
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
        return eligible.isEmpty() ? null : eligible.get(Math.floorMod(state.index, eligible.size()));
    }

    private static boolean conditionsMatch(Human human, Object mainAttributes, Object conditions) {
        if (conditions == null || !conditions.getClass().isArray()) return true;
        ItemStack offhand = human.getOffhandItem();
        Object offAttributes = getWeaponAttributes(offhand);
        boolean dualWield = mainAttributes != null && offAttributes != null
                && !booleanValue(call(mainAttributes, "isTwoHanded"), false)
                && !booleanValue(call(offAttributes, "isTwoHanded"), false);
        for (int i = 0; i < Array.getLength(conditions); i++) {
            Object condition = Array.get(conditions, i);
            String name = String.valueOf(condition);
            boolean accepted = switch (name) {
                case "NOT_DUAL_WIELDING" -> !dualWield;
                case "DUAL_WIELDING_ANY" -> dualWield;
                case "DUAL_WIELDING_SAME" -> dualWield
                        && ItemStack.isSameItem(human.getMainHandItem(), offhand);
                case "DUAL_WIELDING_SAME_CATEGORY" -> dualWield
                        && nonEmptyString(call(mainAttributes, "category"))
                        && nonEmptyString(call(offAttributes, "category"))
                        && objectsEqual(call(mainAttributes, "category"), call(offAttributes, "category"));
                case "NO_OFFHAND_ITEM" -> offhand.isEmpty();
                case "OFF_HAND_SHIELD" -> offhand.getItem() instanceof ShieldItem
                        && !booleanValue(call(mainAttributes, "isTwoHanded"), false);
                case "MAIN_HAND_ONLY" -> true;
                case "OFF_HAND_ONLY" -> false;
                case "MOUNTED" -> human.isPassenger();
                case "NOT_MOUNTED" -> !human.isPassenger();
                default -> true;
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
        if (!target.isAlive() || !human.canAttack(target)
                || (!booleanValue(field(getConfig(), "allow_attacking_thru_walls"), false)
                && !human.hasLineOfSight(target))) return false;
        if (target instanceof Human other && HumanRelations.allied(human, other)) return false;

        Vec3 origin = human.position().add(0.0D, human.getBbHeight() * 0.85D, 0.0D);
        Vec3 forward = human.getViewVector(1.0F).normalize();
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
        double distance = Math.max(1.0E-6D, delta.length());
        double dot = Math.max(-1.0D, Math.min(1.0D, delta.dot(forward) / distance));
        return Math.toDegrees(Math.acos(dot)) <= angleDegrees * 0.5D;
    }

    private static Object getWeaponAttributes(ItemStack stack) {
        Api bridge = resolveApi();
        if (bridge == null || stack == null || stack.isEmpty()) return null;
        try {
            return bridge.getAttributes().invoke(null, stack);
        } catch (ReflectiveOperationException | LinkageError error) {
            warnApiOnce(error);
            return null;
        }
    }

    private static Object weaponAttributes(Human human) {
        ComboState state = comboState(human);
        ItemStack weapon = human.getMainHandItem();
        synchronized (state) {
            if (!state.attributesResolved || !ItemStack.isSameItemSameTags(state.weapon, weapon)) {
                state.weapon = weapon.copy();
                state.index = 0;
                state.attributes = getWeaponAttributes(weapon);
                state.attributesResolved = true;
                state.lastAttributeLookupTick = human.tickCount;
            } else if (state.attributes == null
                    && human.tickCount - state.lastAttributeLookupTick >= 20) {
                // The weapon registry can synchronize after this Human first
                // appears. A missing profile must not be cached forever.
                state.attributes = getWeaponAttributes(weapon);
                state.lastAttributeLookupTick = human.tickCount;
            }
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
        if (instance == null) return null;
        try {
            return instance.getClass().getMethod(methodName).invoke(instance);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static Object field(Object instance, String fieldName) {
        if (instance == null) return null;
        try {
            return instance.getClass().getField(fieldName).get(instance);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
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
                LOGGER.warn("Better Combat NPC melee bridge could not access the optional Better Combat API; using conservative built-in hitbox defaults", error);
                warnedAboutApi = true;
            }
        }
    }
}
