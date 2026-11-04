package dev.felix.hostilehumans.settlement;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPInputStream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

/** Instance-scoped portable files shared by all worlds of the same game instance. */
final class SettlementTemplateLibrary {
    static final long MAX_COMPRESSED_BYTES = 16L * 1024 * 1024;
    private static final long MAX_NBT_BYTES = 128L * 1024 * 1024;

    private SettlementTemplateLibrary() {}

    private static Path root() {
        return FMLPaths.CONFIGDIR.get().resolve("hostile_humans_unified").resolve("building_library");
    }

    static void validateId(ResourceLocation id) {
        for (String segment : id.getPath().split("/", -1))
            if (segment.isEmpty() || segment.equals(".") || segment.equals(".."))
                throw new IllegalArgumentException("invalid template path");
        if (id.getNamespace().equals(".") || id.getNamespace().equals(".."))
            throw new IllegalArgumentException("invalid template namespace");
    }

    static Path path(ResourceLocation id) {
        validateId(id);
        Path root = root();
        Path file = root.resolve(id.getNamespace()).resolve("structures").resolve(id.getPath() + ".nbt");
        for (Path current = file; current != null && current.startsWith(root.getParent()); current = current.getParent())
            if (Files.isSymbolicLink(current)) throw new IllegalArgumentException("template symlinks are not allowed");
        return file;
    }

    static List<ResourceLocation> list() throws IOException {
        Path root = root();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) return List.of();
        try (var files = Files.walk(root, 20)) {
            return files.filter(file -> Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
                    .map(root::relativize)
                    .filter(file -> file.getNameCount() >= 3 && file.getName(1).toString().equals("structures"))
                    .filter(file -> file.toString().endsWith(".nbt"))
                    .map(file -> {
                        String id = file.subpath(2, file.getNameCount()).toString().replace('\\', '/');
                        return ResourceLocation.tryParse(file.getName(0) + ":" + id.substring(0, id.length() - 4));
                    })
                    .filter(java.util.Objects::nonNull)
                    .filter(id -> id.toString().length() <= 256)
                    .sorted()
                    .limit(256)
                    .toList();
        }
    }

    static CompoundTag read(Path file) throws IOException {
        if (Files.size(file) > MAX_COMPRESSED_BYTES) throw new IOException("template file too large");
        try (var input = Files.newInputStream(file)) {
            return readCompressed(input);
        }
    }

    static CompoundTag read(byte[] bytes) throws IOException {
        if (bytes.length > MAX_COMPRESSED_BYTES) throw new IOException("template file too large");
        return readCompressed(new java.io.ByteArrayInputStream(bytes));
    }

    private static CompoundTag readCompressed(java.io.InputStream stream) throws IOException {
        try (var input = new DataInputStream(new BufferedInputStream(new GZIPInputStream(stream)))) {
            return NbtIo.read(input, new NbtAccounter(MAX_NBT_BYTES));
        }
    }
}
