package dev.strategos.core.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.CoreApi;

/**
 * A job the map's terrain has to do (MAP-02, spec §2.10), written in its manifest and verified by the map checker:
 *
 * <ul>
 *   <li>{@value #HILL}: the ground at ({@code x0}, {@code z0}) stands at least {@code size} blocks above the ground at
 *       ({@code x1}, {@code z1}), and a unit reaches it.
 *   <li>{@value #FOREST_LINE} and {@value #RIVER}: a band from ({@code x0}, {@code z0}) to ({@code x1}, {@code z1})
 *       that a unit crosses only at {@code count} places (gaps, fords), each at least {@code size} blocks wide. The band
 *       runs along its longer side; a crossing goes over its shorter one.
 *   <li>{@value #RESOURCE}: at least {@code count} of {@code resource} lies within {@code size} blocks of ({@code x0},
 *       {@code z0}).
 *   <li>{@value #ROOM}: the rectangle from ({@code x0}, {@code z0}) to ({@code x1}, {@code z1}) is level and clear, with
 *       room for {@code count} squares of {@code size} blocks (fields).
 * </ul>
 */
@CoreApi
public record TerrainJob(String kind, String resource, int x0, int z0, int x1, int z1, int count, int size) {
    public static final String HILL = "hill";
    public static final String FOREST_LINE = "forest_line";
    public static final String RIVER = "river";
    public static final String RESOURCE = "resource";
    public static final String ROOM = "room";

    public static final Codec<TerrainJob> CODEC = RecordCodecBuilder.<TerrainJob>create(i -> i.group(
                            Codec.STRING.fieldOf("kind").forGetter(TerrainJob::kind),
                            Codec.STRING.optionalFieldOf("resource", "").forGetter(TerrainJob::resource),
                            Codec.INT.fieldOf("x0").forGetter(TerrainJob::x0),
                            Codec.INT.fieldOf("z0").forGetter(TerrainJob::z0),
                            Codec.INT.optionalFieldOf("x1", 0).forGetter(TerrainJob::x1),
                            Codec.INT.optionalFieldOf("z1", 0).forGetter(TerrainJob::z1),
                            Codec.INT.optionalFieldOf("count", 0).forGetter(TerrainJob::count),
                            Codec.INT.optionalFieldOf("size", 0).forGetter(TerrainJob::size))
                    .apply(i, TerrainJob::new))
            .validate(TerrainJob::check);

    public static TerrainJob hill(int x, int z, int overX, int overZ, int rise) {
        return new TerrainJob(HILL, "", x, z, overX, overZ, 0, rise);
    }

    public static TerrainJob crossings(String kind, int x0, int z0, int x1, int z1, int crossings, int width) {
        return new TerrainJob(kind, "", x0, z0, x1, z1, crossings, width);
    }

    public static TerrainJob resource(String resource, int x, int z, int radius, int amount) {
        return new TerrainJob(RESOURCE, resource, x, z, 0, 0, amount, radius);
    }

    public static TerrainJob room(int x0, int z0, int x1, int z1, int squares, int side) {
        return new TerrainJob(ROOM, "", x0, z0, x1, z1, squares, side);
    }

    private static DataResult<TerrainJob> check(TerrainJob j) {
        if (!switch (j.kind) {
            case HILL, FOREST_LINE, RIVER, RESOURCE, ROOM -> true;
            default -> false;
        }) {
            return DataResult.error(() -> "unknown terrain job '" + j.kind + "'");
        }
        if (j.kind.equals(RESOURCE) && j.resource.isEmpty()) {
            return DataResult.error(() -> "a resource job names no resource");
        }
        if (j.count < 0 || j.size < 0) {
            return DataResult.error(() -> "a terrain job with a negative count or size");
        }
        return DataResult.success(j);
    }
}
