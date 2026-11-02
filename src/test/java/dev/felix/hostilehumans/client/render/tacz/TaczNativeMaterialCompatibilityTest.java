package dev.felix.hostilehumans.client.render.tacz;

/** Real dependency loading and capability invocation, no Minecraft instance or graphics context. */
public final class TaczNativeMaterialCompatibilityTest {
    public static void main(String[] args) throws Exception {
        Class<?> displayType=Class.forName("com.tacz.guns.client.resource.pojo.display.gun.GunDisplay");
        Object display=displayType.getConstructor().newInstance();
        Class<?> adapter=Class.forName("dev.felix.hostilehumans.client.render.tacz.TaczPresentationResources");
        var material=adapter.getDeclaredMethod("transparent",displayType);material.setAccessible(true);
        try {
            var flag=displayType.getDeclaredField("enableTransparency");flag.setAccessible(true);
            flag.setBoolean(display,true);
            if(!Boolean.TRUE.equals(material.invoke(null,display)))throw new AssertionError("Newer native transparency capability must remain effective");
            flag.setBoolean(display,false);
            if(!Boolean.FALSE.equals(material.invoke(null,display)))throw new AssertionError("Disabled transparency retains cutout rendering");
            System.out.println("TaczNativeMaterialCompatibilityTest: actual newer capability true/false passed");
        } catch(NoSuchFieldException older) {
            if(!Boolean.FALSE.equals(material.invoke(null,display)))throw new AssertionError("Older native material remains cutout without a missing-method call");
            System.out.println("TaczNativeMaterialCompatibilityTest: actual older GunDisplay safely retains native cutout material");
        }
    }
}
