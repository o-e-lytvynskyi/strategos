package dev.strategos.core.sim;

import dev.strategos.core.content.CorePaths;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.sim.stats.Modifier;

/**
 * Night's vision (MATCH-16): while the match rule {@code dayNight} is on and the {@link DayClock} says night, every
 * faction's {@code Vision/range} is scaled by the template set's {@code Vision/night} (units and buildings each by
 * their own; {@link #DEFAULT_MULTIPLIER} where the set has none) through a faction modifier under {@link #SOURCE}.
 * Never saved: {@link #refresh} derives it, and it changes the modifiers only at dusk and dawn (or when the rule
 * changes), so {@code VisionSystem} re-stamps on the stat version and nothing costs a tick otherwise. A building's
 * {@code Vision/light} is read by {@code VisionSystem}, which keeps the larger of the two radii at night.
 *
 * <p>{@code AFTER-M8}: FOW-02's shared (team) vision takes the night radius from here: it must read the stamped
 * radius, never {@code Vision/range} directly.
 */
@CoreApi
public final class NightFactor {
    /** The source id: just above {@code CivBonuses.SOURCE}, below every tech's and every handle's. */
    public static final int SOURCE = Integer.MIN_VALUE + 2;

    /** The multiplier of a set that gives none: 0.6, so a 12-block sight is 7.2 at night. */
    public static final double DEFAULT_MULTIPLIER = 0.6;

    private NightFactor() {}

    /** Whether night is in effect at the state's tick: the rule is on and the clock says night. */
    public static boolean active(MatchState s) {
        return s.config().rules().play().dayNight() && DayClock.night(s.tick());
    }

    /**
     * The multiplier of a template set's units ({@code buildings} false) or buildings: the first template of that kind
     * with a {@code Vision/night}, in net index order, else {@link #DEFAULT_MULTIPLIER}.
     */
    public static double multiplier(TemplateSet set, boolean buildings) {
        var path = set.valuePaths().byPath(CorePaths.VISION_NIGHT);
        if (path != null) {
            for (UnitTemplate t : set.all()) {
                if (t.isBuilding() == buildings) {
                    double v = set.value(t.netIndex(), path.id());
                    if (!Double.isNaN(v)) {
                        return v;
                    }
                }
            }
        }
        return DEFAULT_MULTIPLIER;
    }

    /** Brings the modifiers to what the clock and the rule say now; touches nothing when they already do. */
    public static void refresh(MatchState s) {
        boolean night = active(s);
        if (night != s.nightApplied()) {
            reapply(s);
        }
    }

    /** Derives every faction's modifiers again: after a restore, a swap of the template set and at match start. */
    public static void reapply(MatchState s) {
        boolean night = active(s);
        double units = multiplier(s.templates(), false);
        double buildings = multiplier(s.templates(), true);
        for (int f = 0; f < s.config().factions(); f++) {
            FactionSlot slot = new FactionSlot(f);
            s.stats().remove(slot, SOURCE);
            if (night) {
                s.stats()
                        .add(slot, Modifier.mul(CorePaths.VISION, units, SOURCE).withFilter("Unit"));
                s.stats()
                        .add(
                                slot,
                                Modifier.mul(CorePaths.VISION, buildings, SOURCE)
                                        .withFilter("Structure"));
            }
        }
        s.setNightApplied(night);
    }
}
