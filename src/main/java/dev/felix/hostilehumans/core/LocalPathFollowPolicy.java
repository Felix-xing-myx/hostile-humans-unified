package dev.felix.hostilehumans.core;

/** Bounded continuous corridor samples; the caller proves body, floor and command safety. */
public final class LocalPathFollowPolicy {
    @FunctionalInterface public interface Sample { boolean safe(double x,double z,double stepX,double stepZ); }
    private LocalPathFollowPolicy() {}
    public static boolean corridor(double x,double z,double toX,double toZ,Sample sample){
        double dx=toX-x,dz=toZ-z,length=Math.hypot(dx,dz);
        if(!Double.isFinite(x)||!Double.isFinite(z)||!Double.isFinite(toX)||!Double.isFinite(toZ)
                ||!Double.isFinite(length)||length<.25||length>3||sample==null)return false;
        int steps=(int)Math.ceil(length/.25);
        double sx=dx/steps,sz=dz/steps;
        for(int i=0;i<=steps;i++)if(!sample.safe(x+sx*i,z+sz*i,i<steps?sx:0,i<steps?sz:0))return false;
        return true;
    }
}
