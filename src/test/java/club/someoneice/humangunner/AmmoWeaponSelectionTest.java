package club.someoneice.humangunner;

import java.nio.file.Files;
import java.nio.file.Path;

/** Finite-ammunition priority and custody wiring; requires no live level. */
public final class AmmoWeaponSelectionTest {
    public static void main(String[] args) throws Exception {
        int loadedGun = AmmoWeaponSelection.rank(true, true, false, false, false);
        int exhaustedGun = AmmoWeaponSelection.rank(true, false, false, true, false);
        int bow = AmmoWeaponSelection.rank(false, false, true, true, false);
        int exhaustedBow = AmmoWeaponSelection.rank(false, false, true, false, true);
        int melee = AmmoWeaponSelection.rank(false, false, false, false, true);
        int empty = AmmoWeaponSelection.rank(false, false, false, false, false);
        check(loadedGun > bow && bow > melee && melee > empty,
                "loaded/payable gun, supplied bow and existing melee retain their priority");
        check(exhaustedGun == empty && exhaustedBow == empty,
                "exhausted ranged weapons cannot win selection even when also classified as melee");

        Path source = Path.of(System.getProperty("hhu.projectDir", ".")).resolve("src/main/java/club/someoneice/humangunner");
        String selection = Files.readString(source.resolve("AmmoWeaponSelection.java"));
        check(selection.contains("GunSupport.get().isReloading(human)"),
                "paid in-progress reload survives a now-empty magazine inventory");
        check(selection.contains("HumanInventorySession.isEditing(human)"),
                "manual inventory session prevents AI hand exchanges");
        check(selection.contains("if (human.level().addFreshEntity(dropped))"),
                "full backpack clears the hand only after preserving the world item");
        String gun = Files.readString(source.resolve("GunCustody.java"));
        String ranged = Files.readString(source.resolve("RangedWeaponCustody.java"));
        check(!gun.contains("ItemStack.of(") && !ranged.contains("ItemStack.of("),
                "stale custody snapshots cannot reconstruct weapons");
        check(gun.contains("AmmoWeaponSelection.tick(human)"),
                "the existing every-tick custody entry also runs without TaCZ");
        check(ranged.contains("return AmmoWeaponSelection.hasUsableGun(human)"),
                "stored exhausted guns do not suppress supplied bows");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
