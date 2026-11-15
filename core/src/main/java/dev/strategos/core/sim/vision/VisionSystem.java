package dev.strategos.core.sim.vision;

import dev.strategos.core.content.CorePaths;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.Hasher;
import dev.strategos.core.sim.EntityStore;
import dev.strategos.core.sim.IdentityStore;
import dev.strategos.core.sim.MatchConfig;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.MatchSystem;
import dev.strategos.core.sim.MotionStore;
import dev.strategos.core.sim.Phase;
import dev.strategos.core.sim.build.BuildingStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Fog of war's vision (FOW-01, MATCH-07): each tick a unit's or building's disc is moved on its faction's grid, the
 * explored bits rise, and each faction's visibility index is brought up to date. A faction beyond the player factions
 * (Gaia) sees nothing and adds nothing. With {@code explored} every cell starts explored; with {@code revealMap} every
 * cell counts as seen.
 */
@CoreApi
public final class VisionSystem implements MatchSystem {
    private static final int UNSET = -1;

    private final VisionGrid[] grids;
    private final VisibilityIndex[] indices;
    private int[] lastCellX = new int[0];
    private int[] lastCellZ = new int[0];
    private int[] lastRadius = new int[0];
    private int[] lastFaction = new int[0];
    private final Map<Integer, int[]> stampedBuildings = new TreeMap<>(); // handle -> faction, cell x, cell z, radius
    private boolean revealed;
    private long seenBuildings = -1;
    private long[] seenStats = new long[0];
    private boolean allExplored;

    public VisionSystem(MatchConfig config) {
        int factions = config.factions();
        grids = new VisionGrid[factions];
        indices = new VisibilityIndex[factions];
        for (int f = 0; f < factions; f++) {
            grids[f] = new VisionGrid(config.bounds().sizeX(), config.bounds().sizeZ());
            indices[f] = new VisibilityIndex();
        }
    }

    @Override
    public Phase phase() {
        return Phase.WORLD;
    }

    public VisionGrid grid(int faction) {
        return grids[faction];
    }

    /** Whether cell ({@code x}, {@code z}) is explored by {@code faction}; true when no cell was ever explored. */
    public boolean isExplored(int faction, int x, int z) {
        VisionGrid g = grids[faction];
        return g.pristine() || g.isExplored(x, z);
    }

    public VisibilityIndex visibility(int faction) {
        return indices[faction];
    }

    @Override
    public void run(MatchState state) {
        var play = state.config().rules().play();
        if (play.explored() && !allExplored) {
            for (VisionGrid g : grids) {
                g.exploreAll();
            }
            allExplored = true;
        }
        if (play.revealMap()) {
            if (!revealed) {
                resetStamps();
                for (VisionGrid g : grids) {
                    g.revealAll();
                }
                revealed = true;
                allExplored = true;
            }
        } else {
            if (revealed) {
                resetStamps();
                revealed = false;
            }
            stampUnits(state); // a stamp marks its cells explored
            stampBuildings(state);
        }
        updateIndices(state);
    }

    /** Clears the counts and stamps every viewer again from where it stands, after a restore. */
    public void rebuild(MatchState state) {
        resetStamps();
        revealed = false;
        allExplored = false;
        run(state);
    }

    private void resetStamps() {
        for (VisionGrid g : grids) {
            g.clearCounts();
        }
        Arrays.fill(lastCellX, UNSET);
        stampedBuildings.clear();
        seenBuildings = -1;
    }

    private void updateIndices(MatchState state) {
        for (int f = 0; f < indices.length; f++) {
            indices[f].update(f, state.tick(), state.entities(), state.identity(), state.motion(), grids[f]);
        }
    }

    private void stampUnits(MatchState state) {
        EntityStore entities = state.entities();
        IdentityStore identity = state.identity();
        MotionStore motion = state.motion();
        int high = entities.highWater();
        if (lastCellX.length < high) {
            int old = lastCellX.length;
            int n = Math.max(high, old * 2);
            lastCellX = Arrays.copyOf(lastCellX, n);
            lastCellZ = Arrays.copyOf(lastCellZ, n);
            lastRadius = Arrays.copyOf(lastRadius, n);
            lastFaction = Arrays.copyOf(lastFaction, n);
            Arrays.fill(lastCellX, old, n, UNSET);
        }
        for (int i = 0; i < high; i++) {
            boolean alive = entities.aliveAt(i);
            int faction = alive ? identity.faction(i) : UNSET;
            if (lastCellX[i] != UNSET && (!alive || faction != lastFaction[i])) {
                grids[lastFaction[i]].unstamp(lastCellX[i], lastCellZ[i], lastRadius[i]);
                lastCellX[i] = UNSET;
            }
            if (!alive || faction < 0 || faction >= grids.length) {
                continue;
            }
            int cx = VisionGrid.cellOf(motion.x(i));
            int cz = VisionGrid.cellOf(motion.z(i));
            int radius = (int) (state.stats().vision(i) / DiscOffsets.CELL);
            if (lastCellX[i] != UNSET) {
                if (lastCellX[i] == cx && lastCellZ[i] == cz && lastRadius[i] == radius) {
                    continue;
                }
                grids[faction].unstamp(lastCellX[i], lastCellZ[i], lastRadius[i]);
            }
            grids[faction].stamp(cx, cz, radius);
            lastCellX[i] = cx;
            lastCellZ[i] = cz;
            lastRadius[i] = radius;
            lastFaction[i] = faction;
        }
    }

