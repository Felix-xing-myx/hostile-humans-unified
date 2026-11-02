package dev.felix.hostilehumans.faction;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraftforge.client.model.SeparateTransformsModel;

/** Reads the shipped resource graph through vanilla/Forge parsers; no game launch or graphics context. */
public final class FactionIdentityModelTest {
    private static final String MODELS = "/assets/hostile_humans_unified/models/item/";
    private static final JsonDeserializationContext CONTEXT = new JsonDeserializationContext() {
        @Override @SuppressWarnings("unchecked")
        public <T> T deserialize(JsonElement json, Type type) throws JsonParseException {
            if (type != BlockModel.class) throw new JsonParseException("Unexpected embedded model type: " + type);
            return (T) BlockModel.fromString(json.toString());
        }
    };
    private FactionIdentityModelTest() {}
    public static void verify() {
        int parsed = 0;
        for (String faction : List.of("crimson", "azure", "verdant", "ashen")) {
            for (int grade = 1; grade <= 4; grade++) {
                String prefix = "faction_identity/" + faction + "/band_" + grade;
                var root = JsonParser.parseString(read("faction_" + faction + "_band_" + grade)).getAsJsonObject();
                check(root.get("loader").getAsString().equals("forge:separate_transforms"), "native perspective loader");
                check(SeparateTransformsModel.Loader.INSTANCE.read(root, CONTEXT) != null, "Forge wrapper parser");
                parsed++;
                BlockModel mesh = BlockModel.fromString(read(prefix + "_mesh"));
                check(mesh.getElements().size() >= 8, "native held mesh elements");
                BlockModel gui = BlockModel.fromString(read(prefix + "_icon"));
                check(gui.getElements().isEmpty(), "inventory icon uses generated item layers");
                parsed += 2;
            }
            for (int tier = 1; tier <= 3; tier++) for (String suffix : List.of("", "_boss")) {
                check(BlockModel.fromString(read("faction_" + faction + "_tier" + tier + suffix + "_spawn_egg")) != null,
                        "native egg model parser");
                parsed++;
            }
        }
        check(parsed == 72, "all 16 wrappers, 16 meshes, 16 icons and 24 eggs parsed");
        System.out.println("FactionIdentityModelTest: 72 native model parses passed; rendering still needs in-game acceptance");
    }
    private static String read(String name) {
        try (var input = FactionIdentityModelTest.class.getResourceAsStream(MODELS + name + ".json")) {
            if (input == null) throw new IOException("Missing packaged model resource: " + MODELS + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
