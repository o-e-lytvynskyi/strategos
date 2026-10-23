package dev.strategos.core.sim.spatial;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.sim.MatchBounds;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.MatchSystem;
import dev.strategos.core.sim.Phase;
import java.util.function.IntConsumer;

/**
 * A match's spatial index (CORE-08, 04 §10.1): its units' grid, rebuilt as the last step of {@code MOVEMENT} and when
 * a snapshot is restored, so every query between two movements sees where the units stood after the first, and its
 * static entities by page. Derived from the state, so neither saved nor hashed. Queries report unit indices.
 */
public final class SpatialQueries implements MatchSystem {
    private final UnitGrid units;
    private final StaticIndex statics;

    public SpatialQueries(MatchBounds bounds, TemplateSet templates) {
        this.units = new UnitGrid(bounds.sizeX(), bounds.sizeZ(), templates);
        this.statics = new StaticIndex(bounds.sizeX(), bounds.sizeZ());
    }

    /** A new template set's class bits (DATA-03). */
    public void swap(TemplateSet templates) {
        units.swap(templates);
    }

    @Override
    public Phase phase() {
        return Phase.MOVEMENT;
    }

    @Override
    public void run(MatchState state) {
        rebuild(state);
    }

    public void rebuild(MatchState state) {
        units.rebuild(state.entities(), state.motion(), state.identity());
    }

    public UnitGrid units() {
        return units;
    }

    public StaticIndex statics() {
        return statics;
    }

    public void forEachInRadius(double x, double z, double r, UnitFilter filter, IntConsumer consumer) {
        units.forEachInRadius(x, z, r, filter, consumer);
    }

    public int nearest(double x, double z, UnitFilter filter) {
        return units.nearest(x, z, filter);
    }

    /** The unit nearest (x, z) within {@code r} that passes {@code filter}; -1 when none does. */
    public int nearest(double x, double z, double r, UnitFilter filter) {
        return units.nearest(x, z, r, filter);
    }

    public int countWithin(double x, double z, double r, UnitFilter filter) {
        return units.countWithin(x, z, r, filter);
    }

    public int nearestNode(int resource, double x, double z) {
        return statics.nearestNode(resource, x, z);
    }

    public int nearestDropsite(int faction, int resource, double x, double z) {
        return statics.nearestDropsite(faction, resource, x, z);
    }
}
