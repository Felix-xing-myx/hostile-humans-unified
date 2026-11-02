package dev.felix.hostilehumans.equipment;

public final class FoodInventoryValueTest {
    private static void near(double a,double b) { if (Math.abs(a-b)>1e-8) throw new AssertionError(a+" != "+b); }
    public static void main(String[] args) {
        near(FoodInventoryValue.quality(100,100),90);
        near(FoodInventoryValue.value(90,1),90);
        near(FoodInventoryValue.value(90,16),106.875);
        near(FoodInventoryValue.value(90,64),109.6875);
        near(FoodInventoryValue.value(90,100000),109.6875);
        near(FoodInventoryValue.value(160,64),195);
        near(FoodInventoryValue.value(260,0),0);
        if (!(FoodInventoryValue.value(90,64)<FoodInventoryValue.value(160,1)
                && FoodInventoryValue.value(160,64)<FoodInventoryValue.value(260,1)))
            throw new AssertionError("quantity must not invert food quality tiers");
        System.out.println("Food inventory quality boundaries passed");
    }
}
