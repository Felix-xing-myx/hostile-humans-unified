package club.someoneice.humangunner;

import java.util.Locale;

/** Shared finite-ammunition defaults; a magazine pays for one reload cycle. */
public final class HumanAmmoPolicy {
    private HumanAmmoPolicy() {}

    public static int magazineCost(String type) {
        return switch (type == null ? "" : type.toLowerCase(Locale.ROOT)) {
            case "pistol", "smg" -> 1;
            case "rifle", "shotgun" -> 2;
            case "sniper" -> 3;
            case "mg" -> 4;
            case "rpg" -> 5;
            default -> 2;
        };
    }

    public static int arrowMinimum(int tier) {
        return switch (tier) { case 0 -> 32; case 1 -> 64; case 2 -> 128; default -> 192; };
    }
    public static int arrowMaximum(int tier) {
        return switch (tier) { case 0, 1 -> 128; default -> 192; };
    }
    public static int magazineMinimum(int tier) {
        return switch (tier) { case 0 -> 6; case 1 -> 12; case 2 -> 32; default -> 48; };
    }
    public static int magazineMaximum(int tier) {
        return switch (tier) { case 0 -> 12; case 1 -> 24; case 2 -> 48; default -> 72; };
    }
}
