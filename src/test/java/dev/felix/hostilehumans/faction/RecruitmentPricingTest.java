package dev.felix.hostilehumans.faction;

import dev.felix.hostilehumans.economy.MoneyMath;
import dev.felix.hostilehumans.economy.MoneyRate;

public final class RecruitmentPricingTest {
    public static void main(String[] args){
        int[] amounts={20,200,2000,20000};
        for(int tier=0;tier<4;tier++){
            if(RecruitmentPricing.baseAzureCopper(tier)!=amounts[tier])throw new AssertionError("tier base");
            for(var rate:new MoneyRate[]{new MoneyRate(3,4),new MoneyRate(1,1),new MoneyRate(7,5),new MoneyRate(9,5)}){
                long price=MoneyMath.quote(amounts[tier],1,rate,1,1,true);
                long numerator=(long)amounts[tier]*rate.denominator();
                if(price*rate.numerator()<numerator||(price-1)*rate.numerator()>=numerator)throw new AssertionError("round final currency only");
            }
        }
    }
}
