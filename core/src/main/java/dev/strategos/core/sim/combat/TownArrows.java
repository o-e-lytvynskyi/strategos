package dev.strategos.core.sim.combat;

import dev.strategos.core.content.CorePaths;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.MatchSystem;
import dev.strategos.core.sim.Phase;
import dev.strategos.core.sim.ShotRequested;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.spatial.UnitFilter;
import java.util.List;
import java.util.function.IntConsumer;
import org.jspecify.annotations.Nullable;

/**
 * A finished building's town arrows (CMB-07): every {@code Attack/Building/interval} ticks a volley of {@code arrows}
 * plus {@code perGarrisoned} for each unit inside, at the enemy units within {@code range} of the footprint's middle.
 * The volley is chosen in UNIT_AI like any unit's firing intent and released by {@link ShotSystem} in COMBAT, so each
 * arrow is a CMB-04 shot with its lead, spread, landing and damage; only its origin differs, the footprint's middle at
 * its top. The targets are enemy units alone (never buildings, own units, or Gaia's animals), taken by preferred class
 * and then distance, the lower index on a tie; arrow {@code k} goes to the {@code k mod n}th of the {@code n} found.
 *
 * <p>No state of its own: a building's volleys fall on the ticks where {@code tick + slot} is a multiple of its
 * interval, the slot staggering several centres, so nothing needs saving or hashing.
 */
public final class TownArrows implements MatchSystem {
    /** The most arrows one volley carries, whatever the garrison. */
    static final int MAX_ARROWS = 64;

    private final int[] pick = new int[MAX_ARROWS];
    private final int[] pickRank = new int[MAX_ARROWS];
    private final double[] pickDistance = new double[MAX_ARROWS];
    private int picked;
    private int capacity;
    private double centreX;
    private double centreZ;
    private double range;
    private List<String> preferred = List.of();
    private @Nullable MatchState state;
    private final IntConsumer candidate = this::candidate;

    @Override
    public Phase phase() {
        return Phase.UNIT_AI;
    }

    @Override
    public void run(MatchState s) {
        long start = s.combatTimes().start();
        volleys(s);
        s.combatTimes().volleys(start);
    }

    private void volleys(MatchState s) {
        if (s.buildings().empty()) return;
        int arrowsPath = s.stats().path(CorePaths.BUILDING_ARROWS);
        int intervalPath = s.stats().path(CorePaths.BUILDING_INTERVAL);
        if (arrowsPath < 0 || intervalPath < 0) return;
        int perPath = s.stats().path(CorePaths.BUILDING_PER_GARRISONED);
        int piercePath = s.stats().path(CorePaths.BUILDING_PIERCE);
        int rangePath = s.stats().path(CorePaths.BUILDING_RANGE);
        int speedPath = s.stats().path(CorePaths.BUILDING_SPEED);
        int spreadPath = s.stats().path(CorePaths.BUILDING_SPREAD);
        int preferredPath = s.stats().tokenPath(CorePaths.BUILDING_PREFERRED);
        state = s;
        for (int handle : s.buildings().handles()) {
            BuildingStore buildings = s.buildings();
            if (buildings.state(handle) != BuildingStore.COMPLETE) continue;
            int template = buildings.template(handle);
            int faction = buildings.faction(handle);
            double base = value(s, template, faction, arrowsPath);
            long interval = Math.round(value(s, template, faction, intervalPath));
            if (!(base > 0) || interval < 1) continue;
            long slot = (long) Handle.index(handle) - BuildingStore.BASE;
            if (Math.floorMod(s.tick() + slot, interval) != 0) continue;
            double perGarrisoned = perPath < 0 ? 0 : value(s, template, faction, perPath);
            double pierce = piercePath < 0 ? 0 : value(s, template, faction, piercePath);
            range = rangePath < 0 ? 0 : value(s, template, faction, rangePath);
            if (!(pierce > 0) || !(range > 0)) continue;
            int count = (int) Math.min(MAX_ARROWS, base + perGarrisoned * garrisoned(s, handle));
            double speed = speedPath < 0 ? Double.NaN : s.stats().blockValue(template, faction, speedPath);
            double spread = spreadPath < 0 ? Double.NaN : s.stats().blockValue(template, faction, spreadPath);
            List<String> classes = preferredPath < 0 ? null : s.stats().blockTokens(template, faction, preferredPath);
            preferred = classes == null ? List.of() : classes;
            Footprint at = s.footprintOf(handle);
            centreX = at.anchorX() + at.spanX() / 2.0;
            centreZ = at.anchorZ() + at.spanZ() / 2.0;
            capacity = count;
            picked = 0;
            s.spatial()
                    .forEachInRadius(
                            centreX,
                            centreZ,
                            range,
                            UnitFilter.enemiesOf(faction, s.config().factions()),
                            candidate);
            for (int k = 0; k < count && picked > 0; k++) {
                int target = s.entities().handleAt(pick[k % picked]);
                Damage damage = new Damage(target, handle, 0, (float) pierce, 0, 0, List.of(), false);
                s.bus()
                        .publish(new ShotRequested(
                                handle,
                                target,
                                damage,
                                range,
                                Double.isFinite(speed) ? speed : CorePaths.DEFAULT_RANGED_SPEED,
                                Double.isFinite(spread) ? spread : CorePaths.DEFAULT_RANGED_SPREAD));
            }
        }
        state = null;
    }

    /** The units inside building {@code handle} (BLD-16): each adds {@code perGarrisoned} arrows. */
    static int garrisoned(MatchState s, int handle) {
        return s.garrison().count(handle);
    }

    private static double value(MatchState s, int template, int faction, int path) {
        double v = s.stats().blockValue(template, faction, path);
        return Double.isFinite(v) ? v : 0;
    }

    /** Keeps the best {@code capacity} enemies in order of preferred class, then distance, then index. */
    private void candidate(int index) {
        MatchState s = state;
        if (s == null || !s.entities().aliveAt(index) || s.garrisoned(index)) return; // the grid may be a step old
        double d = DetMath.length(s.motion().x(index) - centreX, s.motion().z(index) - centreZ);
        if (d > range) return;
        int rank = 0;
        if (!preferred.isEmpty()) {
            rank = preferred.size();
            List<String> classes = s.stats().classes(index);
            for (int k = 0; k < preferred.size(); k++) {
                if (classes.contains(preferred.get(k))) {
                    rank = k;
                    break;
                }
            }
        }
        int at = picked;
        while (at > 0
                && (pickRank[at - 1] > rank
                        || (pickRank[at - 1] == rank
                                && (pickDistance[at - 1] > d
                                        || (pickDistance[at - 1] == d && pick[at - 1] > index))))) {
            at--;
        }
        if (at >= capacity) return;
        int last = Math.min(picked, capacity - 1);
        System.arraycopy(pick, at, pick, at + 1, last - at);
        System.arraycopy(pickRank, at, pickRank, at + 1, last - at);
        System.arraycopy(pickDistance, at, pickDistance, at + 1, last - at);
        pick[at] = index;
        pickRank[at] = rank;
        pickDistance[at] = d;
        picked = Math.min(capacity, picked + 1);
    }
}
