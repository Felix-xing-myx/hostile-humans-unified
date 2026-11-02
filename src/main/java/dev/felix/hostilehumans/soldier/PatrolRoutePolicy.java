package dev.felix.hostilehumans.soldier;

import java.util.HashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Bounded draft topology and leg geometry; no inventory, networking or entity mutations. */
public final class PatrolRoutePolicy {
    public static final int AWAY_TIMEOUT = 2400;
    public static final int MAX_WAIT = 1200;
    public static final double MAX_LEG = 96;
    public record Waypoint(BlockPos block,Vec3 feet) {
        public Waypoint {
            if(block==null||feet==null||!Double.isFinite(feet.x)||!Double.isFinite(feet.y)||!Double.isFinite(feet.z))
                throw new IllegalArgumentException("Invalid waypoint");
            block=block.immutable();
        }
    }
    private PatrolRoutePolicy() {}
    public static boolean mayEdit(boolean creative,boolean ownSoldier,boolean stationed){return stationed&&(creative||ownSoldier); }
    public static boolean closed(List<BlockPos> points) {
        int last = points.size() - 1;
        if (last < 3 || !points.get(0).equals(points.get(last))) return false;
        if (new HashSet<>(points.subList(0, last)).size() != last) return false;
        for(int i=0;i<last;i++) {
            Vec3 at=Vec3.atBottomCenterOf(points.get(i));
            Vec3 back=Vec3.atBottomCenterOf(points.get((i+last-1)%last)).subtract(at);
            Vec3 next=Vec3.atBottomCenterOf(points.get((i+1)%last)).subtract(at);
            if(back.cross(next).lengthSqr()<1.e-8&&back.dot(next)>0)return false;
        }
        // Crossing or touching non-neighbouring legs is not a simple loop (e.g. a loop with a stem).
        for (int i=0;i<last;i++) for (int j=i+2;j<last;j++) {
            if (i==0 && j==last-1) continue;
            if (intersects(points.get(i),points.get(i+1),points.get(j),points.get(j+1))) return false;
        }
        return true;
    }
    private static boolean intersects(BlockPos a,BlockPos b,BlockPos c,BlockPos d) {
        double rx=b.getX()-a.getX(), rz=b.getZ()-a.getZ(), sx=d.getX()-c.getX(), sz=d.getZ()-c.getZ();
        double cross=rx*sz-rz*sx, qx=c.getX()-a.getX(), qz=c.getZ()-a.getZ();
        if (Math.abs(cross)<1.e-8) {
            if (Math.abs(qx*rz-qz*rx)>1.e-8) return false;
            return on(a,b,c)||on(a,b,d)||on(c,d,a)||on(c,d,b);
        }
        double t=(qx*sz-qz*sx)/cross, u=(qx*rz-qz*rx)/cross;
        return t>=0&&t<=1&&u>=0&&u<=1
                && Math.abs(a.getY()+t*(b.getY()-a.getY())-c.getY()-u*(d.getY()-c.getY()))<=.75;
    }
    private static boolean on(BlockPos a,BlockPos b,BlockPos p) {
        double length=a.distSqr(b);
        if(length<1.e-8)return a.equals(p);
        Vec3 start=Vec3.atBottomCenterOf(a), end=Vec3.atBottomCenterOf(b), point=Vec3.atBottomCenterOf(p);
        double t=point.subtract(start).dot(end.subtract(start))/length;
        return t>=0&&t<=1&&start.lerp(end,t).distanceToSqr(point)<.01;
    }
    public static boolean inLeg(Vec3 start,Vec3 end,Vec3 point,double radius) {
        double dx=end.x-start.x,dz=end.z-start.z, square=dx*dx+dz*dz;
        double t=square<1.e-8?0:Math.max(0,Math.min(1,((point.x-start.x)*dx+(point.z-start.z)*dz)/square));
        double x=point.x-start.x-t*dx,z=point.z-start.z-t*dz;
        return Double.isFinite(x)&&Double.isFinite(z)&&x*x+z*z<=radius*radius;
    }
}
