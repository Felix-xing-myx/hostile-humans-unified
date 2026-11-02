package dev.felix.hostilehumans.damage;

import java.util.function.DoubleUnaryOperator;

/** Pure arithmetic: conversion conserves input, and armor sees only the normal portion. */
public final class DamagePartition {
    private DamagePartition() { }
    public static double fraction(double value, double exponent) {
        if (!Double.isFinite(value) || value <= 0) return 0;
        double linear = Math.min(.45, value);
        return Math.min(.45, Math.pow(linear, Double.isFinite(exponent) && exponent > 0 ? exponent : 1));
    }
    public static double afterBlockFraction(double incoming, double fraction, double blocked) {
        double remaining = incoming - blocked;
        return remaining > 0 ? Math.min(1, incoming * fraction / remaining) : 0;
    }
    public static float armor(float incoming, double fraction, double passiveMultiplier,
                              boolean passiveBeforeArmor, DoubleUnaryOperator nativeArmor) {
        double piercing = incoming * Math.max(0, Math.min(1, fraction));
        double normal = incoming - piercing;
        if (passiveBeforeArmor) normal *= passiveMultiplier;
        if (normal > 0) normal = nativeArmor.applyAsDouble(normal);
        if (!passiveBeforeArmor) normal *= passiveMultiplier;
        return (float) (piercing + normal);
    }
}
