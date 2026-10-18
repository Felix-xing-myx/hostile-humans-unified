package club.someoneice.humangunner;

/** Pure authorization/time guards shared by the server service and regression tests. */
public final class AttributeDetailPolicy {
    private AttributeDetailPolicy() { }
    public static boolean allowed(boolean viewerAlive, boolean targetAlive, boolean sameDimension, double distanceSquared,
                                  boolean formalOwner, boolean anyOwner, boolean temporary, boolean holdsTool, boolean wildTool) {
        if(!viewerAlive||!targetAlive||!sameDimension||!Double.isFinite(distanceSquared)||distanceSquared<0||distanceSquared>48*48)return false;
        return wildTool ? !anyOwner&&!temporary&&holdsTool : formalOwner;
    }
    public static boolean expired(long now,long heartbeat) { return now<heartbeat||now-heartbeat>60; }
    public static boolean nextSequence(long previous,long requested) { return requested>0&&requested>previous; }
}
