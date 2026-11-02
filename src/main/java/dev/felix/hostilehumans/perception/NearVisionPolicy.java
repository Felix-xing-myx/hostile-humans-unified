package dev.felix.hostilehumans.perception;

/** Body-relative vertical cylinder with a horizontal sector, independent of eye pitch. */
public final class NearVisionPolicy {
    private NearVisionPolicy() {}
    public static boolean owns(double dx,double dy,double dz,double radius,double bottom,double top){
        return Double.isFinite(dx+dy+dz)&&radius>=0&&dy>=bottom&&dy<=top&&dx*dx+dz*dz<=radius*radius;
    }
    public static boolean inSector(double dx,double dz,double forwardX,double forwardZ,double halfAngle){
        double horizontal=dx*dx+dz*dz,heading=forwardX*forwardX+forwardZ*forwardZ;
        // Directly below/above the body's axis has no horizontal bearing or blind angle.
        if(horizontal<=1.e-8)return true;
        return heading>1.e-8&&(dx*forwardX+dz*forwardZ)/Math.sqrt(horizontal*heading)>=Math.cos(halfAngle)-1.e-10;
    }
}
