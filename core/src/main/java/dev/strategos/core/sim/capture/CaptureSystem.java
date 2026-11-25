package dev.strategos.core.sim.capture;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.MatchSystem;
import dev.strategos.core.sim.Phase;
import dev.strategos.core.sim.build.BuildingLife;
import dev.strategos.core.sim.build.BuildingStore;

/**
 * Capture in the {@code COMBAT} phase (CMB-10, BLD-10's regeneration; M9 spec §2.6–2.7). Capturers add their strength
 * each tick ({@link CaptureStore#contribute}); once a second, at each building's own offset ({@code tick % 20 ==
 * index % 20}), the building's second is applied by {@link CaptureMaths}: its owner's regeneration is {@code
 * Capturable/regen} plus each occupant's {@code Capture/strength × Capturable/garrisonRegen}. A building whose owner's
 * points reach zero changes hands ({@link BuildingLife#transfer}) and its new owner holds every point.
 */
@CoreApi
public final class CaptureSystem implements MatchSystem {
    public static final String POINTS = "Capturable/points";
    public static final String REGEN = "Capturable/regen";
    public static final String GARRISON_REGEN = "Capturable/garrisonRegen";
    public static final String STRENGTH = "Capture/strength";
    /** Ticks in a capture second. */
    public static final int SECOND = 20;

    @Override
    public Phase phase() {
        return Phase.COMBAT;
    }

    /** Building {@code handle}'s total points in thousandths, 0 when it cannot be captured. */
    public static long total(MatchState s, int handle) {
        return thousandths(s, handle, POINTS);
    }

    /** Unit {@code index}'s capture strength in thousandths a second, 0 for none. */
    public static long strength(MatchState s, int index) {
        int p = s.stats().path(STRENGTH);
        double v = p < 0 ? Double.NaN : s.stats().read(index, p);
        return Double.isFinite(v) && v > 0 ? Math.round(v * 1_000) : 0;
    }

    @Override
    public void run(MatchState s) {
        long start = s.systemTimes().start();
        step(s);
        s.systemTimes().capture(start);
    }

    private void step(MatchState s) {
        CaptureStore store = s.capture();
        if (store.empty()) {
            return;
        }
        BuildingStore b = s.buildings();
        for (int handle : store.active()) {
            if (!b.alive(handle)) {
                store.forget(handle);
                continue;
            }
            if (s.tick() % SECOND != Handle.index(handle) % SECOND) {
                continue;
            }
            long total = total(s, handle);
            int owner = b.faction(handle);
            long[] perTick = store.takeStrength(handle);
            if (total <= 0 || owner < 0 || owner >= store.factions()) {
                store.forget(handle);
                continue;
            }
            double max = BuildingLife.maxHp(s, handle);
            double health = max - b.damage(handle);
            long[] effect = new long[perTick.length];
            boolean attacked = false;
            for (int f = 0; f < perTick.length; f++) {
                effect[f] = CaptureMaths.effect(perTick[f] / SECOND, health, max);
                attacked |= effect[f] > 0;
            }
            long[] points = store.points(handle);
            if (points == null) {
                if (!attacked) {
                    continue;
                }
                points = store.open(handle, owner, total);
            }
            int now = CaptureMaths.second(points, owner, effect, regen(s, handle, owner), s.allies());
            if (now != owner) {
                BuildingLife.transfer(s, handle, now);
                store.close(handle); // the new owner holds every point
            } else if (points[owner] >= total) {
                store.close(handle);
            }
        }
    }

    /** The owner's regeneration of building {@code handle} a second, in thousandths, its garrison's included. */
    public static long regen(MatchState s, int handle, int owner) {
        long r = thousandths(s, handle, REGEN);
        int gp = s.stats().path(GARRISON_REGEN);
        double rate = gp < 0 ? Double.NaN : s.stats().blockValue(s.buildings().template(handle), owner, gp);
        if (Double.isFinite(rate) && rate > 0 && !s.garrison().empty()) {
            for (int unit : s.garrison().occupants(handle)) {
                r += Math.round(strength(s, Handle.index(unit)) * rate);
            }
        }
        return r;
    }

    private static long thousandths(MatchState s, int handle, String path) {
        int p = s.stats().path(path);
        if (p < 0) {
            return 0;
        }
        double v = s.stats()
                .blockValue(s.buildings().template(handle), s.buildings().faction(handle), p);
        return Double.isFinite(v) && v > 0 ? Math.round(v * 1_000) : 0;
    }
}
