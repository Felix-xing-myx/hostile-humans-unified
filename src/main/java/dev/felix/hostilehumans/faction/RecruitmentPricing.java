package dev.felix.hostilehumans.faction;

/** Full price in Azure copper, converted once by the authoritative economy rate, then rounded up. */
public final class RecruitmentPricing {
    private RecruitmentPricing(){}
    public static int baseAzureCopper(int tier){return switch(tier){case 0->20;case 1->200;case 2->2000;case 3->20000;default->throw new IllegalArgumentException("recruitment tier");};}
}
