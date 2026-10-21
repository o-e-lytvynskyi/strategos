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
 * The 96 x 96 arena at (1024, floor, 1024), force-loaded so that its entities tick with no player near (spec 8.4, with
 * the ticking tickets of §1.6 D row 60). Positions are arena-local, like a match's.
 */
final class SpikeArena {
    static final int ORIGIN = 1024;
    static final int SIZE = 96;
    /** The waypoints of "moving", 24 blocks apart. */
    static final double[] A = {36.5, 48.5};

    static final double[] B = {60.5, 48.5};
    /** The 16 x 16 blob "fighting" converges on, and the patch the build storm toggles. */
    static final int BLOB = 40;

    static final int STORM = 8;

    private static final UUID OWNER = UUID.nameUUIDFromBytes("strategos:spike_arena".getBytes(StandardCharsets.UTF_8));

    final ServerLevel level;
    final int floorY;
    final MatchSpace space;
    private final List<long[]> chunks = new ArrayList<>();

    private SpikeArena(ServerLevel level, int floorY, MatchSpace space) {
        this.level = level;
        this.floorY = floorY;
        this.space = space;
    }

    static SpikeArena open(ServerLevel level) {
        List<long[]> forced = new ArrayList<>();
        for (int cx = ORIGIN >> 4; cx <= (ORIGIN + SIZE - 1) >> 4; cx++) {
            for (int cz = ORIGIN >> 4; cz <= (ORIGIN + SIZE - 1) >> 4; cz++) {
                SpikeRegistry.TICKETS.forceChunk(level, OWNER, cx, cz, true, true);
                forced.add(new long[] {cx, cz});
            }
        }
        int centre = ORIGIN + SIZE / 2;
        int floor = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centre, centre);
        SpikeArena arena = new SpikeArena(
                level, floor, StrategosCommands.spaceAround(level, new BlockPos(centre, floor, centre), SIZE, SIZE));
        arena.chunks.addAll(forced);
        return arena;
    }

    void close() {
        for (long[] c : chunks) {
            SpikeRegistry.TICKETS.forceChunk(level, OWNER, (int) c[0], (int) c[1], false, true);
        }
        chunks.clear();
    }

    Vec3 world(double localX, double localZ) {
        return new Vec3(ORIGIN + localX, floorY, ORIGIN + localZ);
    }

    BlockPos block(int localX, int localZ) {
        return new BlockPos(ORIGIN + localX, floorY, ORIGIN + localZ);
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
