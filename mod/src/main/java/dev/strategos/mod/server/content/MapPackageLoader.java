package dev.strategos.mod.server.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.MapManifest;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Finds and reads map packages (04 §3.3): a package exported in this world ({@code <world>/strategos/maps/<path>.json}
 * and its tiles in {@code <path>/}) wins over one in the data packs ({@code data/<ns>/strategos/maps/<path>.json}, tiles
 * in {@code data/<ns>/structure/strategos/maps/<path>/}). Tiles are named {@code x<i>_z<j>.nbt}.
 */
public final class MapPackageLoader {
    private static final String MAPS = "strategos/maps";

    private MapPackageLoader() {}

    /** The folder of this world's exported packages. */
    public static Path exports(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("strategos").resolve("maps");
    }

    /** Every package id the data packs and this world's exports hold. */
    public static List<ResourceLocation> list(MinecraftServer server) {
        TreeSet<ResourceLocation> ids = new TreeSet<>();
        server.getResourceManager()
                .listResources(MAPS, rl -> rl.getPath().endsWith(".json"))
                .keySet()
                .forEach(rl -> ids.add(ResourceLocation.fromNamespaceAndPath(
                        rl.getNamespace(),
                        rl.getPath().substring(MAPS.length() + 1, rl.getPath().length() - 5))));
        Path exports = exports(server);
        if (Files.isDirectory(exports)) {
            try (var namespaces = Files.list(exports)) {
                for (Path ns : namespaces.filter(Files::isDirectory).toList()) {
                    try (var files = Files.list(ns)) {
                        files.filter(f -> f.getFileName().toString().endsWith(".json"))
                                .forEach(f -> {
                                    String name = f.getFileName().toString();
                                    ids.add(ResourceLocation.fromNamespaceAndPath(
                                            ns.getFileName().toString(), name.substring(0, name.length() - 5)));
                                });
                    }
                }
            } catch (IOException e) {
                throw new IllegalStateException("cannot list " + exports, e);
            }
        }
        return new ArrayList<>(ids);
    }

    public static MapPackage load(MinecraftServer server, ResourceLocation id) {
        Path exported = exports(server).resolve(id.getNamespace());
        Path manifestFile = exported.resolve(id.getPath() + ".json");
        boolean inWorld = Files.isRegularFile(manifestFile);
        MapManifest manifest;
        try {
            manifest = decode(id, inWorld ? read(manifestFile) : readResource(server, manifestLocation(id)));
        } catch (IOException e) {
            throw new IllegalArgumentException("map " + id + ": cannot read its manifest: " + e.getMessage(), e);
        }
        List<List<MapTile.Placed>> tiles = new ArrayList<>();
        for (int j = 0; j < manifest.tilesZ(); j++) {
            for (int i = 0; i < manifest.tilesX(); i++) {
                String tile = MapManifest.tileName(i, j) + ".nbt";
                CompoundTag tag;
                try {
                    tag = inWorld
                            ? readNbt(Files.newInputStream(
                                    exported.resolve(id.getPath()).resolve(tile)))
                            : readNbt(resource(server, tileLocation(id, i, j)).open());
                } catch (IOException e) {
                    throw new IllegalArgumentException(
                            "map " + id + ": cannot read tile " + tile + ": " + e.getMessage(), e);
                }
                tiles.add(MapTile.read(tag, BuiltInRegistries.BLOCK.asLookup()));
            }
        }
        List<BlockState> palette = new ArrayList<>();
        for (String state : manifest.palette()) {
            palette.add(parse(id, state));
        }
        return new MapPackage(manifest, tiles, parse(id, manifest.baseBlock()), palette);
    }

    public static ResourceLocation manifestLocation(ResourceLocation id) {
        return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), MAPS + "/" + id.getPath() + ".json");
    }

    public static ResourceLocation tileLocation(ResourceLocation id, int i, int j) {
        return ResourceLocation.fromNamespaceAndPath(
                id.getNamespace(),
                "structure/" + MAPS + "/" + id.getPath() + "/" + MapManifest.tileName(i, j) + ".nbt");
    }

    private static MapManifest decode(ResourceLocation id, JsonElement json) {
        return MapManifest.CODEC
                .parse(JsonOps.INSTANCE, json)
                .getOrThrow(message -> new IllegalArgumentException("map " + id + ": " + message));
    }

    private static BlockState parse(ResourceLocation id, String state) {
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), state, false)
                    .blockState();
        } catch (CommandSyntaxException e) {
            throw new IllegalArgumentException("map " + id + ": not a block state: " + state, e);
        }
    }

    private static JsonElement read(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        }
    }

    private static JsonElement readResource(MinecraftServer server, ResourceLocation location) throws IOException {
        try (Reader reader = resource(server, location).openAsReader()) {
            return JsonParser.parseReader(reader);
        }
    }

    private static Resource resource(MinecraftServer server, ResourceLocation location) throws IOException {
        Optional<Resource> resource = server.getResourceManager().getResource(location);
        if (resource.isEmpty()) {
            throw new IOException("no " + location + " in the data packs");
        }
        return resource.get();
    }

    private static CompoundTag readNbt(InputStream stream) throws IOException {
        try (InputStream in = stream) {
            return NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
        }
    }
}
