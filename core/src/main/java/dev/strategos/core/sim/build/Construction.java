package dev.strategos.core.sim.build;

import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.EntityStore;
import dev.strategos.core.sim.MatchBounds;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.MotionStore;
import dev.strategos.core.sim.WorldEffect;
import dev.strategos.core.sim.world.WritePriority;
import org.jspecify.annotations.Nullable;

/**
 * Raising a building (BLD-03, 04 §11.7): the first builder's arrival commits its foundation, so its columns block units
 * in the core's overlay, the units standing there are moved off, and the writer clears the blueprint's volume and fills
 * each column down to the ground. Then each tick its builders' work adds up, {@code r × n^0.7} for n builders of mean
 * rate r, in whole millionths of a tick, so n builders throughout finish in {@code ceil(T ÷ n^0.7)} ticks, T its
 * {@code Cost/time} in ticks; its progress, its hit points and the blocks the writer is asked for, in build order,
 * follow the work.
 */
@CoreApi
public final class Construction {
    /** Work in millionths of a tick at one builder's rate. */
    public static final long MICRO = 1_000_000;

    /** The exponent of the builders' diminishing returns (BLD-03). */
    public static final double EXPONENT = 0.7;

    /** The block the writer fills a column with, down from a floor layer to the ground under it. */
    public static final String FILL = "minecraft:dirt";

    /** The path of a builder's rate. */
    public static final String RATE = "Builder/rate";

    /** The path of a building's build time, in seconds for one builder. */
    public static final String TIME = "Cost/time";

    private Construction() {}

    /** The work building {@code handle} needs: its {@code Cost/time} in seconds as ticks, in millionths. */
    public static long needed(MatchState s, int handle) {
        BuildingStore b = s.buildings();
        int path = s.stats().path(TIME);
        double seconds = path < 0 ? Double.NaN : s.stats().blockValue(b.template(handle), b.faction(handle), path);
        double ticks = Double.isFinite(seconds) && seconds > 0 ? seconds * 20 : 1;
        return Math.max(1, Math.round(ticks * MICRO));
    }

    /** The work a tick adds with {@code builders} at work, their rates summing to {@code rates}. */
    public static long work(int builders, double rates) {
        if (builders <= 0 || rates <= 0) {
            return 0;
        }
        double mean = rates / builders;
        return Math.round(MICRO * mean * DetMath.pow(builders, EXPONENT));
    }

    /**
     * The foundation becomes a site (the first builder arrived): its columns block units, the units standing on them
     * are moved to the nearest free ground outside, and the writer clears its volume and fills its columns down to the
     * ground.
     */
    public static void commit(MatchState s, int handle) {
        BuildingStore buildings = s.buildings();
        if (buildings.state(handle) != BuildingStore.FOUNDATION) {
            return;
        }
        Blueprint b = s.blueprintOf(handle);
        Footprint at = s.footprintOf(handle);
        int baseY = buildings.baseY(handle);
        int site = Handle.index(handle);
        MatchBounds bounds = s.config().bounds();
        boolean obstructs = Placing.obstructs(s, buildings.template(handle), buildings.faction(handle));
        for (int cell : at.columns(b)) {
            int x = cell >>> 16;
            int z = cell & 0xFFFF;
            if (obstructs) {
                s.blockColumn(x, z); // a field's are walked on (ECO-09)
            }
            double ground = Placing.floor(s.surfaces(), bounds, x, z);
            int top = Double.isNaN(ground) ? baseY : (int) Math.round(ground) - 1; // the ground's top block
            for (int y = top + 1; y < baseY; y++) {
                s.outputs().add(new WorldEffect(site, WritePriority.CONSTRUCTION, x, y, z, FILL, handle));
            }
        }
        boolean[] filled = new boolean[b.sizeX() * b.sizeY() * b.sizeZ()];
        for (int k = 0; k < b.count(); k++) {
            filled[(b.x(k) * b.sizeY() + b.y(k)) * b.sizeZ() + b.z(k)] = true;
        }
        for (int x = 0; x < b.sizeX(); x++) {
            for (int z = 0; z < b.sizeZ(); z++) {
                if (!b.covers(x, z)) {
                    continue;
                }
                for (int y = 0; y < b.sizeY(); y++) {
                    if (!filled[(x * b.sizeY() + y) * b.sizeZ() + z]) {
                        s.outputs()
                                .add(new WorldEffect(
                                        site,
                                        WritePriority.CONSTRUCTION,
                                        at.worldX(x, z),
                                        baseY + y,
                                        at.worldZ(x, z),
                                        WorldEffect.AIR,
                                        0));
                    }
                }
            }
        }
        if (obstructs) {
            evict(s, at);
        }
        buildings.setState(handle, BuildingStore.CONSTRUCTING, buildings.progress(handle));
    }

