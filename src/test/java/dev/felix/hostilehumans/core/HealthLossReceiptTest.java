package dev.felix.hostilehumans.core;

/** Real receipt state transitions, including reentrant writes and multiple damage sources. */
public final class HealthLossReceiptTest {
    public static void main(String[] args) {
        var healed = new HealthLossReceipt(100);
        healed.beginWrite(100); close(healed.endWrite(60),40,"actual damage");
        healed.beginWrite(60); close(healed.endWrite(100),0,"totem/healing does not erase damage");
        close(healed.ownLoss(100),40,"restored health retains real loss");
        var clamped = new HealthLossReceipt(20);
        clamped.beginWrite(20); close(clamped.endWrite(0),20,"overkill counts only actual HP");
        var blocked = new HealthLossReceipt(100); close(blocked.ownLoss(100),0,"shield/absorption/cancelled hit has no loss");
        var recursive = new HealthLossReceipt(100);
        recursive.beginWrite(100); recursive.beginWrite(100);
        close(recursive.endWrite(90),10,"inner same-source write");
        close(recursive.endWrite(80),10,"outer write excludes inner write");
        close(recursive.ownLoss(80),20,"same-source writes not doubled");
        for(int depth=1;depth<=12;depth++) {
            HealthLossReceipt[] receipts = new HealthLossReceipt[depth];
            for(int i=0;i<depth;i++){receipts[i]=new HealthLossReceipt(1000);receipts[i].beginWrite(1000);}
            double health=1000, sum=0;
            for(int i=depth-1;i>=0;i--) {
                double damage=i+1; health-=damage;
                close(receipts[i].endWrite(health),damage,"nested source exclusive loss, depth "+depth+" source "+i);
                sum+=receipts[i].ownLoss(health);
                if(i>0)receipts[i-1].childDamage(receipts[i].subtreeLoss(health));
            }
            close(sum,1000-health,"all sources conserve actual loss");
            close(receipts[0].subtreeLoss(health),1000-health,"parent receives all grandchildren");
        }
        var beforeWrite = new HealthLossReceipt(100);
        beforeWrite.childDamage(10); beforeWrite.beginWrite(90); beforeWrite.endWrite(80);
        close(beforeWrite.ownLoss(80),10,"child hit before parent write not subtracted twice");
        var fallback=new HealthLossReceipt(100); fallback.childDamage(10);
        close(fallback.ownLoss(70),20,"unobserved net fallback excludes child damage");
        System.out.println("Health loss receipt checks passed");
    }
    private static void close(double actual,double expected,String message){if(Math.abs(actual-expected)>1e-9)throw new AssertionError(message+": "+actual+" != "+expected);}
}
