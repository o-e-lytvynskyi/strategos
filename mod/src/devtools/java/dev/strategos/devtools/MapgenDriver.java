package dev.strategos.devtools;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.MapManifest;
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
 * The {@code mapgen} run (spec §4.1, MAP-01): a self-terminating dedicated server that writes the proving ground's map
 * package from {@link ProvingGround}, the source of truth, into {@code strategos.mapgen.out} (the devtools resources):
 * the manifest at {@code data/strategos/strategos/maps/proving_ground.json} and 144 tiles of 32 x 64 x 32 at {@code
 * data/strategos/structure/strategos/maps/proving_ground/}, each column storing its blocks from the lowest that is not
 * stone. A server, because the tiles hold vanilla block states and the data version. Acts only when {@code
 * strategos.mapgen} is {@code proving_ground}; its marker names the hash for the run's guard.
 */
@EventBusSubscriber(modid = Strategos.MOD_ID)
public final class MapgenDriver {
    private static final Logger LOG = LoggerFactory.getLogger(MapgenDriver.class);

    private MapgenDriver() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onServerStarted(ServerStartedEvent event) {
        String map = System.getProperty("strategos.mapgen", "");
        if (map.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        JsonObject marker = new JsonObject();
        try {
            if (!map.equals("proving_ground")) {
                throw new IllegalArgumentException("mapgen knows proving_ground, not " + map);
            }
            Path out = Path.of(System.getProperty("strategos.mapgen.out", ""));
            String hash = writeProvingGround(out);
            marker.addProperty("ok", true);
            marker.addProperty("terrainHash", hash);
            LOG.info("mapgen: proving_ground written to {}, terrain {}", out, hash);
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

    private static String writeProvingGround(Path out) {
        ProvingGround ground = ProvingGround.generate();
        List<BlockState> states = new ArrayList<>();
        for (String state : ProvingGround.PALETTE) {
            try {
                states.add(BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), state, false)
                        .blockState());
            } catch (CommandSyntaxException e) {
                throw new IllegalStateException("the proving ground's palette has a bad state: " + state, e);
            }
        }
        String hash = ground.terrainHash();
        MapManifest manifest = new MapManifest(
                "strategos:proving_ground",
                ProvingGround.SIZE,
                ProvingGround.SIZE,
                0,
                ProvingGround.HEIGHT,
                "minecraft:stone",
                ProvingGround.TILE,
                ProvingGround.PALETTE,
                ground.startSlots(),
                ground.waypoints(),
                ProvingGround.VERSION,
                hash);
        Path maps = out.resolve("data/strategos/strategos/maps");
        Path tiles = out.resolve("data/strategos/structure/strategos/maps/proving_ground");
        try {
            Files.createDirectories(maps);
            Files.createDirectories(tiles);
            JsonElement json =
                    MapManifest.CODEC.encodeStart(JsonOps.INSTANCE, manifest).getOrThrow();
            Files.writeString(
                    maps.resolve("proving_ground.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(json) + "\n",
                    StandardCharsets.UTF_8);
            for (int j = 0; j < manifest.tilesZ(); j++) {
                for (int i = 0; i < manifest.tilesX(); i++) {
                    NbtIo.writeCompressed(
                            MapTile.write(
                                    ProvingGround.TILE,
                                    ProvingGround.HEIGHT,
                                    ProvingGround.TILE,
                                    tile(ground, states, i, j)),
                            tiles.resolve(MapManifest.tileName(i, j) + ".nbt"));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return hash;
    }

    /** Tile ({@code i}, {@code j})'s blocks: each column from its lowest stored block to its top, air left out. */
    private static List<MapTile.Placed> tile(ProvingGround ground, List<BlockState> states, int i, int j) {
        List<MapTile.Placed> blocks = new ArrayList<>();
        for (int x = 0; x < ProvingGround.TILE; x++) {
            for (int z = 0; z < ProvingGround.TILE; z++) {
                int mx = i * ProvingGround.TILE + x;
                int mz = j * ProvingGround.TILE + z;
                int lowest = ground.lowestStored(mx, mz);
                if (ground.block(mx, lowest, mz) == ProvingGround.AIR) {
                    throw new IllegalStateException("column (" + mx + ", " + mz + ") starts with air at " + lowest
                            + ": the importer would fill it with stone");
                }
                for (int y = lowest; y <= ground.top(mx, mz); y++) {
                    byte b = ground.block(mx, y, mz);
                    if (b != ProvingGround.AIR) {
                        blocks.add(new MapTile.Placed(x, y, z, states.get(b)));
                    }
                }
            }
        }
        return blocks;
    }
}
