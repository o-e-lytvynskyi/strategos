package dev.strategos.core.sim.territory;

import dev.strategos.core.content.CorePaths;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.port.JobExecutor;
import dev.strategos.core.port.JobHandle;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.TerritoryGrid;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.MatchSystem;
import dev.strategos.core.sim.Phase;
import dev.strategos.core.sim.build.BuildingStore;
import java.util.Arrays;
import org.jspecify.annotations.Nullable;

/**
 * Territory in the {@code WORLD} phase (M9 spec §2.3, TERR-01). Each tick it gathers the sources — every complete
 * building whose template has a territory radius, at its faction's stats — and, when they differ from the last
 * recompute's and that one started {@value #CADENCE} or more ticks ago, submits a recompute as a pure job on a frozen
 * copy. The job is joined on the next tick, whatever thread ran it, so 0, 1 and 4 threads deliver the same grid on
 * the same tick and a slow recompute never blocks the tick that started it. The very first grid, the first with any
 * source (a match's first centres), and the grid after a restore are computed at once.
 *
 * <p>The grid is derived: the saved state is the two source sets (the delivered grid's and a pending job's), from
 * which a restore rebuilds both exactly ({@link #save()}, {@link #restore}).
 */
@CoreApi
public final class TerritorySystem implements MatchSystem {
    /** The fewest ticks between two recompute starts: the per-tick cap (04 line 742). */
    public static final int CADENCE = 20;

    private final JobExecutor jobs;
    private final int factions;
    private final TerritoryGrid grid;
    private final TerritoryKernel kernel = new TerritoryKernel(); // the tick thread's, for synchronous recomputes
    private TerritorySources delivered = new TerritorySources();
    private final TerritorySources gathered = new TerritorySources();
    private @Nullable TerritorySources pendingSources;
    private @Nullable JobHandle<TerritoryGrid> pending;
    private long due;
    private long lastStart = Long.MIN_VALUE / 2;
    private boolean ready;
    private long seenBuildings = -1;
    private long[] seenStats = new long[0];
    private long recomputes;

    public TerritorySystem(int sizeX, int sizeZ, int factions, JobExecutor jobs) {
        this.jobs = jobs;
        this.factions = factions;
        this.grid = TerritoryGrid.forBlocks(sizeX, sizeZ);
    }

    @Override
    public Phase phase() {
        return Phase.WORLD;
    }

    /** The delivered grid: what placement, the AI and the clients read. */
    public TerritoryGrid grid() {
        return grid;
    }

    /** How many recomputes were delivered (diagnostics and the cadence tests). */
    public long recomputes() {
        return recomputes;
    }

    @Override
    public void run(MatchState s) {
        long tick = s.tick();
        if (pending != null && tick >= due) {
            grid.copyFrom(pending.join());
            delivered = java.util.Objects.requireNonNull(pendingSources);
            pending = null;
            pendingSources = null;
            recomputes++;
        }
        if (!changed(s)) {
            return;
        }
        gather(s, gathered);
        TerritorySources latest = pendingSources != null ? pendingSources : delivered;
        if (ready && same(gathered, latest)) {
            return;
        }
        if (!ready
                || (delivered.size() == 0 && pending == null)) { // the first land: at once, so it is there to build on
            delivered = gathered.copy();
            kernel.compute(delivered, factions, grid);
            ready = true;
            lastStart = tick;
            recomputes++;
            return;
        }
        if (pending != null || tick - lastStart < CADENCE) {
            seenBuildings = -1; // look again next tick: the change is still waiting
            return;
        }
        TerritorySources frozen = gathered.copy();
        int tx = grid.tilesX();
        int tz = grid.tilesZ();
        int n = factions;
        pending = jobs.submit(scratch -> {
            TerritoryGrid out = new TerritoryGrid(tx, tz);
            scratch.get(TerritoryKernel.class, TerritoryKernel::new).compute(frozen, n, out);
            return out;
        });
        pendingSources = frozen;
        due = tick + 1;
        lastStart = tick;
    }

    private boolean changed(MatchState s) {
        boolean moved = s.buildings().changes() != seenBuildings;
        seenBuildings = s.buildings().changes();
        if (seenStats.length != factions) {
            seenStats = new long[factions];
            Arrays.fill(seenStats, -1);
        }
        for (int f = 0; f < factions; f++) {
            long v = s.stats().version(f);
            if (v != seenStats[f]) {
                seenStats[f] = v;
                moved = true;
            }
        }
        return moved || !ready;
    }

