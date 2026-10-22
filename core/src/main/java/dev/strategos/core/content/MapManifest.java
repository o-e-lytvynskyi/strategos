package dev.strategos.core.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.CoreApi;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A map package's manifest (04 §3.3, MAP-01): {@code sizeX} x {@code sizeZ} blocks over a band of {@code height}
 * blocks, which an arena slot places from Y {@code minY}; tiles of {@code tileSize} x height x {@code tileSize} blocks
 * in the vanilla structure format, each column storing its blocks from its lowest one that is not {@code baseBlock},
 * the importer filling base blocks below; the start slots and waypoints; and the terrain hash: hex SHA-256 over the
 * {@code palette} index of every voxel ({@link TerrainHash}). The palette lists block states as Minecraft writes them,
 * air included.
 */
@CoreApi
public record MapManifest(
        String id,
        int sizeX,
        int sizeZ,
        int minY,
        int height,
        String baseBlock,
        int tileSize,
        List<String> palette,
        List<StartSlot> startSlots,
        Map<String, MapWaypoint> waypoints,
        int version,
        String terrainHash) {
    public static final Codec<MapManifest> CODEC = RecordCodecBuilder.<MapManifest>create(i -> i.group(
                            Codec.STRING.fieldOf("id").forGetter(MapManifest::id),
                            Codec.intRange(1, 4_096).fieldOf("sizeX").forGetter(MapManifest::sizeX),
                            Codec.intRange(1, 4_096).fieldOf("sizeZ").forGetter(MapManifest::sizeZ),
                            Codec.INT.fieldOf("minY").forGetter(MapManifest::minY),
                            Codec.intRange(1, 384).fieldOf("height").forGetter(MapManifest::height),
                            Codec.STRING.fieldOf("baseBlock").forGetter(MapManifest::baseBlock),
                            Codec.intRange(1, 48).fieldOf("tileSize").forGetter(MapManifest::tileSize),
                            Codec.STRING.listOf().fieldOf("palette").forGetter(MapManifest::palette),
                            StartSlot.CODEC.listOf().fieldOf("startSlots").forGetter(MapManifest::startSlots),
                            Codec.unboundedMap(Codec.STRING, MapWaypoint.CODEC)
                                    .fieldOf("waypoints")
                                    .forGetter(MapManifest::waypoints),
                            Codec.intRange(1, Integer.MAX_VALUE)
                                    .fieldOf("version")
                                    .forGetter(MapManifest::version),
                            Codec.STRING.fieldOf("terrainHash").forGetter(MapManifest::terrainHash))
                    .apply(i, MapManifest::new))
            .validate(MapManifest::check);

    public MapManifest {
        palette = List.copyOf(palette);
        startSlots = List.copyOf(startSlots);
        waypoints = Collections.unmodifiableMap(new TreeMap<>(waypoints));
    }

    /** Tiles along X. */
    public int tilesX() {
        return (sizeX + tileSize - 1) / tileSize;
    }

    /** Tiles along Z. */
    public int tilesZ() {
        return (sizeZ + tileSize - 1) / tileSize;
    }

    /** The tile file of tile ({@code i}, {@code j}): {@code x<i>_z<j>}. */
    public static String tileName(int i, int j) {
        return "x" + i + "_z" + j;
    }

    private static DataResult<MapManifest> check(MapManifest m) {
        if (!m.palette.contains("minecraft:air")) {
            return DataResult.error(() -> m.id + ": the palette has no minecraft:air");
        }
        if (m.palette.size() > 65_536) {
            return DataResult.error(() -> m.id + ": " + m.palette.size() + " palette entries, more than 65,536");
        }
        if (!m.terrainHash.matches("[0-9a-f]{64}")) {
            return DataResult.error(() -> m.id + ": the terrain hash is not 64 hex digits");
        }
        for (Map.Entry<String, MapWaypoint> e : m.waypoints.entrySet()) {
            if (!m.inside(e.getValue().x(), e.getValue().y(), e.getValue().z())) {
                return DataResult.error(() -> m.id + ": waypoint " + e.getKey() + " lies outside the map");
            }
        }
        for (StartSlot s : m.startSlots) {
            if (!m.inside(s.x(), s.y(), s.z())) {
                return DataResult.error(() -> m.id + ": a start slot lies outside the map");
            }
        }
        return DataResult.success(m);
    }

    private boolean inside(int x, int y, int z) {
        return x >= 0 && z >= 0 && y >= 0 && x < sizeX && z < sizeZ && y < height;
    }
}
