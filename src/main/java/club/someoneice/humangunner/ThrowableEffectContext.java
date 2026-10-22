package club.someoneice.humangunner;

import net.minecraft.world.entity.Entity;

/** Only the exact native static gas call inherits its grenade origin; scopes always unwind. */
public final class ThrowableEffectContext {
    private static final ThreadLocal<Entity> CURRENT = new ThreadLocal<>();
    private ThrowableEffectContext() { }
    public static Entity source() {
        Entity source = CURRENT.get();
        return ThrowableSource.recordedHuman(source) ? source : null;
    }
    public static boolean protects(Entity candidate) {
        Entity source = source();
        return source != null && HumanProjectileRelations.passesThroughEntity(source, candidate);
    }
    public static void run(Entity source, Runnable operation) { call(source, () -> { operation.run(); return null; }); }
    public static <T> T call(Entity source, java.util.function.Supplier<T> operation) {
        Entity previous = CURRENT.get();
        Entity admitted = ThrowableSource.recordedHuman(source) ? source : null;
        if (admitted == null) CURRENT.remove(); else CURRENT.set(admitted);
        try { return operation.get(); }
        finally { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
    }
}
