package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import dev.felix.hostilehumans.core.ActionCoordinator;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;

/** One route belongs to its original admitted execution, even between Goal callbacks. */
public final class HumanNavigationOwnership {
    public interface OwnedNavigation { HumanNavigationOwnership movementOwnership(); }
    private record Context(Human human, ActionCoordinator.Token token) { }
    private static final ThreadLocal<Context> CONTINUATION = new ThreadLocal<>();
    private final dev.felix.hostilehumans.core.RouteExecutionLease<Path, ActionCoordinator.Token> route = new dev.felix.hostilehumans.core.RouteExecutionLease<>();

    /** Called only for a real install, never for createPath/search trials. */
    public boolean install(Human human, Supplier<Path> current, BooleanSupplier install, Runnable discard) {
        boolean gated = HumanGoalExecutionBridge.bound(human);
        return route.install(gated ? writeToken(human) : null, gated,
                token -> HumanGoalExecutionBridge.ownsBaseMove(human, token), current, install, discard);
    }
    public boolean mayStop(Human human, Path actual) {
        return route.mayStop(actual, currentToken(human), HumanGoalExecutionBridge.bound(human),
                token -> HumanGoalExecutionBridge.ownsBaseMove(human, token));
    }
    public void stopped() { route.stopped(); }

    /** Native navigator tick/repair inherits only this exact currently live route, never a guessed owner. */
    public boolean runInstalled(Human human, Path actual, Runnable execution) {
        if (!HumanGoalExecutionBridge.bound(human)) { execution.run(); return true; }
        if (!route.owns(actual, token -> HumanGoalExecutionBridge.ownsBaseMove(human, token))) return false;
        if (HumanGoalExecutionBridge.insideCallback(human) && !route.owner().equals(writeToken(human))) return false;
        Context previous = CONTINUATION.get();
        CONTINUATION.set(new Context(human, route.owner()));
        try { execution.run(); return true; }
        finally { if (previous == null) CONTINUATION.remove(); else CONTINUATION.set(previous); }
    }
    /** Native recomputePath writes its field directly instead of calling moveTo. Preserve only its exact source. */
    public void recomputed(Human human, Path before, Path after) {
        route.recomputed(before, after, writeToken(human), token -> HumanGoalExecutionBridge.ownsBaseMove(human, token));
    }

    public static boolean continueInstalled(Human human, PathNavigation navigation, Runnable execution) {
        if (!HumanGoalExecutionBridge.bound(human)) { execution.run(); return true; }
        return navigation instanceof OwnedNavigation owned
                && owned.movementOwnership().runInstalled(human, navigation.getPath(), execution);
    }
    /** A delayed local input keeps the exact token captured by its real requester. */
    public static boolean continueExecution(Human human, ActionCoordinator.Token token, Runnable execution) {
        if (!HumanGoalExecutionBridge.bound(human)) { execution.run(); return true; }
        if (!HumanGoalExecutionBridge.ownsBaseMove(human, token)) return false;
        if (HumanGoalExecutionBridge.insideCallback(human) && !token.equals(writeToken(human))) return false;
        Context previous = CONTINUATION.get();
        CONTINUATION.set(new Context(human, token));
        try { execution.run(); return true; }
        finally { if (previous == null) CONTINUATION.remove(); else CONTINUATION.set(previous); }
    }
    /** Read an existing context, including stop cleanup; does not acquire permissions. */
    public static ActionCoordinator.Token currentToken(Human human) {
        if (HumanGoalExecutionBridge.insideCallback(human)) return HumanGoalExecutionBridge.currentExecutionToken(human);
        Context context = CONTINUATION.get();
        return context != null && context.human() == human ? context.token() : null;
    }
    /** A non-MOVE Goal cannot borrow the route being continued by its caller. */
    public static ActionCoordinator.Token writeToken(Human human) {
        if (HumanGoalExecutionBridge.insideCallback(human)) return HumanGoalExecutionBridge.admitMovementWrite(human);
        ActionCoordinator.Token token = currentToken(human);
        return HumanGoalExecutionBridge.ownsBaseMove(human, token) ? token : null;
    }
}
