package dev.felix.hostilehumans.testutil;

/** Test-only source/class lookup independent of module placement; duplicate names are forbidden by the layout audit. */
public final class ModuleTestSources {
    private static final com.google.gson.JsonObject INDEX;
    static {
        try (var input = ModuleTestSources.class.getResourceAsStream("/hhu-module-sources.json")) {
            if (input == null) throw new IllegalStateException("missing module source index");
            INDEX = com.google.gson.JsonParser.parseString(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
    private ModuleTestSources() {}
    public static String owner(String name) {
        if (!name.startsWith("dev/felix/hostilehumans/") && name.contains("/")) return name;
        String simple = name.substring(name.lastIndexOf('/') + 1);
        int nested = simple.indexOf('$'); String suffix = nested < 0 ? "" : simple.substring(nested);
        if (nested >= 0) simple = simple.substring(0, nested);
        var entry = INDEX.getAsJsonObject(simple);
        return entry == null ? name : entry.get("class").getAsString().replace('.', '/') + suffix;
    }
    public static String resource(String name) {
        return name.endsWith(".class") ? owner(name.substring(0, name.length() - 6)) + ".class" : name;
    }
    public static java.nio.file.Path source(String name) {
        String simple = name.substring(name.lastIndexOf('/') + 1).replace(".java", "");
        var entry = INDEX.getAsJsonObject(simple);
        if (entry == null) throw new IllegalArgumentException("unknown production source " + name);
        return java.nio.file.Path.of(System.getProperty("hhu.projectDir", ".")).resolve(entry.get("path").getAsString());
    }
}
