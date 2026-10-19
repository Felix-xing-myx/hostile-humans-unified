package club.someoneice.humangunner;

/** Dependency-free arithmetic and scoped-receipt regressions; run with Java 17 directly. */
public final class DamagePartitionTest {
    public static void main(String[] args) {
        close(DamagePartition.fraction(.25, 1), .25, "default linear conversion");
        close(DamagePartition.fraction(1, 1), .45, "cap");
        close(DamagePartition.fraction(Double.NaN, 1), 0, "nonfinite input");
        close(DamagePartition.fraction(.2, 2), .04, "configurable exponent");
        for (double ratio : new double[]{0,.001,.15,.45,1}) {
            close(DamagePartition.armor(100, ratio, 1, true, value -> value),100,"conserves input");
            close(DamagePartition.armor(100, ratio, 1, true, value -> value*.4),100*ratio+40*(1-ratio),"only N is armored");
            close(DamagePartition.armor(100, ratio, .9, true, value -> value),100*ratio+90*(1-ratio),"only N receives passive shield");
        }
        // Nonlinear armor proves before/after is a real setting, not a renamed multiplier.
        close(DamagePartition.armor(100,.2,.9,true,x->x*x/100),71.84,"passive before armor");
        close(DamagePartition.armor(100,.2,.9,false,x->x*x/100),77.6,"passive after armor");
        double remainder = DamagePartition.afterBlockFraction(100,.25,75);
        close(remainder,1,"full N block leaves all P");
        close(DamagePartition.armor(25,remainder,.9,true,x->0),25,"shield remainder does not get converted twice");
        close(DamagePartition.afterBlockFraction(100,.25,30),25.0/70,"partial foreign block preserves P");
        close(DamagePartition.afterBlockFraction(100,0,100),0,"fully blocked hit");
        var receipt = new DamagePipelineState();
        Object first = new Object(), second = new Object();
        receipt.begin(); receipt.record(first,10,1);
        close(receipt.consume(first,10,.25),1,"same source receipt");
        close(receipt.consume(first,10,.25),.25,"one consumption only");
        receipt.record(first,10,1); close(receipt.consume(second,10,.25),.25,"source identity isolation");
        receipt.record(first,10,1); close(receipt.consume(first,11,.25),.25,"tick expiry");
        receipt.record(first,10,1); receipt.begin();
        close(receipt.consume(first,10,.25),.25,"nested/new hurt invalidates outer residue");
        receipt.record(first,10,1); receipt.clear();
        close(receipt.consume(first,10,.25),.25,"cancel/return cleanup");
        receipt.record(first,10,1); // Simulate an exception before the armor seam, then a new hit.
        receipt.begin(); close(receipt.consume(first,10,.25),.25,"exception cannot leak into next hit");
        System.out.println("DamagePartitionTest passed");
    }
    private static void close(double actual, double expected, String label) {
        if (Math.abs(actual-expected)>1e-4) throw new AssertionError(label+": "+actual+" != "+expected);
    }
}
