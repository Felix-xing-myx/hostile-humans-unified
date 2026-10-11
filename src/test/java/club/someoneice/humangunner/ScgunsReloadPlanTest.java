package club.someoneice.humangunner;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipFile;

/** Real upstream animation fixtures, no optional class loading or client required. */
public final class ScgunsReloadPlanTest {
    public static void main(String[] args) throws Exception {
        try (var jar = new ZipFile(args[0])) {
            JsonObject dual = animations(jar, "rat_king_and_queen");
            var normal = ScgunsReloadPlan.create(dual, "", false, 20, 1, 20, 20);
            check(normal.size() == 1 && normal.get(0).ticks() == 56, "dual guns wait for full native animation");
            check(normal.get(0).rounds() == 20, "one magazine completion");
            var quick = ScgunsReloadPlan.create(dual, "", false, 20, 1, 10, 20);
            check(quick.get(0).ticks() == 28, "native duration modifier speeds up rather than slows down");
            var partial = ScgunsReloadPlan.create(dual, "", false, 4, 1, 20, 20);
            check(partial.get(0).ticks() == 56 && partial.get(0).rounds() == 4, "top-off still plays entire magazine operation");
            JsonObject arc = animations(jar, "arc_worker");
            var manual = ScgunsReloadPlan.create(arc, "", true, 10, 1, 30, 30);
            check(manual.size() == 12, "manual preparation ten inserts and finish");
            check(manual.get(0).rounds() == 0 && manual.get(11).rounds() == 0, "no free rounds at start/end");
            check(manual.stream().mapToInt(ScgunsReloadPlan.Step::rounds).sum() == 10, "inserts equal actual deficit");
            var batch = ScgunsReloadPlan.create(arc, "", true, 5, 2, 30, 30);
            check(batch.size() == 5 && batch.get(3).rounds() == 1, "reloadAmount and partial final insertion");
            var fallback = ScgunsReloadPlan.create(null, "", false, 7, 1, 40, 20);
            check(fallback.get(0).ticks() == 40, "unknown animation falls back to native adjusted duration");
            check(ScgunsReloadPlan.create(dual, "", false, 0, 1, 20, 20).isEmpty(), "full gun cannot start paid reload");
        }
        System.out.println("ScgunsReloadPlanTest: native magazine/manual/quick-reload and fallback checks passed");
    }
    private static JsonObject animations(ZipFile jar, String name) throws Exception {
        var entry = jar.getEntry("assets/scguns/animations/item/" + name + ".animation.json");
        try (var stream = jar.getInputStream(entry)) {
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("animations");
        }
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
