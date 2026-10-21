package dev.strategos.mod.server.match;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.sim.MatchBounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Where a match lives in one level: {@code sizeX} x {@code sizeZ} blocks from {@code origin} and the block band
 * {@code minY..maxY}, both ends included; the origin's Y is the band's bottom. Match-local X and Z are offsets from the
 * origin; Y stays absolute, as in {@link MatchBounds}.
 */
public record MatchSpace(ResourceKey<Level> level, BlockPos origin, int sizeX, int sizeZ, int minY, int maxY) {
    /** Decoding reports a bad value as an error, never by throwing. */
    public static final Codec<MatchSpace> CODEC = Raw.CODEC.flatXmap(Raw::toSpace, Raw::of);

    public MatchSpace {
        String problem = problem(sizeX, sizeZ, minY, maxY);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
    }

    /** A GameTest's bounds, the whole structure: the box's lower corner is the origin, its blocks the band. */
    public static MatchSpace fromBounds(ResourceKey<Level> level, AABB bounds) {
        int x0 = floor(bounds.minX);
        int z0 = floor(bounds.minZ);
        return fromBounds(level, bounds, 0, 0, ceil(bounds.maxX) - x0, ceil(bounds.maxZ) - z0);
    }

    /** A {@code sizeX} x {@code sizeZ} part of a GameTest's bounds, {@code offsetX}, {@code offsetZ} from its corner. */
    public static MatchSpace fromBounds(
            ResourceKey<Level> level, AABB bounds, int offsetX, int offsetZ, int sizeX, int sizeZ) {
        int x0 = floor(bounds.minX);
        int z0 = floor(bounds.minZ);
        if (offsetX < 0
                || offsetZ < 0
                || offsetX + sizeX > ceil(bounds.maxX) - x0
                || offsetZ + sizeZ > ceil(bounds.maxZ) - z0) {
            throw new IllegalArgumentException("a " + sizeX + " x " + sizeZ + " space at offset (" + offsetX + ", "
                    + offsetZ + ") does not fit in " + bounds);
        }
        int minY = floor(bounds.minY);
        return new MatchSpace(
                level, new BlockPos(x0 + offsetX, minY, z0 + offsetZ), sizeX, sizeZ, minY, ceil(bounds.maxY) - 1);
    }

    /** {@code sizeX} x {@code sizeZ} around {@code centre}, from {@code below} blocks under it to {@code above} over. */
    public static MatchSpace centredOn(
            ResourceKey<Level> level, BlockPos centre, int sizeX, int sizeZ, int below, int above) {
        int minY = centre.getY() - below;
        return new MatchSpace(
                level,
                new BlockPos(centre.getX() - sizeX / 2, minY, centre.getZ() - sizeZ / 2),
                sizeX,
                sizeZ,
                minY,
                centre.getY() + above);
    }

    public double worldX(double localX) {
        return origin.getX() + localX;
    }

    public double worldZ(double localZ) {
        return origin.getZ() + localZ;
    }

    public double localX(double worldX) {
        return worldX - origin.getX();
    }

    public double localZ(double worldZ) {
        return worldZ - origin.getZ();
    }

    /** Whether the block is inside; the level is the caller's to check. */
    public boolean contains(BlockPos pos) {
        return pos.getX() >= origin.getX()
                && pos.getX() < origin.getX() + sizeX
                && pos.getZ() >= origin.getZ()
                && pos.getZ() < origin.getZ() + sizeZ
                && pos.getY() >= minY
                && pos.getY() <= maxY;
    }

    /** In the same level and closer than {@code margin} blocks on every axis: a gap of {@code margin} is apart. */
    public boolean overlaps(MatchSpace other, int margin) {
        return level.equals(other.level)
                && near(
                        origin.getX(),
                        origin.getX() + sizeX,
                        other.origin.getX(),
                        other.origin.getX() + other.sizeX,
                        margin)
                && near(
                        origin.getZ(),
                        origin.getZ() + sizeZ,
                        other.origin.getZ(),
                        other.origin.getZ() + other.sizeZ,
                        margin)
                && near(minY, maxY + 1, other.minY, other.maxY + 1, margin);
    }

    public ChunkPos minChunk() {
        return new ChunkPos(origin.getX() >> 4, origin.getZ() >> 4);
    }

    public ChunkPos maxChunk() {
        return new ChunkPos((origin.getX() + sizeX - 1) >> 4, (origin.getZ() + sizeZ - 1) >> 4);
    }

    /** The core's view of this space, with units standing at {@code flatFloorY}. */
    public MatchBounds toBounds(double flatFloorY) {
        return new MatchBounds(sizeX, sizeZ, minY, maxY, flatFloorY);
    }

    /** Half-open intervals [a0, a1) and [b0, b1) whose gap is under {@code margin}. */
    private static boolean near(int a0, int a1, int b0, int b1, int margin) {
        return b0 - a1 < margin && a0 - b1 < margin;
    }

    private static int floor(double v) {
        return (int) Math.floor(v);
    }

    private static int ceil(double v) {
        return (int) Math.ceil(v);
    }

    private static @Nullable String problem(int sizeX, int sizeZ, int minY, int maxY) {
        if (sizeX < 1 || sizeZ < 1) {
            return "match space " + sizeX + " x " + sizeZ + " must be at least 1 x 1";
        }
        if (maxY < minY) {
            return "match space Y band " + minY + ".." + maxY + " is empty";
        }
        return null;
    }

    private record Raw(ResourceKey<Level> level, BlockPos origin, int sizeX, int sizeZ, int minY, int maxY) {
        static final Codec<Raw> CODEC = RecordCodecBuilder.create(i -> i.group(
                        ResourceKey.codec(Registries.DIMENSION).fieldOf("level").forGetter(Raw::level),
                        BlockPos.CODEC.fieldOf("origin").forGetter(Raw::origin),
                        Codec.INT.fieldOf("sizeX").forGetter(Raw::sizeX),
                        Codec.INT.fieldOf("sizeZ").forGetter(Raw::sizeZ),
                        Codec.INT.fieldOf("minY").forGetter(Raw::minY),
                        Codec.INT.fieldOf("maxY").forGetter(Raw::maxY))
                .apply(i, Raw::new));

        static DataResult<Raw> of(MatchSpace s) {
            return DataResult.success(new Raw(s.level(), s.origin(), s.sizeX(), s.sizeZ(), s.minY(), s.maxY()));
        }

        DataResult<MatchSpace> toSpace() {
            String problem = problem(sizeX, sizeZ, minY, maxY);
            return problem == null
                    ? DataResult.success(new MatchSpace(level, origin, sizeX, sizeZ, minY, maxY))
                    : DataResult.error(() -> problem);
        }
    }
}
