package dev.strategos.gametest.spike;

import dev.strategos.mod.server.command.StrategosCommands;
import dev.strategos.mod.server.match.MatchSpace;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * A square arena force-loaded through the spikes' ticket controller, as ADR-0010 loads arenas: S1's 96 x 96 at (1024,
 * floor, 1024) and S2's 256² to 512² at 8192, with {@code ticking=false} tickets, which already make the chunks
 * entity-ticking (every region ticket enters the ticking tracker, DistanceManager.java:197–202); {@code ticking=true}
 * adds random ticks and natural spawning without players. Positions are local to the match space at the centre.
 */
final class SpikeArena {
    static final int S1_ORIGIN = 1024;
    static final int S1_SIZE = 96;
    /** The waypoints of "moving", 24 blocks apart. */
    static final double[] A = {36.5, 48.5};

    static final double[] B = {60.5, 48.5};
    /** The 16 x 16 blob "fighting" converges on, and the patch the build storm toggles. */
    static final int BLOB = 40;

    static final int STORM = 8;

    private static final UUID OWNER = UUID.nameUUIDFromBytes("strategos:spike_arena".getBytes(StandardCharsets.UTF_8));

    final ServerLevel level;
    final int origin;
    final int size;
    final int floorY;
    /** A match space of up to 96 x 96 at the arena's centre. */
    final MatchSpace space;

    private final List<long[]> chunks = new ArrayList<>();
    private boolean ticking;

    private SpikeArena(ServerLevel level, int origin, int size, int floorY, MatchSpace space) {
        this.level = level;
        this.origin = origin;
        this.size = size;
        this.floorY = floorY;
        this.space = space;
    }

    /** S1's arena. */
    static SpikeArena s1(ServerLevel level) {
        return open(level, S1_ORIGIN, S1_SIZE, false);
    }

    /** Forces every chunk of the square; the chunks load synchronously (ForcedChunkManager.java:93–96). */
    static SpikeArena open(ServerLevel level, int origin, int size, boolean ticking) {
        List<long[]> forced = new ArrayList<>();
        for (int cx = origin >> 4; cx <= (origin + size - 1) >> 4; cx++) {
            for (int cz = origin >> 4; cz <= (origin + size - 1) >> 4; cz++) {
                SpikeRegistry.TICKETS.forceChunk(level, OWNER, cx, cz, true, ticking);
                forced.add(new long[] {cx, cz});
            }
        }
        int centre = origin + size / 2;
        int floor = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centre, centre);
        int matchSize = Math.min(size, S1_SIZE);
        SpikeArena arena = new SpikeArena(
                level,
                origin,
                size,
                floor,
                StrategosCommands.spaceAround(level, new BlockPos(centre, floor, centre), matchSize, matchSize));
        arena.chunks.addAll(forced);
        arena.ticking = ticking;
        return arena;
    }

    int chunkCount() {
        return chunks.size();
    }

    boolean ticking() {
        return ticking;
    }

    /** Swaps every ticket for one of the other kind, the new one first, so no chunk unloads in between. */
    void retick(boolean ticking) {
        if (ticking == this.ticking) {
            return;
        }
        for (long[] c : chunks) {
            SpikeRegistry.TICKETS.forceChunk(level, OWNER, (int) c[0], (int) c[1], true, ticking);
            SpikeRegistry.TICKETS.forceChunk(level, OWNER, (int) c[0], (int) c[1], false, this.ticking);
        }
        this.ticking = ticking;
    }

    void close() {
        for (long[] c : chunks) {
            SpikeRegistry.TICKETS.forceChunk(level, OWNER, (int) c[0], (int) c[1], false, ticking);
        }
        chunks.clear();
    }

    Vec3 world(double localX, double localZ) {
        BlockPos o = space.origin();
        return new Vec3(o.getX() + localX, floorY, o.getZ() + localZ);
    }

    BlockPos block(int localX, int localZ) {
        BlockPos o = space.origin();
        return new BlockPos(o.getX() + localX, floorY, o.getZ() + localZ);
    }

    /** {@code n} positions one block apart in a square around a local point. */
    List<Vec3> grid(int n, double centreX, double centreZ) {
        int side = (int) Math.ceil(Math.sqrt(n));
        List<Vec3> points = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double x = centreX + (i % side) - side / 2.0;
            double z = centreZ + Math.floorDiv(i, side) - side / 2.0;
            points.add(world(x, z));
        }
        return points;
    }
}
