package dev.felix.hostilehumans.navigation;

import dev.felix.hostilehumans.progression.DefenseAttributePolicy;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import dev.felix.hostilehumans.navigation.SoftSpeedLimit;

/** Cap intrinsic/equipment/action speed first; status-effect modifiers compose afterwards. */
final class MovementSpeedLimitPolicy {
    private MovementSpeedLimitPolicy() {}
    static double project(double base, List<DefenseAttributePolicy.Modifier> modifiers,
                          Set<UUID> effects, UUID ceiling) {
        var intrinsic = modifiers.stream().filter(m -> !m.id().equals(ceiling) && !effects.contains(m.id())).toList();
        var potions = modifiers.stream().filter(m -> !m.id().equals(ceiling) && effects.contains(m.id())).toList();
        double capped = SoftSpeedLimit.apply(DefenseAttributePolicy.rawWithout(base, intrinsic, null));
        return DefenseAttributePolicy.rawWithout(capped, potions, null);
    }
}
