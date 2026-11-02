package dev.felix.hostilehumans.behavior.combat.ranged;

import dev.felix.hostilehumans.behavior.combat.gun.GunCustody;
import dev.felix.hostilehumans.behavior.combat.gun.GunSupport;
import dev.felix.hostilehumans.inventory.HumanEquipmentExchange;
import dev.felix.hostilehumans.inventory.HumanInventorySession;

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

        Path source = Path.of(System.getProperty("hhu.projectDir", ".")).resolve("src/main/java/dev/felix/hostilehumans");
        String selection = Files.readString(dev.felix.hostilehumans.testutil.ModuleTestSources.source("AmmoWeaponSelection.java"));
        check(selection.contains("GunSupport.get().isReloading(human)"),
                "paid in-progress reload survives a now-empty magazine inventory");
        check(selection.contains("HumanInventorySession.isEditing(human)"),
                "manual inventory session prevents AI hand exchanges");
        check(selection.contains("HumanEquipmentExchange.stow(human, hand)"),
                "exhausted equipment uses the shared real-layout transaction");
        String exchange = Files.readString(dev.felix.hostilehumans.testutil.ModuleTestSources.source("HumanEquipmentExchange.java"));
        String stow = exchange.substring(exchange.indexOf("public static Result stow("), exchange.indexOf("private static void addHand("));
        check(stow.contains("return Result.NO_CAPACITY;") && stow.contains("return writeLayout(")
                        && !stow.contains("addFreshEntity") && !stow.contains("setItemSlot("),
                "full backpack returns without clearing the hand or creating a world copy");
        String gun = Files.readString(dev.felix.hostilehumans.testutil.ModuleTestSources.source("GunCustody.java"));
        String ranged = Files.readString(dev.felix.hostilehumans.testutil.ModuleTestSources.source("RangedWeaponCustody.java"));
        check(!gun.contains("ItemStack.of(") && !ranged.contains("ItemStack.of("),
                "stale custody snapshots cannot reconstruct weapons");
        check(gun.contains("AmmoWeaponSelection.tick(human)"),
                "the existing every-tick custody entry also runs without TaCZ");
        check(ranged.contains("return AmmoWeaponSelection.gunHasPriority(human)"),
                "stored exhausted guns do not suppress supplied bows");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
