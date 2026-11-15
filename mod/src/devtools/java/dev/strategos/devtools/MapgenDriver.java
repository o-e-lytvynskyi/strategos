package dev.strategos.devtools;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.MapManifest;
import dev.strategos.core.content.MapWaypoint;
import dev.strategos.core.content.StartSlot;
import dev.strategos.core.content.TerrainJob;
import dev.strategos.core.testkit.FirstSkirmish;
import dev.strategos.core.testkit.ProvingGround;
import dev.strategos.mod.Strategos;
import dev.strategos.mod.server.content.MapTile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@code mapgen} run (spec §4.1, MAP-01, MAP-02): a self-terminating dedicated server that writes a map package
 * from its generator, the source of truth, into {@code strategos.mapgen.out}: the manifest at {@code
 * data/strategos/strategos/maps/<map>.json} and the tiles of 32 x 64 x 32 at {@code
 * data/strategos/structure/strategos/maps/<map>/}, each column storing its blocks from the lowest that is not stone.
 * {@code strategos.mapgen} names the map: {@code proving_ground} ({@link ProvingGround}, the devtools resources) or
 * {@code first_skirmish} ({@link FirstSkirmish}, the mod's own resources). A server, because the tiles hold vanilla
 * block states and the data version; its marker names the hash for the run's guard.
 */
@EventBusSubscriber(modid = Strategos.MOD_ID)
public final class MapgenDriver {
    private static final Logger LOG = LoggerFactory.getLogger(MapgenDriver.class);

    private MapgenDriver() {}

    /** A generated map as the writer reads it. */
    private interface Voxels {
        byte block(int x, int y, int z);
    }

    private interface Column {
        int at(int x, int z);
    }

    private record Source(
            String name,
            int size,
            int height,
            int tile,
            List<String> palette,
            Voxels voxels,
            Column top,
            Column lowestStored,
            List<StartSlot> startSlots,
            Map<String, MapWaypoint> waypoints,
            int version,
            String terrainHash,
            List<TerrainJob> jobs) {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onServerStarted(ServerStartedEvent event) {
        String map = System.getProperty("strategos.mapgen", "");
        if (map.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        JsonObject marker = new JsonObject();
        try {
            Path out = Path.of(System.getProperty("strategos.mapgen.out", ""));
            String hash = write(source(map), out);
            marker.addProperty("ok", true);
            marker.addProperty("map", map);
            marker.addProperty("terrainHash", hash);
            LOG.info("mapgen: {} written to {}, terrain {}", map, out, hash);
        } catch (RuntimeException e) {
            marker.addProperty("ok", false);
            marker.addProperty("reason", String.valueOf(e));
            LOG.error("mapgen failed", e);
        }
        try {
            Path file = Path.of(System.getProperty("strategos.mapgen.marker", "mapgen-result.json"));
            Files.writeString(
                    file, new GsonBuilder().setPrettyPrinting().create().toJson(marker) + "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.halt(false);
    }

    private static Source source(String map) {
        return switch (map) {
            case "proving_ground" -> {
                ProvingGround g = ProvingGround.generate();
                yield new Source(
                        map,
                        ProvingGround.SIZE,
                        ProvingGround.HEIGHT,
                        ProvingGround.TILE,
                        ProvingGround.PALETTE,
                        g::block,
                        g::top,
                        g::lowestStored,
                        g.startSlots(),
                        g.waypoints(),
                        ProvingGround.VERSION,
                        g.terrainHash(),
                        List.of());
            }
            case "first_skirmish" -> {
                FirstSkirmish g = FirstSkirmish.generate();
                yield new Source(
                        map,
                        FirstSkirmish.SIZE,
                        FirstSkirmish.HEIGHT,
                        FirstSkirmish.TILE,
                        FirstSkirmish.PALETTE,
                        g::block,
                        g::top,
                        g::lowestStored,
                        g.startSlots(),
                        g.waypoints(),
                        FirstSkirmish.VERSION,
                        g.terrainHash(),
                        g.terrainJobs());
            }
            default -> throw new IllegalArgumentException("mapgen knows proving_ground and first_skirmish, not " + map);
        };
    }

    private static String write(Source source, Path out) {
        List<BlockState> states = new ArrayList<>();
        for (String state : source.palette()) {
            try {
                states.add(BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), state, false)
                        .blockState());
            } catch (CommandSyntaxException e) {
                throw new IllegalStateException(source.name() + "'s palette has a bad state: " + state, e);
            }
        }
        MapManifest manifest = new MapManifest(
                "strategos:" + source.name(),
                source.size(),
                source.size(),
                0,
                source.height(),
                "minecraft:stone",
                source.tile(),
                source.palette(),
                source.startSlots(),
                source.waypoints(),
                source.version(),
                source.terrainHash(),
                source.jobs());
        Path maps = out.resolve("data/strategos/strategos/maps");
        Path tiles = out.resolve("data/strategos/structure/strategos/maps/" + source.name());
        try {
            Files.createDirectories(maps);
            Files.createDirectories(tiles);
            JsonElement json =
                    MapManifest.CODEC.encodeStart(JsonOps.INSTANCE, manifest).getOrThrow();
            Files.writeString(
                    maps.resolve(source.name() + ".json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(json) + "\n",
                    StandardCharsets.UTF_8);
            for (int j = 0; j < manifest.tilesZ(); j++) {
                for (int i = 0; i < manifest.tilesX(); i++) {
                    NbtIo.writeCompressed(
                            MapTile.write(source.tile(), source.height(), source.tile(), tile(source, states, i, j)),
                            tiles.resolve(MapManifest.tileName(i, j) + ".nbt"));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return source.terrainHash();
    }

    /** Tile ({@code i}, {@code j})'s blocks: each column from its lowest stored block to its top, air left out. */
    private static List<MapTile.Placed> tile(Source source, List<BlockState> states, int i, int j) {
        List<MapTile.Placed> blocks = new ArrayList<>();
        for (int x = 0; x < source.tile(); x++) {
            for (int z = 0; z < source.tile(); z++) {
                int mx = i * source.tile() + x;
                int mz = j * source.tile() + z;
                int lowest = source.lowestStored().at(mx, mz);
                if (source.voxels().block(mx, lowest, mz) == 0) {
                    throw new IllegalStateException("column (" + mx + ", " + mz + ") starts with air at " + lowest
                            + ": the importer would fill it with stone");
                }
                for (int y = lowest; y <= source.top().at(mx, mz); y++) {
                    byte b = source.voxels().block(mx, y, mz);
                    if (b != 0) {
                        blocks.add(new MapTile.Placed(x, y, z, states.get(b)));
                    }
                }
            }
        }
        return blocks;
    }
}
