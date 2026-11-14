package dev.strategos.core.sim.ai;

import dev.strategos.core.content.Footprint;
import dev.strategos.core.content.StartSlot;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.build.BuildingView;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Where a computer player's base is in a think: its first finished civic centre's middle, the nearest enemy civic
 * centre (or the enemy start when none is visible, AI-18), and which way its sites mirror (the second start slot of a
 * point-mirrored map turns them round).
 */
record Base(
        BuildingView centre,
        double x,
        double z,
        @Nullable BuildingView enemy,
        int mirror,
        double aimX,
        double aimZ,
        boolean fallen) {
    static final String CENTRE = "CivicCentre";

    /** The base in {@code view}, or null while the player holds no finished centre. */
    static @Nullable Base of(AiView view) {
        BuildingView own = null;
        for (BuildingView b : view.buildings()) {
            if (b.state() == 2 && view.is(b.template(), CENTRE)) {
                own = b;
                break;
            }
        }
        if (own == null) {
            return null;
        }
        Footprint f = own.footprint();
        double x = f.anchorX() + f.spanX() / 2.0;
        double z = f.anchorZ() + f.spanZ() / 2.0;
        BuildingView enemy = null;
        double best = Double.POSITIVE_INFINITY;
        for (BuildingView b : view.enemyBuildings()) {
            if (view.is(b.template(), CENTRE)) {
                Footprint e = b.footprint();
                double d = DetMath.length(e.anchorX() + e.spanX() / 2.0 - x, e.anchorZ() + e.spanZ() / 2.0 - z);
                if (d < best) {
                    best = d;
                    enemy = b;
                }
            }
        }
        StartSlot home = enemy == null ? enemyStart(view, x, z) : null;
        boolean fallen = home != null && standingAt(view, home.x() + 0.5, home.z() + 0.5);
        if (fallen) {
            // QA-12: one of our soldiers stands at the enemy's start and sees no centre: it is down. What else of the
            // enemy's stands in sight is the target, the nearest first, so conquest can finish; fields and
            // decorations do not count to it
            for (BuildingView b : view.enemyBuildings()) {
                if (b.state() != 2 || view.is(b.template(), "Field") || view.is(b.template(), "Decoration")) {
                    continue;
                }
                Footprint e = b.footprint();
                double d = DetMath.length(e.anchorX() + e.spanX() / 2.0 - x, e.anchorZ() + e.spanZ() / 2.0 - z);
                if (d < best) {
                    best = d;
                    enemy = b;
                }
            }
        }
        double aimX;
        double aimZ;
        if (enemy != null) {
            Footprint e = enemy.footprint();
            aimX = e.anchorX() + e.spanX() / 2.0;
            aimZ = e.anchorZ() + e.spanZ() / 2.0;
        } else {
            StartSlot start = enemyStart(view, x, z);
            if (start == null) {
                aimX = x;
                aimZ = z;
            } else {
                aimX = start.x() + 0.5;
                aimZ = start.z() + 0.5;
            }
        }
        int mirror = x < view.bounds().sizeX() / 2.0 ? 1 : -1;
        return new Base(own, x, z, enemy, mirror, aimX, aimZ, fallen);
    }

    /** How near the enemy's start a soldier of ours stands to see its centre, if one stands there: blocks. */
    static final double SIGHT = 12;

    private static boolean standingAt(AiView view, double x, double z) {
        for (UnitView u : view.units()) {
            if (view.role(u) != AiView.ROLE_NONE && DetMath.length(u.x() - x, u.z() - z) <= SIGHT) {
                return true;
            }
        }
        return false;
    }

    /**
     * The start slot to attack toward when no enemy building is visible (AI-18): not this faction's own start when the
     * slots are indexed by faction, else the farthest start from the civic centre.
     */
    static @Nullable StartSlot enemyStart(AiView view, double x, double z) {
        List<StartSlot> starts = view.startPositions();
        if (starts.isEmpty()) {
            return null;
        }
        int faction = view.faction();
        if (faction >= 0 && faction < starts.size() && starts.size() > 1) {
            StartSlot best = null;
            double bestD = Double.POSITIVE_INFINITY;
            for (int i = 0; i < starts.size(); i++) {
                if (i == faction) {
                    continue;
                }
                StartSlot s = starts.get(i);
                double d = DetMath.length(s.x() + 0.5 - x, s.z() + 0.5 - z);
                if (d < bestD) {
                    bestD = d;
                    best = s;
                }
            }
            return best;
        }
        StartSlot best = null;
        double bestD = -1;
        for (StartSlot s : starts) {
            double d = DetMath.length(s.x() + 0.5 - x, s.z() + 0.5 - z);
            if (d > bestD) {
                bestD = d;
                best = s;
            }
        }
        return best;
    }

    /** How near the enemy's centre a node is its, not to be gathered or built by (AI-02). */
    static final double ENEMY_HOME = 64;

    /** Whether (x, z) lies within {@value #ENEMY_HOME} blocks of the enemy's centre. */
    boolean enemyHome(double x, double z) {
        return enemy != null && DetMath.length(x - enemyX(), z - enemyZ()) < ENEMY_HOME;
    }

    double enemyX() {
        return aimX;
    }

    double enemyZ() {
        return aimZ;
    }
}