    /** The units standing in the footprint go to the nearest free ground outside it, nearest first, the lower cell first. */
    private static void evict(MatchState s, Footprint at) {
        EntityStore entities = s.entities();
        MotionStore motion = s.motion();
        MatchBounds bounds = s.config().bounds();
        for (int i = 0; i < entities.highWater(); i++) {
            if (!entities.aliveAt(i)) {
                continue;
            }
            int cx = (int) Math.floor(motion.x(i));
            int cz = (int) Math.floor(motion.z(i));
            if (!inside(at, cx, cz)) {
                continue;
            }
            int[] cell = nearestFree(s, at, cx, cz);
            if (cell == null) {
                continue;
            }
            double x = cell[0] + 0.5;
            double z = cell[1] + 0.5;
            double floor = Placing.floor(s.surfaces(), bounds, cell[0], cell[1]);
            double y = Double.isNaN(floor) ? motion.y(i) : floor;
            motion.set(i, x, y, z, motion.yaw(i));
            s.frame().add(entities.handleAt(i), x, y, z, motion.yaw(i), (byte) 0);
            s.paths().setRepath(i, true);
        }
    }

    /**
     * The free cell outside footprint {@code at} nearest (cx, cz), ring by ring, the lower distance first: ground with
     * no footprint on it. Null when none lies within the footprint's size and 8 more.
     */
    public static int @Nullable [] nearestFree(MatchState s, Footprint at, int cx, int cz) {
        MatchBounds bounds = s.config().bounds();
        for (int r = 1; r <= Math.max(at.spanX(), at.spanZ()) + 8; r++) {
            int[] best = null;
            double bestD = Double.POSITIVE_INFINITY;
            for (int x = cx - r; x <= cx + r; x++) {
                for (int z = cz - r; z <= cz + r; z++) {
                    if (Math.max(Math.abs(x - cx), Math.abs(z - cz)) != r
                            || x < 0
                            || z < 0
                            || x >= bounds.sizeX()
                            || z >= bounds.sizeZ()
                            || inside(at, x, z)
                            || s.footprintAt(x, z)
                            || Double.isNaN(Placing.floor(s.surfaces(), bounds, x, z))) {
                        continue;
                    }
                    double d = (x - cx) * (double) (x - cx) + (z - cz) * (double) (z - cz);
                    if (d < bestD) {
                        bestD = d;
                        best = new int[] {x, z};
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    private static boolean inside(Footprint at, int x, int z) {
        return x >= at.anchorX() && x < at.anchorX() + at.spanX() && z >= at.anchorZ() && z < at.anchorZ() + at.spanZ();
    }

    /**
     * One tick of building {@code handle}: this tick's builders' work is added, the writer is asked for the blocks the
     * work now covers, in build order, and a site whose work is done stands finished.
     */
    public static void step(MatchState s, int handle) {
        BuildingStore buildings = s.buildings();
        long add = work(buildings.tickBuilders(handle), buildings.tickRates(handle));
        buildings.clearTick(handle);
        if (add == 0 || buildings.state(handle) != BuildingStore.CONSTRUCTING) {
            return;
        }
        long need = needed(s, handle);
        long done = Math.min(need, buildings.work(handle) + add);
        buildings.setWork(handle, done);
        Blueprint b = s.blueprintOf(handle);
        Footprint at = s.footprintOf(handle);
        int baseY = buildings.baseY(handle);
        int site = Handle.index(handle);
        int target = (int) Math.min(b.count(), done * b.count() / need);
        for (int k = buildings.placed(handle); k < target; k++) {
            s.outputs()
                    .add(new WorldEffect(
                            site,
                            WritePriority.CONSTRUCTION,
                            at.worldX(b.x(k), b.z(k)),
                            baseY + b.y(k),
                            at.worldZ(b.x(k), b.z(k)),
                            b.palette().get(b.state(k)),
                            handle));
        }
        buildings.setPlaced(handle, Math.max(buildings.placed(handle), target));
        long progress = Math.min(BuildingStore.FULL, done * BuildingStore.FULL / need);
        buildings.setState(
                handle,
                done >= need ? BuildingStore.COMPLETE : BuildingStore.CONSTRUCTING,
                done >= need ? BuildingStore.FULL : progress);
    }
}