    private void stampBuildings(MatchState state) {
        BuildingStore buildings = state.buildings();
        if (buildings.empty() && stampedBuildings.isEmpty()) {
            return;
        }
        if (!buildingsMoved(state)) { // nothing placed, lost, finished or modified: the stamps stand
            return;
        }
        for (Iterator<Map.Entry<Integer, int[]>> it =
                        stampedBuildings.entrySet().iterator();
                it.hasNext(); ) {
            Map.Entry<Integer, int[]> e = it.next();
            if (!buildings.alive(e.getKey())) {
                int[] s = e.getValue();
                grids[s[0]].unstamp(s[1], s[2], s[3]);
                it.remove();
            }
        }
        int visionPath = state.stats().path(CorePaths.VISION);
        for (int handle : buildings.handles()) {
            int faction = buildings.faction(handle);
            if (faction < 0 || faction >= grids.length || buildings.state(handle) < BuildingStore.CONSTRUCTING) {
                continue;
            }
            double range = CorePaths.DEFAULT_VISION;
            if (visionPath >= 0) { // the faction's value: a phase's +2 vision reaches its buildings (TECH-02)
                double v = state.stats().blockValue(buildings.template(handle), faction, visionPath);
                if (!Double.isNaN(v)) {
                    range = v;
                }
            }
            int[] s = {
                faction,
                VisionGrid.cellOf(buildings.anchorX(handle)),
                VisionGrid.cellOf(buildings.anchorZ(handle)),
                (int) (range / DiscOffsets.CELL)
            };
            int[] was = stampedBuildings.get(handle);
            if (was != null) {
                if (Arrays.equals(was, s)) {
                    continue;
                }
                grids[was[0]].unstamp(was[1], was[2], was[3]);
            }
            grids[faction].stamp(s[1], s[2], s[3]);
            stampedBuildings.put(handle, s);
        }
    }

    /** Whether a building was placed, lost, finished or changed state, or a faction's modifiers moved, since the last look. */
    private boolean buildingsMoved(MatchState state) {
        boolean moved = state.buildings().changes() != seenBuildings;
        seenBuildings = state.buildings().changes();
        if (seenStats.length != grids.length) {
            seenStats = new long[grids.length];
            Arrays.fill(seenStats, -1);
        }
        for (int f = 0; f < grids.length; f++) {
            long v = state.stats().version(f);
            if (v != seenStats[f]) {
                seenStats[f] = v;
                moved = true;
            }
        }
        return moved;
    }

    private boolean pristine() {
        for (int f = 0; f < grids.length; f++) {
            if (!grids[f].pristine() || !indices[f].pristine()) {
                return false;
            }
        }
        return true;
    }

    /** Factions, then per faction its explored words and its hysteresis pairs, each with a length; none when pristine. */
    public List<Long> save() {
        if (pristine()) {
            return List.of();
        }
        List<Long> out = new ArrayList<>();
        out.add((long) grids.length);
        for (int f = 0; f < grids.length; f++) {
            List<Long> explored = grids[f].saveExplored();
            out.add((long) explored.size());
            out.addAll(explored);
            List<Long> pairs = indices[f].saveHysteresis();
            out.add((long) (pairs.size() / 2));
            out.addAll(pairs);
        }
        return out;
    }

    public void restore(List<Long> data) {
        if (data.isEmpty()) {
            return;
        }
        int at = 0;
        if (data.get(at++) != grids.length) {
            throw new IllegalArgumentException(
                    "a saved vision of " + data.get(0) + " factions for a match of " + grids.length);
        }
        for (int f = 0; f < grids.length; f++) {
            int words = Math.toIntExact(data.get(at++));
            grids[f].restoreExplored(data.subList(at, at + words));
            at += words;
            int pairs = Math.toIntExact(data.get(at++));
            indices[f].restoreHysteresis(data.subList(at, at + 2 * pairs));
            at += 2 * pairs;
        }
    }

    /** Hashes the explored cells and the hysteresis; nothing while pristine, so older states keep their hashes. */
    public long mix(long h) {
        if (pristine()) {
            return h;
        }
        for (int f = 0; f < grids.length; f++) {
            h = grids[f].mixExplored(h);
            h = indices[f].mixHysteresis(h);
            h = Hasher.mix(h, f);
        }
        return h;
    }
}
