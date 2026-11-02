package dev.felix.hostilehumans.economy;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/** Validated immutable table revision. Test values never become a formal economy implicitly. */
public record EconomyPolicy(
        long revision,
        boolean enabled,
        Map<Currency, MoneyRate> coefficients,
        long dailyExchangeAzure,
        long depositBaseAzure,
        long contributionValuePerStanding,
        List<Offer> offers,
        List<Task> tasks,
        String reason) {
    public enum Category {
        FOOD,
        POTION,
        AMMO,
        MATERIAL,
        EQUIPMENT
    }

    public enum TaskKind {
        INITIATION,
        ATONEMENT_ONE,
        ATONEMENT_TWO,
        BASIC,
        DAILY,
        BOUNTY,
        SOLDIER
    }

    public enum Objective {
        DELIVERY,
        KILL_ROAMER,
        KILL_FACTION,
        KILL_BOSS
    }

    public record Offer(
            String id,
            ResourceLocation item,
            Category category,
            long baseAzure,
            boolean buy,
            boolean sell) {}

    public record Task(
            String id,
            String title,
            TaskKind kind,
            Objective objective,
            ResourceLocation item,
            String targetFaction,
            int count,
            int minimumStanding,
            int standingReward,
            long copperReward,
            long timeLimit,
            boolean once) {
        public Task {
            if (item == null) item = new ResourceLocation("minecraft:air");
        }
    }

    public EconomyPolicy {
        coefficients = Map.copyOf(coefficients);
        offers = List.copyOf(offers);
        tasks = List.copyOf(tasks);
        if (offers.size() > 64 || tasks.size() > 64)
            throw new IllegalArgumentException("economy table row limit");
    }

    public static EconomyPolicy disabled() {
        return new EconomyPolicy(
                0,
                false,
                Map.of(),
                0,
                0,
                0,
                List.of(),
                List.of(),
                "Formal prices/rewards not configured. Explicit creative test only.");
    }
}
