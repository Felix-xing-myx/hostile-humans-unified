package dev.felix.hostilehumans.client.render.tacz;

/** Loads the optional dependency only in this explicitly provisioned test JVM. */
public final class NativeAnimationWeightTest {
    private static void near(float a,float b) { if(Math.abs(a-b)>1e-5) throw new AssertionError(a+" != "+b); }
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void main(String[] args) throws Exception {
        Class<? extends Enum> channel=Class.forName("com.tacz.guns.api.client.animation.ObjectAnimationChannel$ChannelType").asSubclass(Enum.class);
        var apply=NativeAnimationWeight.class.getDeclaredMethod("apply",float[].class,channel,float.class,float[].class);
        var math=Class.forName("com.tacz.guns.util.math.MathUtil");
        Object translation=Enum.valueOf(channel,"TRANSLATION"), scale=Enum.valueOf(channel,"SCALE"), rotation=Enum.valueOf(channel,"ROTATION");
        float[] out=new float[3];
        apply.invoke(null,new float[]{4,2,-6},translation,.25F,out);
        near(out[0],1);near(out[1],.5F);near(out[2],-1.5F);
        apply.invoke(null,new float[]{2,.5F,1},scale,.5F,out);
        near(out[0],1.5F);near(out[1],.75F);near(out[2],1);
        float[] quaternion=(float[])math.getMethod("toQuaternion",float.class,float.class,float.class).invoke(null,.2F,-.4F,.6F);
        float[] euler=(float[])math.getMethod("toEulerAngles",float[].class).invoke(null,(Object)quaternion);
        apply.invoke(null,quaternion,rotation,.5F,out);
        for(int i=0;i<3;i++)near(out[i],euler[i]*.5F);
        apply.invoke(null,new float[]{0,0,0,1},rotation,.5F,out);
        for(float v:out)near(v,0);
        System.out.println("NativeAnimationWeightTest passed");
    }
}
