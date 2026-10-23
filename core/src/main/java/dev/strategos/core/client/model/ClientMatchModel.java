package dev.strategos.core.client.model;

import dev.strategos.core.protocol.UnitDetail;
import dev.strategos.core.protocol.UnitHud;
import dev.strategos.core.protocol.UnitSummary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What the client knows of its match (NET-08): the units its faction knows, from the last {@code unit_summary}, a full
 * frame, so each replaces the last (a frame of another match, or older than the one held, is ignored); each unit's hit
 * points from {@code unit_hud}, whole until told otherwise; and the details its {@code ui_query} asked for.
 */
public final class ClientMatchModel {
    private int matchId = -1;
    private long tick = -1;
    private List<UnitSummary.Entry> units = List.of();
    private final Map<Integer, Integer> health = new HashMap<>();
    private List<UnitDetail.Unit> details = List.of();

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

    /** Takes hit points that changed: the joined match's. */
    public void accept(UnitHud hud, int joinedMatch) {
        if (hud.matchId() != joinedMatch) {
            return;
        }
        for (UnitHud.Entry e : hud.units()) {
            health.put(e.index(), e.health());
        }
    }

    /** Takes the details the last query asked for: the joined match's. */
    public void accept(UnitDetail detail, int joinedMatch) {
        if (detail.matchId() == joinedMatch) {
            details = detail.units();
        }
    }

    /** The unit at {@code index}'s hit points in 255ths of its most: {@link UnitHud#WHOLE} unless told otherwise. */
    public int health(int index) {
        return health.getOrDefault(index, UnitHud.WHOLE);
    }

    public List<UnitDetail.Unit> details() {
        return details;
    }

    /** Forgets the match: the player left it. */
    public void clear() {
        matchId = -1;
        tick = -1;
        units = List.of();
        health.clear();
        details = List.of();
    }

    public List<UnitSummary.Entry> units() {
        return units;
    }

    public long tick() {
        return tick;
    }
}
