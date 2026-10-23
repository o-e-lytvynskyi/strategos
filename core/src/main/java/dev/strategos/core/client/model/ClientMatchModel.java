package dev.strategos.core.client.model;

import dev.strategos.core.protocol.UnitSummary;
import java.util.List;

/**
 * The units the client's faction knows (NET-08 tier 2), from the last {@code unit_summary} of its match: a full frame,
 * so each replaces the last; a frame of another match, or older than the one held, is ignored.
 */
public final class ClientMatchModel {
    private int matchId = -1;
    private long tick = -1;
    private List<UnitSummary.Entry> units = List.of();

    /** Takes a frame: the joined match's, newer than the last; a new match's replaces everything. */
    public void accept(UnitSummary frame, int joinedMatch) {
        if (frame.matchId() != joinedMatch) {
            return;
        }
        if (frame.matchId() == matchId && frame.tick() <= tick) {
            return;
        }
        matchId = frame.matchId();
        tick = frame.tick();
        units = frame.units();
    }

    /** Forgets the match: the player left it. */
    public void clear() {
        matchId = -1;
        tick = -1;
        units = List.of();
    }

    public List<UnitSummary.Entry> units() {
        return units;
    }

    public long tick() {
        return tick;
    }
}
