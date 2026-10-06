package club.someoneice.humangunner;

/** One actual held stack binding across combat-goal restarts; never resets firing/reload clocks. */
final class GunDrawContinuity {
    private Object weapon;
    private String gunId;

    boolean ensure(Object currentWeapon, String currentId, boolean operatorBound,
                   java.util.function.BooleanSupplier draw) {
        boolean needed = !operatorBound || (weapon != null
                && (weapon != currentWeapon || !currentId.equals(gunId)));
        if (needed && !draw.getAsBoolean()) return true;
        drawn(currentWeapon, currentId);
        return needed;
    }

    void drawn(Object currentWeapon, String currentId) {
        weapon = currentWeapon;
        gunId = currentId;
    }
}
