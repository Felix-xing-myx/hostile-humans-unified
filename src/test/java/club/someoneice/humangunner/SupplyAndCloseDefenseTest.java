package club.someoneice.humangunner;

import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Survival reserves under repeated ammo pickups and attack completion across restoration paths. */
public final class SupplyAndCloseDefenseTest {
    private static int checks;
    private static final SupplyInventoryPolicy.Slot AMMO = slot(SupplyInventoryPolicy.Kind.OTHER, 180, 24);
    private static SupplyInventoryPolicy.Slot slot(SupplyInventoryPolicy.Kind kind, double value, int count) {
        return new SupplyInventoryPolicy.Slot(kind, value, count, false);
    }
    public static void main(String[] args) throws Exception {
        var resupply = CombatResupplyConfig.parse(new com.google.gson.JsonObject());
        check(resupply.enabled() && resupply.arrowLow() == 8 && resupply.arrowTarget() == 32
                && resupply.reloads() == 2 && resupply.radius() == 2 && resupply.intervalTicks() == 20,
                "default emergency supply bounds");
        check(CombatResupplyConfig.ammunitionNeeded(false, true, true, 8, 8, 32), "arrow shortage starts at eight");
        check(!CombatResupplyConfig.ammunitionNeeded(false, true, true, 9, 8, 32), "nine arrows does not start a cycle");
        check(CombatResupplyConfig.ammunitionNeeded(true, true, true, 31, 8, 32)
                && !CombatResupplyConfig.ammunitionNeeded(true, true, true, 32, 8, 32), "resupply has a distinct stop threshold");
        check(!CombatResupplyConfig.ammunitionNeeded(true, false, true, 0, 8, 32)
                && !CombatResupplyConfig.ammunitionNeeded(true, true, false, 0, 8, 32),
                "losing weapon or disabling consumption cancels ammo collection");
        check(!CombatResupplyConfig.ammunitionNeeded(false, true, true, 4, 3, 8)
                && CombatResupplyConfig.ammunitionNeeded(false, true, true, 3, 3, 8),
                "four-cost gun starts below four magazines");
        var reversed = com.google.gson.JsonParser.parseString("{\"arrow_low\":40,\"arrow_target\":10}").getAsJsonObject();
        check(CombatResupplyConfig.parse(reversed).arrowTarget() == 41, "reversed refill target is normalized");
        var fixedEmpty = new SupplyInventoryPolicy.Slot(SupplyInventoryPolicy.Kind.EMPTY, 0, 0, true);
        var unavailable = new SupplyInventoryPolicy(List.of(fixedEmpty));
        check(unavailable.availableEmptySlots(slot(SupplyInventoryPolicy.Kind.FOOD, 50, 1)) == 0,
                "hand-custody empty slot is not available storage");
        var armor = ArmorSwapConfig.parse(new com.google.gson.JsonObject());
        check(armor.cooldownTicks() == 200, "armor slot cooldown is ten seconds");
        check(!armor.shouldReplace(false, 80, .90, 80, 1, false), "slight wear never rotates identical armor");
        check(!armor.shouldReplace(false, 80, .50, 80, 1, false), "half durability is not a replacement trigger");
        check(armor.shouldReplace(false, 80, .25, 80, .50, false), "low armor accepts a 25-point condition improvement");
        check(!armor.shouldReplace(false, 80, .25, 80, .49, false), "tiny durability improvements are ignored");
        check(!armor.shouldReplace(false, 80, .25, 79, 1, false), "non-emergency wear does not downgrade protection");
        check(armor.shouldReplace(false, 80, 1, 88, .50, false), "ten-percent full-quality upgrade at fifty-percent durability");
        check(!armor.shouldReplace(false, 80, 1, 87, 1, false), "small full-quality upgrades do not rotate equipment");
        check(!armor.shouldReplace(false, 80, 1, 160, .49, false), "powerful but worn candidate waits");
        check(!armor.shouldReplace(false, 80, 1, 160, 1, true), "ordinary upgrade respects cooldown");
        check(armor.shouldReplace(false, 80, .10, 64, .50, true), "emergency permits bounded downgrade during cooldown");
        check(!armor.shouldReplace(false, 80, .10, 63, 1, false), "emergency rejects a large protection downgrade");
        check(!armor.shouldReplace(false, 80, .10, 80, .10, true), "emergency never swaps equally worn armor");
        check(armor.shouldReplace(true, 0, 0, 80, .50, true), "empty slots bypass cooldown");
        check(!armor.shouldReplace(false, 80, 1, Double.NaN, 1, false), "invalid armor value is rejected");
        var limits = com.google.gson.JsonParser.parseString("{\"replace_below_durability_percent\":5,\"emergency_below_durability_percent\":50}").getAsJsonObject();
        check(ArmorSwapConfig.parse(limits).emergencyCondition() == .05, "emergency threshold cannot exceed low-durability threshold");
        var food = slot(SupplyInventoryPolicy.Kind.FOOD, 55, 64);
        var empty = slot(SupplyInventoryPolicy.Kind.EMPTY, 0, 0);
        List<SupplyInventoryPolicy.Slot> inventory = new ArrayList<>(List.of(food, food, food,
                slot(SupplyInventoryPolicy.Kind.FOOD, 90, 1),
                slot(SupplyInventoryPolicy.Kind.MEDICINE, 55, 1),
                slot(SupplyInventoryPolicy.Kind.MELEE, 80, 1),
                slot(SupplyInventoryPolicy.Kind.HEAD, 70, 1),
                slot(SupplyInventoryPolicy.Kind.CHEST, 90, 1),
                slot(SupplyInventoryPolicy.Kind.LEGS, 75, 1),
                slot(SupplyInventoryPolicy.Kind.FEET, 60, 1)));
        while (inventory.size() < 30) inventory.add(slot(SupplyInventoryPolicy.Kind.OTHER, 20, 1));
        // Repeated incoming ammo can replace expendable stacks, but never the retained kit.
        for (int pickup = 0; pickup < 40; pickup++) {
            var policy = new SupplyInventoryPolicy(inventory);
            for (int index = 0; index < inventory.size(); index++) {
                if (policy.canReplace(index, AMMO)) { inventory.set(index, AMMO); break; }
            }
        }
        for (int i : List.of(0, 1, 2, 4, 5, 6, 7, 8, 9)) check(inventory.get(i) != AMMO, "retained reserve " + i);
        check(inventory.get(3) == AMMO, "small extra food stack is expendable");
        var policy = new SupplyInventoryPolicy(inventory);
        check(!policy.canReplace(0, slot(SupplyInventoryPolicy.Kind.FOOD, 90, 1)), "one fine meal cannot discard a large reserve");
        check(policy.canReplace(0, slot(SupplyInventoryPolicy.Kind.FOOD, 90, 64)), "large better food reserve can upgrade");
        var extraFood = new SupplyInventoryPolicy(List.of(food, food, food,
                slot(SupplyInventoryPolicy.Kind.FOOD, 90, 1)));
        check(extraFood.canReplace(3, food), "unreserved food also compares full stack value");
        check(!new SupplyInventoryPolicy(List.of(food, food, food, food))
                .canReplace(3, slot(SupplyInventoryPolicy.Kind.FOOD, 90, 1)),
                "one premium meal cannot evict an extra full stack either");
        check(extraFood.replacementCost(3, food) == 90
                && extraFood.replacementCost(0, food) == 55 * 64,
                "food eviction chooses the least valuable whole food stack");
        check(ShieldQualityPolicy.fullValue(2000, 0, false)
                > ShieldQualityPolicy.fullValue(336, 10, false),
                "maximum shield durability outweighs modest enchantment advantages");
        check(ShieldQualityPolicy.fullValue(4000, 0, false)
                > ShieldQualityPolicy.fullValue(2000, 0, false),
                "high durability shields retain distinct values without the old cap");
        check(ShieldQualityPolicy.fullValue(336, 1, false)
                - ShieldQualityPolicy.fullValue(336, 0, false) == 3,
                "shield enchantment weight is counted once and reduced to three");
        check(new SupplyInventoryPolicy(List.of(slot(SupplyInventoryPolicy.Kind.FOOD, 150, 1)))
                .canReplace(0, food), "large ordinary stack can upgrade a tiny high-value food reserve");
        check(policy.canReplace(4, slot(SupplyInventoryPolicy.Kind.MEDICINE, 108, 1)), "last medicine can upgrade to medicine");
        check(policy.canReplace(5, slot(SupplyInventoryPolicy.Kind.MELEE, 95, 1)), "backup melee can upgrade within category");
        check(!policy.canReplace(6, slot(SupplyInventoryPolicy.Kind.CHEST, 190, 1)), "chest cannot replace last backup helmet");

        var sparse = new SupplyInventoryPolicy(List.of(food, empty, empty, empty));
        check(sparse.availableEmptySlots(AMMO) == 1, "two missing food slots remain empty");
        check(sparse.availableEmptySlots(food) == 3, "food may fill all empty slots");
        var lastSpaces = new SupplyInventoryPolicy(List.of(empty, empty, empty));
        check(lastSpaces.availableEmptySlots(AMMO) == 0, "no ammo into last three empty slots");
        var oldAllAmmo = new SupplyInventoryPolicy(List.of(AMMO, AMMO, AMMO));
        check(oldAllAmmo.canReplace(0, food), "food can recover space from an old ammo-filled inventory");
        var fixed = new SupplyInventoryPolicy(List.of(new SupplyInventoryPolicy.Slot(SupplyInventoryPolicy.Kind.OTHER, 10, 1, true)));
        check(!fixed.canReplace(0, food), "bound gear and custody items remain protected");
        check(SupplyInventoryPolicy.ammoValue(true, true, true) > SupplyInventoryPolicy.ammoValue(false, true, true), "gunner with backup bow prefers magazines");
        check(SupplyInventoryPolicy.ammoValue(false, false, true) > SupplyInventoryPolicy.ammoValue(true, false, true), "bow/crossbow prefers arrows");
        check(SupplyInventoryPolicy.ammoValue(false, false, false) == SupplyInventoryPolicy.ammoValue(true, false, false), "melee ammo priorities equal");

        Object sword = new Object();
        var hold = new CloseDefenseHold.State(sword, 100);
        check(hold.holds(sword, 101) && hold.holds(sword, 139), "initial two-second hold");
        check(!hold.holds(sword, 140), "idle hold expires");
        hold.attack(130, 62);
        check(hold.holds(sword, 191) && !hold.holds(sword, 192), "long attack completes after initial hold");
        hold.attack(185, 8);
        check(hold.holds(sword, 192) && !hold.holds(sword, 193), "later counter extends completion deadline");
        check(!hold.holds(new Object(), 131), "actual weapon replacement releases old hold");
        check(hold.holds(sword, 200, true) && !hold.holds(sword, 200, false), "slowed vanilla swing must finish even past deadline");
        for (String owner : List.of("GunCustody", "RangedWeaponCustody")) {
            check(calls(owner, owner.equals("GunCustody") ? "restorePrimaryGun" : "restorePreferred", "CloseDefenseHold", "shouldHold"), "all automatic restore paths guarded " + owner);
            check(calls(owner, owner.equals("GunCustody") ? "beginCloseMeleeCounter" : "beginCloseMelee", "CloseDefenseHold", "begin"), "start hold after swap " + owner);
            check(calls(owner, "prepareForManualInventory", "CloseDefenseHold", "clear"), "manual owner action releases hold " + owner);
        }
        check(calls("HumanLootManager", "lowestEvictableSlot", "SupplyInventoryPolicy", "isReserved"), "offhand custody cannot discard survival reserves");
        check(calls("HumanLootManager", "lowestMandatoryMeleeEvictionSlot", "SupplyInventoryPolicy", "isReserved"), "mandatory backup provisioning retains reserves");
        check(calls("AmmoWeaponSelection", "tick", "CloseDefenseHold", "shouldHold"), "resupply promotion respects ongoing attack");
        check(calls("HumanLootManager", "canStoreOrImprove", "HumanLootManager$StorageSummary", "canStore"), "search shares reservation policy");
        check(calls("HumanLootManager", "storeWithEviction", "SupplyInventoryPolicy", "canReplace"), "commit enforces protected eviction");
        System.out.println("SupplyAndCloseDefenseTest: " + checks + " checks passed");
    }
    private static boolean calls(String owner, String method, String targetOwner, String name) throws Exception {
        String prefix = "club/someoneice/humangunner/";
        ClassNode node = new ClassNode();
        try (var stream = SupplyAndCloseDefenseTest.class.getClassLoader().getResourceAsStream(prefix + owner + ".class")) {
            if (stream == null) throw new AssertionError("Missing compiled " + owner);
            new ClassReader(stream).accept(node, 0);
        }
        for (var entry : node.methods) if (entry.name.equals(method)) for (var instruction : entry.instructions)
            if (instruction instanceof MethodInsnNode call && call.owner.equals(prefix + targetOwner) && call.name.equals(name)) return true;
        return false;
    }
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
