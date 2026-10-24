package dev.strategos.core.sim.build;

import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.CorePaths;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.WorldEffect;
import dev.strategos.core.sim.nav.BlockClass;
import dev.strategos.core.sim.prod.Production;
import dev.strategos.core.sim.world.WritePriority;

/**
 * A building's life in the core that touches the rest of the match: its hit points, its instant placement and its
 * fall. Split out of {@link MatchState} in M4 T1, which keeps the calls.
 */
public final class BuildingLife {
    private BuildingLife() {}

    /**
     * Building {@code handle}'s most hit points: {@code Health/max} through its faction's modifiers, rising with its
     * construction from a thousandth of it at the foundation (BLD-03).
     */
    public static double maxHp(MatchState s, int handle) {
        int path = s.stats().path(CorePaths.HEALTH);
        double v = path < 0
                ? Double.NaN
                : s.stats()
                        .blockValue(
                                s.buildings().template(handle), s.buildings().faction(handle), path);
        double full = Double.isFinite(v) && v > 0 ? v : CorePaths.DEFAULT_HEALTH;
        if (s.buildings().state(handle) == BuildingStore.COMPLETE) {
            return full;
        }
        return full * Math.max(1, s.buildings().progress(handle)) / BuildingStore.FULL;
    }

    /**
     * A finished building at once (BLD-01's instant placement, for scenarios and tests): its footprint's columns are
     * blocked for units, and the writer is asked to clear its volume and place its blocks in build order. Its handle.
     */
    public static int placeComplete(MatchState s, int template, int faction, Footprint at, int baseY) {
        int handle = s.buildings().create(template, faction, at, baseY, BuildingStore.COMPLETE, BuildingStore.FULL);
        Blueprint b = s.blueprintOf(handle);
        if (Placing.obstructs(s, template, faction)) { // a field does not (ECO-09)
            for (int cell : at.columns(b)) {
                s.blockColumn(cell >>> 16, cell & 0xFFFF);
            }
        }
        writeBlueprint(s, Handle.index(handle), handle, b, at, baseY);
        Production.finished(s, handle); // its housing counts (ECO-06)
        return handle;
    }

    /**
     * Building {@code handle} falls (BLD-01: 0 hit points): its blocks above its floor layer go, the floor stays as a
     * foundation; its columns are released, and the core's terrain gets the air the writer will leave.
     */
    public static void destroy(MatchState s, int handle) {
        Production.lost(s, handle); // its housing and its queue (ECO-06, PROD-01)
        Blueprint b = s.blueprintOf(handle);
        Footprint at = s.footprintOf(handle);
        int baseY = s.buildings().baseY(handle);
        int site = Handle.index(handle);
        for (int k = b.count() - 1; k >= 0; k--) { // top-down, the reverse of the build order
            if (b.y(k) == 0) {
                continue;
            }
            int x = at.worldX(b.x(k), b.z(k));
            int z = at.worldZ(b.x(k), b.z(k));
            s.outputs()
                    .add(new WorldEffect(site, WritePriority.DAMAGE_STAGE, x, baseY + b.y(k), z, WorldEffect.AIR, 0));
            s.editVoxel(x, baseY + b.y(k), z, BlockClass.AIR);
        }
        if (Placing.obstructs(s, s.buildings().template(handle), s.buildings().faction(handle))) {
            for (int cell : at.columns(b)) {
                s.releaseColumn(cell >>> 16, cell & 0xFFFF);
            }
        }
        s.buildings().remove(handle);
    }

    /**
     * What a building {@code progress} thousandths built gives back of its {@code cost} when cancelled (BLD-09): {@code
     * cost × (1000 − progress) ÷ 1000} per resource, rounded down to the thousandth (the balance sheet's rule).
     */
    public static long[] refund(long[] cost, long progress) {
        long left = BuildingStore.FULL - Math.max(0, Math.min(BuildingStore.FULL, progress));
        long[] out = new long[cost.length];
        for (int r = 0; r < cost.length; r++) {
            out[r] = Math.multiplyExact(cost[r], left) / BuildingStore.FULL;
        }
        return out;
    }

    /**
     * Asks the writer for a finished blueprint at {@code at}, its lowest layer at {@code baseY}: the box's empty cells
     * cleared, then its blocks in build order, under write site {@code site}, owned by {@code owner} (a building's
     * handle, or 0 for none).
     */
    public static void writeBlueprint(MatchState s, int site, int owner, Blueprint b, Footprint at, int baseY) {
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
        for (int k = 0; k < b.count(); k++) {
            s.outputs()
                    .add(new WorldEffect(
                            site,
                            WritePriority.CONSTRUCTION,
                            at.worldX(b.x(k), b.z(k)),
                            baseY + b.y(k),
                            at.worldZ(b.x(k), b.z(k)),
                            b.palette().get(b.state(k)),
                            owner));
        }
    }
}
