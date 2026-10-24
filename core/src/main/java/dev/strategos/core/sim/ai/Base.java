package dev.strategos.core.sim.ai;

import dev.strategos.core.content.Footprint;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.sim.build.BuildingView;
import org.jspecify.annotations.Nullable;

/**
 * Where a computer player's base is in a think: its first finished civic centre's middle, the nearest enemy civic
 * centre, and which way its sites mirror (the second start slot of a point-mirrored map turns them round).
 */
record Base(
        BuildingView centre, double x, double z, @Nullable BuildingView enemy, int mirror) {
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
        int mirror = x < view.bounds().sizeX() / 2.0 ? 1 : -1;
        return new Base(own, x, z, enemy, mirror);
    }

    /** How near the enemy's centre a node is its, not to be gathered or built by (AI-02). */
    static final double ENEMY_HOME = 64;

    /** Whether (x, z) lies within {@value #ENEMY_HOME} blocks of the enemy's centre. */
    boolean enemyHome(double x, double z) {
        return enemy != null && DetMath.length(x - enemyX(), z - enemyZ()) < ENEMY_HOME;
    }

    double enemyX() {
        BuildingView e = enemy;
        return e == null ? x : e.footprint().anchorX() + e.footprint().spanX() / 2.0;
    }

    double enemyZ() {
        BuildingView e = enemy;
        return e == null ? z : e.footprint().anchorZ() + e.footprint().spanZ() / 2.0;
    }
}
