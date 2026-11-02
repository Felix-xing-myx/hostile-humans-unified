package dev.felix.hostilehumans.common;

import com.google.gson.JsonParser;

import dev.felix.hostilehumans.cultivation.CultivationContent;
import dev.felix.hostilehumans.economy.EconomyContent;
import dev.felix.hostilehumans.settlement.SettlementContent;

import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.resources.ResourceLocation;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import javax.imageio.ImageIO;

/**
 * Actual registry IDs, vanilla model parser, resource graph and PNG/localization checks without a
 * game window.
 */
public final class FacilityResourcesTest {
    private static int models;

    private static String read(String path) throws IOException {
        try (var input = FacilityResourcesTest.class.getResourceAsStream(path)) {
            if (input == null) throw new IOException("Missing packaged resource: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void model(ResourceLocation id) throws IOException {
        String text = read("/assets/" + id.getNamespace() + "/models/" + id.getPath() + ".json");
        if (BlockModel.fromString(text) == null)
            throw new AssertionError("Native model parser: " + id);
        var json = JsonParser.parseString(text).getAsJsonObject();
        if (json.has("parent")) {
            var parent = new ResourceLocation(json.get("parent").getAsString());
            if (parent.getNamespace().equals("hostile_humans_unified"))
                read("/assets/" + parent.getNamespace() + "/models/" + parent.getPath() + ".json");
        }
        if (json.has("textures"))
            for (var entry : json.getAsJsonObject("textures").entrySet()) {
                String value = entry.getValue().getAsString();
                if (value.startsWith("#")) continue;
                var texture = new ResourceLocation(value);
                try (var input =
                        FacilityResourcesTest.class.getResourceAsStream(
                                "/assets/"
                                        + texture.getNamespace()
                                        + "/textures/"
                                        + texture.getPath()
                                        + ".png")) {
                    if (input == null) throw new AssertionError("Missing model texture " + texture);
                    var image = ImageIO.read(input);
                    if (image == null || image.getWidth() != 32 || image.getHeight() != 32)
                        throw new AssertionError("Initial32px texture: " + texture);
                }
            }
        models++;
    }

    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        var fixture = net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped");
        fixture.setAccessible(true);
        fixture.setBoolean(null, true);
        net.minecraft.core.registries.BuiltInRegistries.bootStrap();
        Set<ResourceLocation> blocks = new LinkedHashSet<>(), items = new LinkedHashSet<>();
        for (var registry : List.of(SettlementContent.BLOCKS, EconomyContent.BLOCKS))
            registry.getEntries().forEach(row -> blocks.add(row.getId()));
        blocks.add(CultivationContent.TRAINING_BLOCK.getId());
        for (var registry : List.of(SettlementContent.ITEMS, EconomyContent.ITEMS))
            registry.getEntries().forEach(row -> items.add(row.getId()));
        for (var row :
                List.of(
                        CultivationContent.TRAINING_ITEM,
                        CultivationContent.SUPPLIES,
                        CultivationContent.POTENTIAL,
                        CultivationContent.EXPANSION)) items.add(row.getId());
        for (var id : blocks) {
            var state =
                    JsonParser.parseString(
                                    read(
                                            "/assets/"
                                                    + id.getNamespace()
                                                    + "/blockstates/"
                                                    + id.getPath()
                                                    + ".json"))
                            .getAsJsonObject();
            String target =
                    state.getAsJsonObject("variants")
                            .getAsJsonObject("")
                            .get("model")
                            .getAsString();
            model(new ResourceLocation(target));
        }
        for (var id : items) model(new ResourceLocation(id.getNamespace(), "item/" + id.getPath()));
        for (String locale : List.of("zh_cn", "en_us")) {
            var text =
                    JsonParser.parseString(
                                    read("/assets/hostile_humans_unified/lang/" + locale + ".json"))
                            .getAsJsonObject();
            for (var id : items) {
                String key =
                        (blocks.contains(id) ? "block." : "item.")
                                + id.getNamespace()
                                + "."
                                + id.getPath();
                if (!text.has(key)) throw new AssertionError("Missing " + locale + " label " + key);
            }
        }
        if (blocks.size() != 12 || items.size() != 30 || models != 42)
            throw new AssertionError(
                    "Registry/model coverage " + blocks.size() + "/" + items.size() + "/" + models);
        System.out.println(
                "FacilityResourcesTest:42 actual-ID native models,54 texture references and"
                    + " bilingual labels passed; visual acceptance remains separate");
    }
}
