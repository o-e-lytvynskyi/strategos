package dev.strategos.mod.client.fx;

import dev.strategos.core.client.fx.ShotArc;
import dev.strategos.core.protocol.Fx;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The shots in flight on this client (CMB-04): a launch {@code fx} event starts an arc, its landing time or the
 * matching impact event (same two ends) ends it, and a different match, the match's end or a logout clears them all.
 * At most {@value #CAP} at once, the oldest dropped first. Client thread only, no Minecraft types.
 */
public final class ShotArcs {
    public static final int CAP = 512;

    /** A shot in flight: its path in world coordinates, the event's ends for matching, and its times. */
    public record Flight(ShotArc arc, Fx.Event ends, long launchTime, int flightTicks) {
        /** The fraction of the flight done at {@code time} (game ticks and a part), unclamped. */
        public double fraction(double time) {
            return (time - launchTime) / flightTicks;
        }
    }

    private final ArrayDeque<Flight> flights = new ArrayDeque<>();
    private int match = Integer.MIN_VALUE;

    /** A launch of {@code matchId}, its ends offset by the match's origin, seen at client time {@code now}. */
    public void launch(int matchId, Fx.Event e, double originX, double originY, double originZ, long now) {
        if (matchId != match) {
            flights.clear();
            match = matchId;
        }
        if (flights.size() >= CAP) {
            flights.removeFirst();
        }
        flights.addLast(new Flight(
                new ShotArc(
                        originX + e.x8() / 8.0,
                        originY + e.y8() / 8.0,
                        originZ + e.z8() / 8.0,
                        originX + e.toX8() / 8.0,
                        originY + e.toY8() / 8.0,
                        originZ + e.toZ8() / 8.0),
                e,
                now,
                e.flightTicks()));
    }

    /** An impact of {@code matchId}: ends the oldest arc with the same two ends, if one is still in the air. */
    public void impact(int matchId, Fx.Event e) {
        if (matchId != match) {
            return;
        }
        for (Iterator<Flight> it = flights.iterator(); it.hasNext(); ) {
            Fx.Event ends = it.next().ends();
            if (ends.x8() == e.x8()
                    && ends.y8() == e.y8()
                    && ends.z8() == e.z8()
                    && ends.toX8() == e.toX8()
                    && ends.toY8() == e.toY8()
                    && ends.toZ8() == e.toZ8()) {
                it.remove();
                return;
            }
        }
    }

    /** The match's end, a logout or a new match. */
    public void clear() {
        flights.clear();
        match = Integer.MIN_VALUE;
    }

    /** The match whose arcs are held; a join of another match clears them. */
    public void joined(int matchId) {
        if (matchId != match) {
            clear();
            match = matchId;
        }
    }

    /** Drops the arcs landed by client time {@code now} and returns those still in flight, oldest first. */
    public List<Flight> active(long now) {
        flights.removeIf(f -> now >= f.launchTime() + f.flightTicks());
        return new ArrayList<>(flights);
    }

    public int size() {
        return flights.size();
    }
}
