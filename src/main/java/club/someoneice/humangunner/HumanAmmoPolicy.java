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
        return switch (tier) { case 0 -> 32; case 1 -> 48; case 2 -> 72; default -> 96; };
    }
    public static int arrowMaximum(int tier) {
        return switch (tier) { case 0, 1 -> 64; case 2 -> 96; default -> 128; };
    }
    public static int magazineMinimum(int tier) {
        return switch (tier) { case 0 -> 4; case 1 -> 8; case 2 -> 16; default -> 24; };
    }
    public static int magazineMaximum(int tier) {
        return switch (tier) { case 0 -> 8; case 1 -> 14; case 2 -> 24; default -> 36; };
    }
}
