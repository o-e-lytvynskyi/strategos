package dev.strategos.core.sim.ai;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.replicate.BuildingMemory;
import dev.strategos.core.sim.replicate.Visibility;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Cold, tick-scoped fog-fair knowledge, not an AI controller or reveal-map exception. No private enemy orders survive.
 * AFTER-M6: produce AI-18 observations and issue ordinary validated scouting commands.
 * AFTER-M7: supply diplomacy and viewer-authorized building memory.
 */
@CoreApi
public final class ScoutKnowledge {
    /** Publicly observed target data only; UnitView.target and private orders are intentionally absent. */
    public record Target(int handle, FactionSlot faction, TemplateId template, Vec2d position) {}

    private final long observedAt;
    private final List<Target> targets;
    private final BuildingMemory buildings;
    private final List<Vec2d> starts;

    private ScoutKnowledge(long observedAt, List<Target> targets, BuildingMemory buildings, List<Vec2d> starts) {
        this.observedAt = observedAt;
        this.targets = List.copyOf(targets);
        this.buildings = buildings;
        this.starts = List.copyOf(starts);
    }

    /** All inputs must describe this viewer at one authoritative tick. No live query or mutable list is retained. */
    public static ScoutKnowledge capture(
            long tick,
            FactionSlot viewer,
            List<UnitView> candidates,
            Visibility visibility,
            Predicate<FactionSlot> enemy,
            BuildingMemory buildings,
            List<Vec2d> starts) {
        if (tick < 0 || buildings.updatedAt() > tick) {
            throw new IllegalArgumentException("knowledge predates observed building memory");
        }
        for (Vec2d start : starts) {
            if (!Double.isFinite(start.x()) || !Double.isFinite(start.z())) {
                throw new IllegalArgumentException("nonfinite map start");
            }
        }
        List<Target> observed = new ArrayList<>();
        for (UnitView unit : candidates) {
            if (!unit.faction().equals(viewer) && enemy.test(unit.faction()) && visibility.sees(viewer, unit)) {
                observed.add(new Target(unit.handle(), unit.faction(), unit.template(), new Vec2d(unit.x(), unit.z())));
            }
        }
        observed.sort((a, b) -> Integer.compareUnsigned(a.handle(), b.handle()));
        for (int i = 1; i < observed.size(); i++) {
            if (observed.get(i - 1).handle() == observed.get(i).handle()) {
                throw new IllegalArgumentException("duplicate observed unit handle");
            }
        }
        return new ScoutKnowledge(tick, observed, buildings, starts);
    }

    public long observedAt() {
        return observedAt;
    }

    /** Live targets must not carry over into a later think. Building memories never enter this list. */
    public List<Target> targets(long tick) {
        return tick == observedAt ? targets : List.of();
    }

    public BuildingMemory buildings() {
        return buildings;
    }

    /** Public map starts in map order; the adapter supplies whether a start has actually been scouted. */
    public List<Vec2d> unscoutedStarts(Predicate<Vec2d> scouted) {
        List<Vec2d> out = new ArrayList<>();
        for (Vec2d start : starts) {
            if (!scouted.test(start)) {
                out.add(start);
            }
        }
        return List.copyOf(out);
    }
}
