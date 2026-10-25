package dev.strategos.core.client.model;

import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.BuildingState;
import dev.strategos.core.protocol.DepositState;
import dev.strategos.core.protocol.FactionState;
import dev.strategos.core.protocol.MatchNews;
import dev.strategos.core.protocol.UnitDetail;
import dev.strategos.core.protocol.UnitHud;
import dev.strategos.core.protocol.UnitSummary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * What the client knows of its match (NET-08): the units its faction knows, from the last {@code unit_summary}, a full
 * frame, so each replaces the last (a frame of another match, or older than the one held, is ignored); each unit's hit
 * points from {@code unit_hud}, whole until told otherwise; and the details its {@code ui_query} asked for.
 */
public final class ClientMatchModel {
    private @Nullable FactionState faction;
    private @Nullable BuildingState buildings;
    private @Nullable DepositState deposits;
    private @Nullable MatchNews end;
    private @Nullable MatchNews alert;
    private @Nullable MatchNews wave;
    private int waves;
    private int alerts;
    private long defeatedMask;

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

    /** Takes the player's faction's state: the joined match's, newer ticks only (ECO-01). */
    public void accept(FactionState state, int joinedMatch) {
        FactionState last = faction;
        if (state.matchId() == joinedMatch
                && (last == null || last.matchId() != joinedMatch || state.tick() >= last.tick())) {
            faction = state;
        }
    }

    /** Takes the buildings the player knows of: the joined match's, newer ticks only (BLD-01). */
    public void accept(BuildingState state, int joinedMatch) {
        BuildingState last = buildings;
        if (state.matchId() == joinedMatch
                && (last == null || last.matchId() != joinedMatch || state.tick() >= last.tick())) {
            buildings = state;
        }
    }

    /** Takes the match's deposits: the joined match's, newer ticks only (ECO-08). */
    public void accept(DepositState state, int joinedMatch) {
        DepositState last = deposits;
        if (state.matchId() == joinedMatch
                && (last == null || last.matchId() != joinedMatch || state.tick() >= last.tick())) {
            deposits = state;
        }
    }

    /**
     * Takes match news (MATCH-01, UI-12): the joined match's defeats, its end and its alerts. A phase notice (TECH-02)
     * changes nothing here; the feed shows it from T14.
     */
    public void accept(MatchNews news, int joinedMatch) {
        if (news.matchId() != joinedMatch) {
            return;
        }
        if (news.kind() == MatchNews.ENDED) {
            end = news;
        } else if (news.kind() == MatchNews.WAVE) {
            wave = news;
            waves++;
        } else if (news.kind() == MatchNews.ALERT) {
            alert = news;
            alerts++;
        } else if (news.kind() == MatchNews.DEFEATED && news.faction() >= 0 && news.faction() < 64) {
            defeatedMask |= 1L << news.faction();
        }
    }

    /** The match's end, once it came; null while it goes on. */
    public @Nullable MatchNews end() {
        return end;
    }

    /** The latest under-attack alert (UI-12), or null before any. */
    public @Nullable MatchNews alert() {
        return alert;
    }

    /** The latest announced wave (AI-17), or null before any. */
    public @Nullable MatchNews wave() {
        return wave;
    }

    /** How many waves were announced, for a new one to be noticed. */
    public int waves() {
        return waves;
    }

    /** How many alerts came, for a new one to be noticed. */
    public int alerts() {
        return alerts;
    }

    /** Whether faction {@code faction} was defeated. */
    public boolean defeated(int faction) {
        return faction >= 0 && faction < 64 && (defeatedMask & (1L << faction)) != 0;
    }

    /** The buildings the player knows of; empty before the first state. */
    public List<BuildingState.Entry> buildings() {
        BuildingState b = buildings;
        return b == null ? List.of() : b.buildings();
    }

    /**
     * The building whose footprint holds match-local column ({@code x}, {@code z}), by its template's blueprint in
     * {@code templates}, or null: what a click on a building's block picks (BLD-01).
     */
    public BuildingState.@Nullable Entry buildingAt(TemplateSet templates, int x, int z) {
        for (BuildingState.Entry e : buildings()) {
            Blueprint bp = templates.blueprint(e.template());
            if (bp == null) {
                continue;
            }
            if (holds(Footprint.of(bp, e.x(), e.z(), e.rotation()).columns(bp), x, z)) {
                return e;
            }
        }
        return null;
    }

    /** The match's deposits; empty before the first state. */
    public List<DepositState.Entry> deposits() {
        DepositState d = deposits;
        return d == null ? List.of() : d.deposits();
    }

    /** The deposit whose footprint holds match-local column ({@code x}, {@code z}), or null (ECO-08). */
    public DepositState.@Nullable Entry depositAt(TemplateSet templates, int x, int z) {
        for (DepositState.Entry e : deposits()) {
            Blueprint bp = templates.blueprint(e.template());
            if (bp != null && holds(Footprint.of(bp, e.x(), e.z(), 0).columns(bp), x, z)) {
                return e;
            }
        }
        return null;
    }

    private static boolean holds(int[] columns, int x, int z) {
        for (int cell : columns) {
            if ((cell >>> 16) == x && (cell & 0xFFFF) == z) {
                return true;
            }
        }
        return false;
    }

    /** The last faction state of the joined match, or null before the first. */
    public @Nullable FactionState faction() {
        return faction;
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
        end = null;
        alert = null;
        alerts = 0;
        wave = null;
        waves = 0;
        defeatedMask = 0;
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
