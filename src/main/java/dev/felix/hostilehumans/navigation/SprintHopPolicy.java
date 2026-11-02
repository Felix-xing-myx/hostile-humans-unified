package dev.felix.hostilehumans.navigation;

/** Flat, aligned runway for optional speed hops. Never a path smoothing or corner-cutting rule. */
public final class SprintHopPolicy {
    private static final double MIN_DOT=.94;
    private SprintHopPolicy() {}
    public static boolean clearRunway(double x,double y,double z,double vx,double vz,double[][] nodes) {
        return clearRunway(x,y,z,vx,vz,nodes,12);
    }
    public static boolean clearRunway(double x,double y,double z,double vx,double vz,double[][] nodes,double flightTicks) {
        double speed=Math.hypot(vx,vz);
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||!Double.isFinite(speed)
                ||speed<.04||nodes==null||nodes.length==0||nodes.length>8||!Double.isFinite(flightTicks)||flightTicks<1)return false;
        double required=Math.max(2,speed*flightTicks+.75);
        if(required>6)return false; // Our bounded horizon cannot prove a longer high-speed/boosted hop.
        double previousX=x,previousY=y,previousZ=z,headingX=vx/speed,headingZ=vz/speed,travelled=0;
        for(double[] node:nodes){
            if(node==null||node.length<3||!Double.isFinite(node[0])||!Double.isFinite(node[1])
                    ||!Double.isFinite(node[2])||Math.abs(node[1]-y)>.6||Math.abs(node[1]-previousY)>.6)return false;
            double dx=node[0]-previousX,dz=node[2]-previousZ,length=Math.hypot(dx,dz);
            previousX=node[0];previousY=node[1];previousZ=node[2];
            if(length<.15)continue;
            if((dx*headingX+dz*headingZ)/length<MIN_DOT)return false;
            travelled+=length;
            if(travelled>=required)return true;
            headingX=dx/length;headingZ=dz/length;
        }
        return false; // Do not leap past a nearby route end or an uninspected horizon.
    }
}
