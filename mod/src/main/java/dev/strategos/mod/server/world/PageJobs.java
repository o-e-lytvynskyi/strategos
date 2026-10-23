package dev.strategos.mod.server.world;

import dev.strategos.core.port.JobExecutor;
import dev.strategos.core.port.JobHandle;
import dev.strategos.core.sim.TerrainPage;
import dev.strategos.core.sim.nav.PageCoord;
import dev.strategos.core.sim.nav.VoxelSlice;
import dev.strategos.mod.server.match.MatchSpace;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Keeps a match's terrain pages in step with its level (04 §9.2, PATH-15). A block change marks its page dirty; after
 * each step, at most {@link #PER_TICK} pages that have been quiet for {@link #QUIET} ticks, or dirty for {@link
 * #MAX_WAIT}, oldest first, are captured on the main thread and built as jobs due the rule {@code pageK} ticks later;
 * a due job is joined and enters that tick's inputs as a {@code TerrainPage}. Until then units keep the stale page. At
 * prepare and at resume every page is captured and joined for the next tick. Server thread only.
 */
public final class PageJobs {
    public static final int QUIET = 4;
    public static final int MAX_WAIT = 40;
    public static final int PER_TICK = 4;
    private static final long CLEAN = Long.MIN_VALUE;

    private final MatchSpace space;
    private final int delay;
    private final int pagesX;
    private final int pagesZ;
    private final long[] firstDirty;
    private final long[] lastChange;
    private final List<InFlight> inFlight = new ArrayList<>();
    private int dirty;
    private long captured;
    private long lastCaptureNanos;
    private long lastJoinNanos;
    private int lastCaptured;

    /** A page job and the tick it is due. */
    private record InFlight(int page, long due, JobHandle<TerrainPage> job) {}

    /** A match's pages, rebuilt jobs due {@code delay} ticks (the match's {@code pageK}) after their capture. */
    public PageJobs(MatchSpace space, int delay) {
        this.space = space;
        this.delay = delay;
        this.pagesX = (space.sizeX() + VoxelSlice.SIDE - 1) / VoxelSlice.SIDE;
        this.pagesZ = (space.sizeZ() + VoxelSlice.SIDE - 1) / VoxelSlice.SIDE;
        this.firstDirty = new long[pagesX * pagesZ];
        this.lastChange = new long[pagesX * pagesZ];
        Arrays.fill(firstDirty, CLEAN);
        Arrays.fill(lastChange, CLEAN);
    }

    /** A block of the space changed its class at match tick {@code tick}. */
    public void changed(BlockPos pos, long tick) {
        int x = pos.getX() - space.origin().getX();
        int z = pos.getZ() - space.origin().getZ();
        if (x < 0 || z < 0 || x >= space.sizeX() || z >= space.sizeZ()) {
            return;
        }
        int page = (z / VoxelSlice.SIDE) * pagesX + x / VoxelSlice.SIDE;
        if (firstDirty[page] == CLEAN) {
            firstDirty[page] = tick;
            dirty++;
        }
        lastChange[page] = tick;
    }

    /**
     * After the step of tick {@code tick}: captures the pages that are due, at most {@link #PER_TICK}, oldest first,
     * each with the writes match {@code match} still waits to make there laid over it, sampled at {@code tick}.
     */
    public void tick(
            ServerLevel level, long tick, BlockClassTable table, JobExecutor jobs, BlockWriter writer, int match) {
        long start = System.nanoTime();
        lastCaptured = 0;
        try {
            captureDue(level, tick, table, jobs, writer, match);
        } finally {
            lastCaptureNanos = System.nanoTime() - start;
        }
    }

    private void captureDue(
            ServerLevel level, long tick, BlockClassTable table, JobExecutor jobs, BlockWriter writer, int match) {
        for (int n = 0; n < PER_TICK && dirty > 0; n++) {
            int page = -1;
            for (int i = 0; i < firstDirty.length; i++) {
                boolean ripe =
                        firstDirty[i] != CLEAN && (tick - lastChange[i] >= QUIET || tick - firstDirty[i] >= MAX_WAIT);
                if (ripe && (page < 0 || firstDirty[i] < firstDirty[page])) {
                    page = i;
                }
            }
            if (page < 0) {
                return;
            }
            firstDirty[page] = CLEAN;
            lastChange[page] = CLEAN;
            dirty--;
            lastCaptured++;
            PageCoord coord = new PageCoord(page % pagesX, page / pagesX);
            int x0 = space.origin().getX() + coord.x() * VoxelSlice.SIDE;
            int z0 = space.origin().getZ() + coord.z() * VoxelSlice.SIDE;
            Map<BlockPos, BlockState> pending = writer.pendingIn(
                    level,
                    match,
                    new BlockPos(x0, space.minY(), z0),
                    new BlockPos(x0 + VoxelSlice.SIDE - 1, space.maxY(), z0 + VoxelSlice.SIDE - 1));
            inFlight.add(new InFlight(
                    page, tick + delay, jobs.submit(SliceCapture.capture(level, space, coord, table, pending, tick))));
            captured++;
        }
    }

    /**
     * Every page, captured now and due at {@code due}: a new match's terrain, or a resumed one's. Their sample tick is
     * unknown, so every edit the core keeps still applies over them: a restart may have lost writes that were waiting.
     */
    public void prewarm(ServerLevel level, long due, BlockClassTable table, JobExecutor jobs) {
        for (int page = 0; page < pagesX * pagesZ; page++) {
            submit(level, page, due, table, jobs);
        }
    }

    /** The pages due at {@code tick}, joined, in page order; they leave the queue. */
    public List<TerrainPage> due(long tick) {
        long start = System.nanoTime();
        List<InFlight> ready = new ArrayList<>();
        Iterator<InFlight> it = inFlight.iterator();
        while (it.hasNext()) {
            InFlight f = it.next();
            if (f.due() <= tick) {
                ready.add(f);
                it.remove();
            }
        }
        ready.sort((a, b) -> Integer.compare(a.page(), b.page()));
        List<TerrainPage> pages = new ArrayList<>(ready.size());
        for (InFlight f : ready) {
            pages.add(f.job().join());
        }
        lastJoinNanos = System.nanoTime() - start;
        return pages;
    }

    /** The main thread's last capture: how long it took, and how many pages it took. */
    public long lastCaptureNanos() {
        return lastCaptureNanos;
    }

    public int lastCaptured() {
        return lastCaptured;
    }

    /** The main thread's last join of due jobs (with the inline executor, their builds too). */
    public long lastJoinNanos() {
        return lastJoinNanos;
    }

    /** Pages marked dirty and not yet captured. */
    public int dirtyPages() {
        return dirty;
    }

    /** Pages captured and not yet delivered. */
    public int inFlight() {
        return inFlight.size();
    }

    /** Pages captured since the match was made. */
    public long captured() {
        return captured;
    }

    private void submit(ServerLevel level, int page, long due, BlockClassTable table, JobExecutor jobs) {
        PageCoord coord = new PageCoord(page % pagesX, page / pagesX);
        inFlight.add(new InFlight(page, due, jobs.submit(SliceCapture.capture(level, space, coord, table))));
        captured++;
    }
}
