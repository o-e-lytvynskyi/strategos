package dev.strategos.mod.server.match;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.MapManifest;
import dev.strategos.core.content.TerrainHash;
import dev.strategos.mod.server.content.MapPackageLoader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Writes a region of a level as a map package into this world's exports (04 §3.3, {@code /strategos map export}): its
 * tiles with {@code StructureTemplate#fillFromWorld} and {@code save} (StructureTemplate.java:83, 609), and a manifest
 * with the region's palette, in order of first appearance, and its terrain hash. Every block is stored, so the base
 * block is air: an import fills nothing below.
 */
public final class MapExporter {
    private MapExporter() {}

    /** Exports the box from {@code from} to {@code to}, both corners included; returns the manifest. */
    public static MapManifest export(ServerLevel level, ResourceLocation id, BlockPos from, BlockPos to) {
        BlockPos min = new BlockPos(
                Math.min(from.getX(), to.getX()), Math.min(from.getY(), to.getY()), Math.min(from.getZ(), to.getZ()));
        int sizeX = Math.abs(to.getX() - from.getX()) + 1;
        int sizeZ = Math.abs(to.getZ() - from.getZ()) + 1;
        int height = Math.abs(to.getY() - from.getY()) + 1;
        int tile = 32;
        Path dir = MapPackageLoader.exports(level.getServer()).resolve(id.getNamespace());
        Path tiles = dir.resolve(id.getPath());
        Map<BlockState, Integer> indices = new HashMap<>();
        List<String> palette = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        String hash = TerrainHash.of(sizeX, sizeZ, height, (x, y, z) -> {
            BlockState state = level.getBlockState(pos.set(min.getX() + x, min.getY() + y, min.getZ() + z));
            return indices.computeIfAbsent(state, s -> {
                palette.add(BlockStateParser.serialize(s));
                return palette.size() - 1;
            });
        });
        if (!palette.contains("minecraft:air")) {
            palette.add("minecraft:air");
        }
        MapManifest manifest = new MapManifest(
                id.toString(),
                sizeX,
                sizeZ,
                min.getY(),
                height,
                "minecraft:air",
                tile,
                palette,
                List.of(),
                Map.of(),
                1,
                hash);
        try {
            Files.createDirectories(tiles);
            for (int j = 0; j < manifest.tilesZ(); j++) {
                for (int i = 0; i < manifest.tilesX(); i++) {
                    StructureTemplate template = new StructureTemplate();
                    template.fillFromWorld(
                            level,
                            min.offset(i * tile, 0, j * tile),
                            new Vec3i(Math.min(tile, sizeX - i * tile), height, Math.min(tile, sizeZ - j * tile)),
                            false,
                            null);
                    NbtIo.writeCompressed(
                            template.save(new CompoundTag()), tiles.resolve(MapManifest.tileName(i, j) + ".nbt"));
                }
            }
            JsonElement json =
                    MapManifest.CODEC.encodeStart(JsonOps.INSTANCE, manifest).getOrThrow();
            Files.writeString(
                    dir.resolve(id.getPath() + ".json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(json) + "\n",
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot export map " + id, e);
        }
        return manifest;
    }
}
