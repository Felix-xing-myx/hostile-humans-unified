package dev.felix.hostilehumans.client.render.tacz;

import java.lang.reflect.Array;
import java.net.URLClassLoader;
import java.nio.file.Path;

/**
 * Executes the real installed TaCZ spline API and nested math library with deterministic authored
 * frames.
 */
public final class NativeRecoilApiContractTest {
    private static void near(double a, double b) {
        if (Math.abs(a - b) > 1e-5) throw new AssertionError(a + " != " + b);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2)
            throw new AssertionError("TaCZ jar and its commons-math3 jar required");
        try (var loader =
                new URLClassLoader(
                        new java.net.URL[] {
                            Path.of(args[0]).toUri().toURL(), Path.of(args[1]).toUri().toURL()
                        },
                        ClassLoader.getPlatformClassLoader())) {
            Class<?> type =
                    Class.forName("com.tacz.guns.resource.pojo.data.gun.GunRecoil", true, loader);
            Class<?> frame =
                    Class.forName(
                            "com.tacz.guns.resource.pojo.data.gun.GunRecoilKeyFrame", true, loader);
            Object recoil = type.getConstructor().newInstance();
            Object frames = Array.newInstance(frame, 2);
            for (int i = 0; i < 2; i++) {
                Object f = frame.getConstructor().newInstance();
                frame.getMethod("setTime", float.class).invoke(f, i * .1F);
                frame.getMethod("setValue", float[].class)
                        .invoke(f, (Object) new float[] {i + 1, i + 1});
                Array.set(frames, i, f);
            }
            type.getMethod("setPitch", frames.getClass()).invoke(recoil, frames);
            type.getMethod("setYaw", frames.getClass()).invoke(recoil, frames);
            for (String method : new String[] {"genPitchSplineFunction", "genYawSplineFunction"}) {
                var curve = NativeRecoilCurve.create(recoil, method, .5F);
                near(curve.applyAsDouble(0), 0);
                // Native prepends origin and shifts authored seconds to milliseconds + 30ms.
                near(curve.applyAsDouble(30), .5);
                near(curve.applyAsDouble(130), 1);
                near(curve.applyAsDouble(1000), 1);
                near(curve.applyAsDouble(-100), 0);
            }
            Object empty = type.getConstructor().newInstance();
            near(
                    NativeRecoilCurve.create(empty, "genPitchSplineFunction", 1).applyAsDouble(100),
                    0);
        }
        System.out.println("Actual TaCZ native recoil API and nested math passed");
    }
}
