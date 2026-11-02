package dev.felix.hostilehumans.cultivation;

/**
 * Investor-owned stock and fractional credit. Reserved credit cannot be withdrawn or spent twice.
 */
public record TrainingCredit(long stock, long available, long reserved) {
    public TrainingCredit {
        if (stock < 0 || available < 0 || reserved < 0)
            throw new IllegalArgumentException("Negative training resources");
    }

    public TrainingCredit deposit(long count) {
        if (count <= 0) throw new IllegalArgumentException();
        return new TrainingCredit(Math.addExact(stock, count), available, reserved);
    }

    public TrainingCredit withdraw(long count) {
        if (count <= 0 || count > stock)
            throw new IllegalStateException("Insufficient unlocked items");
        return new TrainingCredit(stock - count, available, reserved);
    }

    public TrainingCredit reserve(long cost) {
        if (cost <= 0) throw new IllegalArgumentException();
        long needed = Math.max(0, cost - available),
                items = (needed + CultivationRules.UNIT - 1) / CultivationRules.UNIT;
        if (items > stock) throw new IllegalStateException("Training supplies required");
        return new TrainingCredit(
                stock - items,
                Math.addExact(available, Math.multiplyExact(items, CultivationRules.UNIT)) - cost,
                Math.addExact(reserved, cost));
    }

    public TrainingCredit settle(long cost) {
        if (cost <= 0 || cost > reserved) throw new IllegalStateException("Invalid reservation");
        return new TrainingCredit(stock, available, reserved - cost);
    }

    public TrainingCredit refund(long cost) {
        if (cost <= 0 || cost > reserved) throw new IllegalStateException("Invalid reservation");
        return new TrainingCredit(stock, Math.addExact(available, cost), reserved - cost);
    }
}