    /** The sources of match {@code s} now, in handle order. */
    static void gather(MatchState s, TerritorySources out) {
        out.clear();
        int radiusPath = s.stats().path(CorePaths.TERRITORY_RADIUS);
        if (radiusPath < 0) {
            return;
        }
        int weightPath = s.stats().path(CorePaths.TERRITORY_WEIGHT);
        int rootPath = s.stats().path(CorePaths.TERRITORY_ROOT);
        BuildingStore b = s.buildings();
        for (int handle : b.handles()) {
            int faction = b.faction(handle);
            if (faction < 0 || faction >= s.gaia() || b.state(handle) != BuildingStore.COMPLETE) {
                continue;
            }
            int template = b.template(handle);
            double radius = s.stats().blockValue(template, faction, radiusPath);
            if (Double.isNaN(radius) || radius < 1) {
                continue;
            }
            double weight = weightPath < 0 ? Double.NaN : s.stats().blockValue(template, faction, weightPath);
            double root = rootPath < 0 ? Double.NaN : s.stats().blockValue(template, faction, rootPath);
            Footprint f = s.footprintOf(handle);
            int minX = Math.min(f.worldX(0, 0), f.worldX(f.sizeX() - 1, f.sizeZ() - 1));
            int minZ = Math.min(f.worldZ(0, 0), f.worldZ(f.sizeX() - 1, f.sizeZ() - 1));
            out.add(
                    faction,
                    2 * minX + f.spanX(),
                    2 * minZ + f.spanZ(),
                    Double.isNaN(weight) ? 1 : (int) Math.round(weight),
                    (int) Math.round(radius),
                    !Double.isNaN(root) && root >= 1);
        }
    }

    /**
     * The saved state ({@code Play.territory}): empty before the first grid; else {@code [lastStart, due, pending ? 1 :
     * 0]}, the delivered sources, then the pending job's if any; a source set is its size, then six numbers a source.
     */
    public java.util.List<Long> save() {
        if (!ready) {
            return java.util.List.of();
        }
        java.util.List<Long> out = new java.util.ArrayList<>();
        out.add(lastStart);
        out.add(due);
        out.add(pendingSources != null ? 1L : 0L);
        put(out, delivered);
        if (pendingSources != null) {
            put(out, pendingSources);
        }
        return out;
    }

    /** Rebuilds the grid and any pending job from {@link #save()}'s list; an empty list leaves a fresh system. */
    public void restore(java.util.List<Long> saved) {
        pending = null;
        pendingSources = null;
        seenBuildings = -1;
        Arrays.fill(seenStats, -1);
        if (saved.isEmpty()) {
            ready = false;
            return;
        }
        int[] at = {3};
        lastStart = saved.get(0);
        due = saved.get(1);
        delivered = take(saved, at);
        kernel.compute(delivered, factions, grid);
        ready = true;
        recomputes = 1; // a delivered grid exists again; the clients' versions restart with the server
        if (saved.get(2) == 1L) {
            TerritorySources frozen = take(saved, at);
            TerritoryGrid out = new TerritoryGrid(grid.tilesX(), grid.tilesZ());
            kernel.compute(frozen, factions, out);
            pendingSources = frozen;
            pending =
                    new JobHandle<>() { // already computed: the restore is synchronous
                        @Override
                        public boolean isDone() {
                            return true;
                        }

                        @Override
                        public TerritoryGrid join() {
                            return out;
                        }
                    };
        }
    }

    private static void put(java.util.List<Long> out, TerritorySources s) {
        out.add((long) s.size());
        for (int i = 0; i < s.size(); i++) {
            out.add((long) s.faction(i));
            out.add((long) s.centreX2(i));
            out.add((long) s.centreZ2(i));
            out.add((long) s.weight(i));
            out.add((long) s.radius(i));
            out.add(s.root(i) ? 1L : 0L);
        }
    }

    private static TerritorySources take(java.util.List<Long> in, int[] at) {
        TerritorySources s = new TerritorySources();
        int n = (int) (long) in.get(at[0]++);
        for (int i = 0; i < n; i++) {
            int k = at[0];
            s.add(
                    (int) (long) in.get(k),
                    (int) (long) in.get(k + 1),
                    (int) (long) in.get(k + 2),
                    (int) (long) in.get(k + 3),
                    (int) (long) in.get(k + 4),
                    in.get(k + 5) == 1L);
            at[0] += 6;
        }
        return s;
    }

    static boolean same(TerritorySources a, TerritorySources b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (a.faction(i) != b.faction(i)
                    || a.centreX2(i) != b.centreX2(i)
                    || a.centreZ2(i) != b.centreZ2(i)
                    || a.weight(i) != b.weight(i)
                    || a.radius(i) != b.radius(i)
                    || a.root(i) != b.root(i)) {
                return false;
            }
        }
        return true;
    }
}
