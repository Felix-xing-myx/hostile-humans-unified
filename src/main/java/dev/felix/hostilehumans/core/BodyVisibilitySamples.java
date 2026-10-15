package dev.felix.hostilehumans.core;

import java.util.LinkedHashSet;
import java.util.List;

/** Logical body coverage, including face centers, edge centers and corners. No model or side dependency. */
public final class BodyVisibilitySamples {
    public static final int MAX_POINTS = 27;
    public record Point(double x,double y,double z) {}
    private BodyVisibilitySamples() {}
    public static List<Point> of(double minX,double minY,double minZ,double maxX,double maxY,double maxZ) {
        if (!Double.isFinite(minX+minY+minZ+maxX+maxY+maxZ)
                ||maxX<minX||maxY<minY||maxZ<minZ)return List.of();
        var points=new LinkedHashSet<Point>();
        double cx=(minX+maxX)*.5,cy=(minY+maxY)*.5,cz=(minZ+maxZ)*.5;
        // Try torso and upper/lower body first, then lateral faces, edges and corners.
        points.add(new Point(cx,cy,cz));
        points.add(new Point(cx,maxY,cz));points.add(new Point(cx,minY,cz));
        for(double y:new double[]{cy,maxY,minY})
            for(double x:new double[]{cx,minX,maxX})
                for(double z:new double[]{cz,minZ,maxZ})points.add(new Point(x,y,z));
        return List.copyOf(points);
    }
}
