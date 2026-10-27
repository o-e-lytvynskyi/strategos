package dev.strategos.core.sim.unitai;

import dev.strategos.core.shared.DetMath;
import dev.strategos.core.shared.Stance;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.combat.Engagement;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.stats.StatService;

/**
 * What a unit's stance asks of its automatic fighting (CMB-05, M5 §2.4). With V its vision and A the reach of its
 * weapon, edge to edge: violent and aggressive units notice enemies within V; a defensive unit those within A + V/2 of
 * its spot, and it walks back there when an engagement ends; a stand-ground unit those within A, and it never walks for
 * them; a passive unit none, and runs from a blow. Orders the player gave are obeyed in every stance.
 */
final class Stances {
    /** How far a passive unit without a flee distance of its own runs from a blow, in blocks. */
    static final double FLEE = 12;
    /** How near its spot a defensive unit counts as there. */
    static final double AT_SPOT = 1.0;
    /** How near a finished order must end to a spot for the spot to stay (the walk home stops short of it). */
    static final double SPOT_KEEPS = 2.0;
    /** The most a ranged unit's range is shortened to meet it where it fires, as in the ranged approach. */
    private static final double MARGIN = 0.5;
    /** The widest body a stand-ground search allows for, beyond the reach it filters by. */
    private static final double BODY_SLACK = 2.0;

    private Stances() {}

    /** Unit {@code i}'s stance: the one a player set, else its template's. */
    static Stance of(MatchState s, int i) {
        Stance set = s.stances().explicit(i);
        return set != null ? set : s.stats().stance(i);
    }

    /** The reach of unit {@code i}'s weapon, edge to edge, as it takes its place to fire or strike. */
    static double reach(MatchState s, int i) {
        StatService stats = s.stats();
        return stats.canRanged(i) ? Math.max(0, stats.rangedRange(i) - MARGIN) : stats.meleeReach(i);
    }

    /** How far from its anchor unit {@code i} notices enemies in stance {@code stance}, centre to centre. */
    static double radius(MatchState s, int i, Stance stance) {
        double vision = s.stats().vision(i);
        return switch (stance) { // never past sight: a chase ends there, so a wider notice would engage and drop
            // forever
            case VIOLENT, AGGRESSIVE -> vision;
            case DEFENSIVE -> Math.min(vision, reach(s, i) + vision / 2);
            case STAND_GROUND -> Math.min(vision, reach(s, i));
            case PASSIVE -> 0;
        };
    }

    /** How far a passive unit runs from a blow: its template's flee distance, else {@value #FLEE} blocks. */
    static double fleeDistance(MatchState s, int i) {
        double own = s.stats().fleeDistance(i);
        return own > 0 ? own : FLEE;
    }

    /** The X of the place unit {@code i} guards: its spot while it stands idle or engaged, else where it is. */
    static double anchorX(MatchState s, int i) {
        return guarding(s, i) ? s.stances().spotX(i) : s.motion().x(i);
    }

    static double anchorZ(MatchState s, int i) {
        return guarding(s, i) ? s.stances().spotZ(i) : s.motion().z(i);
    }

    /** Whether idle defensive unit {@code i} has a spot, which it takes where it stands the first time it needs one. */
    private static boolean guarding(MatchState s, int i) {
        if (of(s, i) != Stance.DEFENSIVE) {
            return false;
        }
        if (s.orders().size(i) > 0) { // an engagement of its own finds the next target round its spot too
            return s.orders().kind(i) == OrderKind.ENGAGE && s.stances().hasSpot(i);
        }
        if (!s.stances().hasSpot(i)) {
            s.stances().set(i, null, s.motion().x(i), s.motion().z(i));
        }
        return true;
    }

    /** A defensive unit that takes an engagement over other orders makes the place it stands its spot. */
    static void engaging(MatchState s, int i) {
        if (s.orders().size(i) > 0 && of(s, i) == Stance.DEFENSIVE) {
            s.stances().set(i, null, s.motion().x(i), s.motion().z(i));
        }
    }

    /** A defensive unit that went idle where it was ordered to stand makes that its spot, unless it is home. */
    static void idled(MatchState s, int i) {
        if (of(s, i) != Stance.DEFENSIVE) {
            return;
        }
        double x = s.motion().x(i);
        double z = s.motion().z(i);
        if (!s.stances().hasSpot(i)
                || DetMath.length(x - s.stances().spotX(i), z - s.stances().spotZ(i)) > SPOT_KEEPS) {
            s.stances().set(i, null, x, z);
        }
    }

    /** An engagement of defensive unit {@code i} ended with no order left: it walks back to its spot, if it is away. */
    static void returnHome(MatchState s, int i) {
        if (of(s, i) != Stance.DEFENSIVE || !s.stances().hasSpot(i)) {
            return;
        }
        double x = s.stances().spotX(i);
        double z = s.stances().spotZ(i);
        if (DetMath.length(x - s.motion().x(i), z - s.motion().z(i)) > AT_SPOT) {
            s.orders().pushFront(i, OrderKind.MOVE, x, z, 0, 0);
        }
    }

    /** The widest a stand-ground search reaches beyond its reach, to be filtered by the gap between edges. */
    static double searchSlack(MatchState s, int i) {
        return Engagement.radius(s, i) + BODY_SLACK;
    }
}
