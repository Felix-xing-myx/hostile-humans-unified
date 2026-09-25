package club.someoneice.humangunner;

import java.nio.file.*;
import java.util.List;

/** Real JVM linking test on Forge/Minecraft libraries, with all gameplay dependencies absent.
 * No launcher, registry bootstrap, client, world, server or network is started. */
public final class OptionalRuntimeTest {
    public static void main(String[] args) throws Exception {
        ClassLoader loader = OptionalRuntimeTest.class.getClassLoader();
        for (String type : List.of("com.tacz.guns.api.item.IGun",
                "com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid",
                "top.theillusivec4.curios.api.CuriosApi")) {
            try { Class.forName(type, false, loader); throw new AssertionError("Optional dependency leaked onto test classpath: " + type); }
            catch (ClassNotFoundException expected) { }
        }
        int loaded = 0;
        Path root = Path.of(args[0]);
        try (var paths = Files.walk(root)) {
            for (Path file : paths.filter(p -> p.toString().endsWith(".class")).sorted().toList()) {
                String name = root.relativize(file).toString().replace('\\', '.').replace('/', '.').replaceAll("\\.class$", "");
                // These adapters are deliberately loadable only with their dependency installed.
                if (name.matches(".*\\.(GunnerGoal|TaczIntegration|TaczReloadSoundClient|TaczMaidIntegration|TouhouMaidCompat|TravelersBackpack|HumanBackpackLayer|CuriosBadgeAccess)(\\$.*)?")) continue;
                Class<?> type = Class.forName(name, false, loader);
                type.getDeclaredFields();
                type.getDeclaredMethods();
                type.getDeclaredConstructors();
                loaded++;
            }
        }
        GunSupport vanilla = new GunSupport() {};
        if (vanilla.enabled() || vanilla.isGun(null) || !vanilla.catalog().isEmpty() || vanilla.isBullet(null))
            throw new AssertionError("Vanilla adapter must not activate firearms");
        vanilla.control(null).aim(false);
        vanilla.control(null).draw();
        vanilla.control(null).clearSprintLock();
        vanilla.control(null).shoot(null, 0, 0);
        vanilla.registerEvents();
        System.out.println("OptionalRuntimeTest: linked " + loaded + " core classes without TaCZ, Curios or Maid; vanilla adapter passed");
    }
}
