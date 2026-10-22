package club.someoneice.humangunner;

/** Standalone JDK-only checks for the actual service's authorization policy. */
public final class AttributeDetailPolicyTest {
    public static void main(String[] args) {
        check(allowed(true,true,true,2304,true,true,false,false,false),"formal owner boundary accepted");
        check(!allowed(true,true,true,2304.01,true,true,false,true,false),"outside 48 rejected");
        check(!allowed(true,true,false,1,true,true,false,true,false),"other dimension rejected");
        check(!allowed(true,false,true,1,true,true,false,true,false),"dead target rejected");
        check(!allowed(false,true,true,1,true,true,false,true,false),"dead viewer rejected");
        check(!allowed(true,true,true,1,false,true,false,true,false),"other owner rejected");
        check(allowed(true,true,true,1,false,false,false,true,true),"wild observer accepted");
        check(!allowed(true,true,true,1,false,false,false,false,true),"wild without tool rejected");
        check(!allowed(true,true,true,1,false,false,true,true,true),"temporary reinforcements rejected as wild");
        check(allowed(true,true,true,1,true,true,false,true,true),"observer accepts own soldier");
        check(!allowed(true,true,true,1,false,true,false,true,true),"observer rejects another employer soldier");
        check(!allowed(true,true,true,1,true,true,false,false,true),"observer own soldier still requires tool");
        check(!allowed(true,true,true,Double.NaN,true,true,false,true,false),"NaN distance rejected");
        check(!allowed(true,true,true,Double.POSITIVE_INFINITY,true,true,false,true,false),"infinite distance rejected");
        check(!AttributeDetailPolicy.expired(160,100)&&AttributeDetailPolicy.expired(161,100),"heartbeat expiration boundary");
        check(AttributeDetailPolicy.expired(99,100),"clock rollback ends session");
        check(AttributeDetailPolicy.nextSequence(5,6),"new open accepted");
        check(!AttributeDetailPolicy.nextSequence(5,5)&&!AttributeDetailPolicy.nextSequence(5,4),"replayed open rejected");
        check(!AttributeDetailPolicy.nextSequence(0,-1),"invalid sequence rejected");
        System.out.println("AttributeDetailPolicyTest: all checks passed");
    }
    private static boolean allowed(boolean va,boolean ta,boolean same,double distance,boolean owner,boolean any,boolean temp,boolean tool,boolean wild) {
        return AttributeDetailPolicy.allowed(va,ta,same,distance,owner,any,temp,tool,wild);
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
