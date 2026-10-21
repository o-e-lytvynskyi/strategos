package dev.strategos.core.testkit;

import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.MatchBounds;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The match invariants every harness checks after every step (QA-02.3): every unit inside the match space; WALKING
 * only with a target; at most {@link #MAX_ORDERS} orders; no WALKING unit without progress toward its target for more
 * than {@link #STUCK_TICKS} ticks; alive units within the unit cap. Later cards add theirs. One instance per harness:
 * it remembers each walking unit's best distance to its target.
 */
public final class Invariants {
    /** ADR-0014's queue limit. M0 has one order slot, so it bites only with ORD-02's queue. */
    public static final int MAX_ORDERS = 16;

    public static final int STUCK_TICKS = 400;

    // Per handle index: the walking unit tracked there (NONE when none), its target, its best squared distance to the
    // target so far and the tick it got there.
    private int[] tracked = new int[64];
    private double[] targetX = new double[64];
    private double[] targetZ = new double[64];
    private double[] best = new double[64];
    private long[] since = new long[64];

    /** The broken invariants at {@code tick}, one message each; empty when all hold. */
    public List<String> check(MatchQueries q, long tick) {
        List<String> problems = new ArrayList<>(0);
        int cap = q.config().rules().unitCap();
        if (q.unitCount() > cap) {
            problems.add("alive units " + q.unitCount() + " above the unit cap " + cap);
        }
        MatchBounds bounds = q.config().bounds();
        for (int handle : q.unitHandles()) {
            UnitView unit = q.unit(handle);
            String name = "unit " + Handle.toString(handle);
            if (unit == null) {
                problems.add(name + " is listed but not alive");
                continue;
            }
            if (!(bounds.contains(unit.x(), unit.z()) && unit.y() >= bounds.minY() && unit.y() <= bounds.maxY())) {
                problems.add(name + " at (" + unit.x() + ", " + unit.y() + ", " + unit.z()
                        + ") outside the match space " + bounds.sizeX() + " x " + bounds.sizeZ() + ", y "
                        + bounds.minY() + ".." + bounds.maxY());
            }
            Vec2d target = unit.target();
            int orders = target == null ? 0 : 1;
            if (orders > MAX_ORDERS) {
                problems.add(name + " has " + orders + " orders, more than " + MAX_ORDERS);
            }
            if (unit.state() == UnitState.WALKING && target == null) {
                problems.add(name + " is WALKING without a target");
            }
            String stuck = progress(handle, unit, target, tick);
            if (stuck != null) {
                problems.add(name + stuck);
            }
        }
        return problems;
    }

    /** Tracks a walking unit's distance to its target; a message when it made no progress for too long. */
    private @Nullable String progress(int handle, UnitView unit, @Nullable Vec2d target, long tick) {
        int i = Handle.index(handle);
        ensure(i);
        if (unit.state() != UnitState.WALKING || target == null) {
            tracked[i] = Handle.NONE;
            return null;
        }
        double dx = target.x() - unit.x();
        double dz = target.z() - unit.z();
        double distance = dx * dx + dz * dz;
        if (tracked[i] != handle
                || Double.compare(targetX[i], target.x()) != 0
                || Double.compare(targetZ[i], target.z()) != 0) {
            tracked[i] = handle; // a new walk: progress counts from here
            targetX[i] = target.x();
            targetZ[i] = target.z();
            best[i] = distance;
            since[i] = tick;
            return null;
        }
        if (distance < best[i]) {
            best[i] = distance;
            since[i] = tick;
            return null;
        }
        long stuckFor = tick - since[i];
        return stuckFor > STUCK_TICKS
                ? " is WALKING without progress toward (" + target.x() + ", " + target.z() + ") for " + stuckFor
                        + " ticks"
                : null;
    }

    private void ensure(int index) {
        if (index < tracked.length) {
            return;
        }
        int capacity = Math.max(index + 1, tracked.length * 2);
        tracked = Arrays.copyOf(tracked, capacity);
        targetX = Arrays.copyOf(targetX, capacity);
        targetZ = Arrays.copyOf(targetZ, capacity);
        best = Arrays.copyOf(best, capacity);
        since = Arrays.copyOf(since, capacity);
    }
}
