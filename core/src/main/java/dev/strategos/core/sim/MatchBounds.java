package dev.strategos.core.sim;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.Vec2d;
import org.jspecify.annotations.Nullable;

/**
 * A match's space: match-local X in [0, sizeX) and Z in [0, sizeZ), absolute Y in [minY, maxY]. {@code flatFloorY} is
 * M0's stand-in for terrain, the Y every unit stands on; M1 removes it with a snapshot migration.
 */
@CoreApi
public record MatchBounds(int sizeX, int sizeZ, int minY, int maxY, double flatFloorY) {
    public static final int MAX_SIZE = 2048;
    public static final int MAX_HEIGHT = 512;

    /** Decoding reports a bad value as an error, never by throwing. */
    public static final Codec<MatchBounds> CODEC = Raw.CODEC.flatXmap(Raw::toBounds, Raw::of);

    public MatchBounds {
        String problem = problem(sizeX, sizeZ, minY, maxY, flatFloorY);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
    }

    public boolean contains(double x, double z) {
        return x >= 0 && x < sizeX && z >= 0 && z < sizeZ;
    }

    /** {@code p} moved inside the bounds, at least {@code margin} from every edge. */
    public Vec2d clamp(Vec2d p, double margin) {
        return new Vec2d(clamp(p.x(), margin, sizeX - margin), clamp(p.z(), margin, sizeZ - margin));
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static @Nullable String problem(int sizeX, int sizeZ, int minY, int maxY, double flatFloorY) {
        if (sizeX < 1 || sizeX > MAX_SIZE || sizeZ < 1 || sizeZ > MAX_SIZE) {
            return "match size " + sizeX + " x " + sizeZ + " outside 1.." + MAX_SIZE;
        }
        if (maxY <= minY || maxY - minY > MAX_HEIGHT) {
            return "match height " + minY + ".." + maxY + " must be 1 to " + MAX_HEIGHT + " blocks";
        }
        if (!(flatFloorY >= minY && flatFloorY <= maxY)) {
            return "floor " + flatFloorY + " outside " + minY + ".." + maxY;
        }
        return null;
    }

    private record Raw(int sizeX, int sizeZ, int minY, int maxY, double flatFloorY) {
        static final Codec<Raw> CODEC = RecordCodecBuilder.create(i -> i.group(
                        Codec.INT.fieldOf("sizeX").forGetter(Raw::sizeX),
                        Codec.INT.fieldOf("sizeZ").forGetter(Raw::sizeZ),
                        Codec.INT.fieldOf("minY").forGetter(Raw::minY),
                        Codec.INT.fieldOf("maxY").forGetter(Raw::maxY),
                        Codec.DOUBLE.fieldOf("flatFloorY").forGetter(Raw::flatFloorY))
                .apply(i, Raw::new));

        static DataResult<Raw> of(MatchBounds b) {
            return DataResult.success(new Raw(b.sizeX(), b.sizeZ(), b.minY(), b.maxY(), b.flatFloorY()));
        }

        DataResult<MatchBounds> toBounds() {
            String problem = problem(sizeX, sizeZ, minY, maxY, flatFloorY);
            return problem == null
                    ? DataResult.success(new MatchBounds(sizeX, sizeZ, minY, maxY, flatFloorY))
                    : DataResult.error(() -> problem);
        }
    }
}
