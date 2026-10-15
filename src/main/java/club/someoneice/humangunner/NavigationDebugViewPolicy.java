package club.someoneice.humangunner;

import java.util.Set;
import java.util.List;
import java.util.UUID;

/** Pure display policy, independent of Minecraft client classes and all server commands. */
final class NavigationDebugViewPolicy {
    private static final Set<String> MEMORY = Set.of("memory","target","targets","recognition","queue");
    private static final Set<String> VISION = Set.of("vision","los");
    private static final Set<String> CLUES = Set.of("sound","beacon","clues");
    private static final Set<String> ACTIONS = Set.of("action","actions","navigation","command");
    private static final Set<String> BUDGETS = Set.of("budget","budgets");
    private NavigationDebugViewPolicy() {}
    static boolean pageContains(int page, String group) {
        return switch(page) {
            case 0 -> !MEMORY.contains(group) && !VISION.contains(group) && !CLUES.contains(group)
                    && !ACTIONS.contains(group) && !BUDGETS.contains(group);
            case 1 -> MEMORY.contains(group);
            case 2 -> VISION.contains(group);
            case 3 -> CLUES.contains(group);
            case 4 -> ACTIONS.contains(group);
            case 5 -> BUDGETS.contains(group);
            default -> false;
        };
    }
    static boolean layerVisible(int scope, boolean diagnosticFocus) {
        return scope == 2 || scope == 1 && diagnosticFocus;
    }
    static UUID resolveFocus(UUID serverFocus, UUID localFocus, List<UUID> visible) {
        if (localFocus != null && visible.contains(localFocus)) return localFocus;
        if (serverFocus != null && visible.contains(serverFocus)) return serverFocus;
        return visible.isEmpty() ? null : visible.get(0);
    }
    static UUID retainLocalFocus(UUID previousServer, UUID nextServer, UUID localFocus) {
        return java.util.Objects.equals(previousServer,nextServer) ? localFocus : null;
    }
    static boolean compactVision(int scope, boolean focus) { return scope == 1 && focus; }
    static boolean detailedVision(int scope) { return scope == 2; }
}
