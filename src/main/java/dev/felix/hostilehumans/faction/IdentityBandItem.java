package dev.felix.hostilehumans.faction;

import net.minecraft.world.item.Item;

/** Immutable item identity only; selection and relationship arithmetic belong to their own modules. */
public final class IdentityBandItem extends Item {
    private final String faction;
    private final int grade;
    public IdentityBandItem(String faction, int grade, Properties properties) {
        super(properties.stacksTo(1));
        if (FactionDefinitions.id(faction) == null || grade < 1 || grade > 4)
            throw new IllegalArgumentException("Invalid identity band");
        this.faction = FactionDefinitions.id(faction).toString();
        this.grade = grade;
    }
    public String faction() { return faction; }
    public int grade() { return grade; }
}
