package dev.strategos.mod.server.fog;

import java.util.UUID;
import java.util.function.ToIntFunction;

/**
 * The fog gate's stub (NET-01, ADR-0016): whether vanilla's tracking may pair a player with a match's unit body, asked
 * through {@code UnitEntity#broadcastToPlayer} (Entity.java:3021). A member of another match never sees the body; a
 * member of the same match, and a player in no match, do. Any failure hides the body: the gate fails closed. Fog of war
 * (M8) puts each body's visibility behind the same call.
 */
public final class FogGate {
    private FogGate() {}

    /** Over match ids, 0 for a player in no match (spec §1.6 row 8). */
    public static boolean decide(int bodyMatch, int viewerMatch) {
        return viewerMatch == 0 || viewerMatch == bodyMatch;
    }

    /** {@code matchOf} finds the match a player sits in, 0 for none; if it throws, the body stays hidden. */
    public static boolean canSee(int bodyMatch, UUID viewer, ToIntFunction<UUID> matchOf) {
        try {
            return decide(bodyMatch, matchOf.applyAsInt(viewer));
        } catch (RuntimeException e) {
            return false;
        }
    }
}
