package dev.felix.hostilehumans.loot;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Lossless JSON drafts; limits also apply to documents received over the network. */
public final class LootDocuments {
    public static final int MAX_BYTES = 65536;
    public static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private LootDocuments() {}

    public static JsonObject parse(String text) {
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new IllegalArgumentException("document_large");
        // Bound nesting before Gson's recursive descent, including malformed input.
        int depth = 0; boolean quoted = false, escape = false;
        for (char c : text.toCharArray()) {
            if (quoted) { if (escape) escape = false; else if (c == '\\') escape = true; else if (c == '"') quoted = false; }
            else if (c == '"') quoted = true;
            else if ((c == '{' || c == '[') && ++depth > 48) throw new IllegalArgumentException("document_deep");
            else if (c == '}' || c == ']') depth--;
        }
        JsonElement element = JsonParser.parseString(text);
        if (!element.isJsonObject()) throw new IllegalArgumentException("document_object");
        count(element, new int[]{0});
        return element.getAsJsonObject();
    }

    private static void count(JsonElement node, int[] count) {
        if (++count[0] > 4096) throw new IllegalArgumentException("document_nodes");
        if (node.isJsonArray()) for (var child : node.getAsJsonArray()) count(child, count);
        if (node.isJsonObject()) for (var child : node.getAsJsonObject().entrySet()) count(child.getValue(), count);
    }

    public static String digest(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    public static JsonObject empty() { return parse("{\"type\":\"minecraft:chest\",\"pools\":[]}"); }
}
