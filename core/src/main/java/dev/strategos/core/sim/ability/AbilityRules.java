package dev.strategos.core.sim.ability;

import dev.strategos.core.content.AbilityDef;
import dev.strategos.core.content.AbilityDef.Aim;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.Target;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.combat.Engagement;
import dev.strategos.core.sim.econ.Amount;
import dev.strategos.core.sim.econ.EconomyRules;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Using an ability (UNIT-10, 04 §7.6): what the command and the unit's machine check, and the firing. The checks are
 * made at the tick boundary, in the order of the unit's turn: the unit has the ability, its target is the kind the
 * ability takes and is there, the ability is off cooldown, the target is in range, the effects can land and the faction
 * can pay; only then is the cost paid, once, through the treasury's {@code pay} (so the books balance), the cooldown
 * started and the effects run. The refusals are {@link RejectReason}s: {@code UNSUPPORTED} (no such ability),
 * {@code INVALID_ARGUMENT} (the wrong kind of target), {@code UNKNOWN_TARGET}, {@code OUT_OF_BOUNDS},
 * {@code ON_COOLDOWN}, {@code OUT_OF_REACH} (beyond range), {@code BLOCKED} (nowhere to
 * land), {@code NOT_ENOUGH_RESOURCES}.
 */
public final class AbilityRules {
    /** How far past an ability's range a picker looks for a target, in blocks: the bodies' radii the gap leaves out. */
    static final double BODY_MARGIN = 2.0;

    private AbilityRules() {}

    /** Unit {@code index}'s own ability {@code id}, or null when its template has none. */
    public static @Nullable AbilityDef defOf(MatchState s, int index, String id) {
        List<AbilityDef> own = s.templates().abilitiesOf(s.identity().templateIndex(index));
        for (AbilityDef def : own) {
            if (def.id().equals(id)) {
                return def;
            }
        }
        return null;
    }

    /**
     * What the command checks of unit {@code handle} using {@code def} on {@code target}, before it takes the order, or
     * null: everything but the range, which the unit walks into. An order that replaces the unit's others is also
     * checked for cooldown and cost; one that joins the queue is checked for them when it fires, as its turn may come
     * after the ability has rested and the faction has earned the cost.
     */
    public static @Nullable RejectReason orderRefusal(
            MatchState s, int handle, AbilityDef def, Target target, QueueMode mode) {
        RejectReason shape = shapeRefusal(s, def, target);
        if (shape != null) {
            return shape;
        }
        int unit = target.kind() == Target.Kind.UNIT ? target.unit() : Handle.NONE;
        return refusal(s, handle, def, unit, target.x(), target.z(), false, mode == QueueMode.REPLACE);
    }

    private static @Nullable RejectReason shapeRefusal(MatchState s, AbilityDef def, Target target) {
        boolean fits = switch (def.aim()) {
            case NONE -> target.kind() == Target.Kind.NONE;
            case POINT -> target.kind() == Target.Kind.POINT;
            case UNIT -> target.kind() == Target.Kind.UNIT;
            case AREA -> target.kind() == Target.Kind.POINT || target.kind() == Target.Kind.UNIT;
        };
        if (!fits) {
            return RejectReason.INVALID_ARGUMENT;
        }
        if (target.kind() == Target.Kind.POINT && !s.config().bounds().contains(target.x(), target.z())) {
            return RejectReason.OUT_OF_BOUNDS;
        }
        if (target.kind() == Target.Kind.UNIT) {
            if (BuildingStore.isBuilding(target.unit())) {
                return RejectReason.INVALID_ARGUMENT; // abilities are for units
            }
            if (Engagement.gone(s, target.unit())) {
                return RejectReason.UNKNOWN_TARGET;
            }
        }
        return null;
    }

    /**
     * The first reason unit {@code handle} cannot use {@code def} on {@code unit} (0 for none) or at ({@code px},
     * {@code pz}) now, or null. {@code inRange} adds the range, which only the firing asks of; {@code timing} adds the
     * cooldown and the cost.
     */
    static @Nullable RejectReason refusal(
            MatchState s, int handle, AbilityDef def, int unit, double px, double pz, boolean inRange, boolean timing) {
        int i = Handle.index(handle);
        if (defOf(s, i, def.id()) == null) {
            return RejectReason.UNSUPPORTED;
        }
        if (unit != Handle.NONE && Engagement.gone(s, unit)) {
            return RejectReason.UNKNOWN_TARGET;
        }
        if (inRange && !inRange(s, i, def, unit, px, pz)) {
            return RejectReason.OUT_OF_REACH;
        }
        if (timing && s.play().abilities().readyAt(handle, def.id()) > s.tick()) {
            return RejectReason.ON_COOLDOWN;
        }
        AbilityEffects.Cast cast = cast(s, handle, def, unit, px, pz);
        for (AbilityEffects.Effect effect : s.play().abilities().effects(s.templates(), def)) {
            RejectReason why = effect.refusal(s, cast);
            if (why != null) {
                return why;
            }
        }
        if (timing && !def.cost().isEmpty() && !affordable(s, i, def)) {
            return RejectReason.NOT_ENOUGH_RESOURCES;
        }
        return null;
    }

    /**
     * Unit {@code handle} uses {@code def} on {@code unit} (0 for none) or at ({@code px}, {@code pz}): the refusal, or
     * null when it fired. {@code OUT_OF_REACH} means it did not and the unit may walk on; every other reason ends the
     * attempt.
     */
    public static @Nullable RejectReason fire(
            MatchState s, int handle, AbilityDef def, int unit, double px, double pz) {
        RejectReason why = refusal(s, handle, def, unit, px, pz, true, true);
        if (why != null) {
            return why;
        }
        int i = Handle.index(handle);
        if (!def.cost().isEmpty() && !s.treasury().pay(faction(s, i), Objects.requireNonNull(cost(s, def)))) {
            return RejectReason.NOT_ENOUGH_RESOURCES; // not reached after the refusal check above
        }
        if (def.cooldown() > 0) {
            s.play().abilities().start(handle, def.id(), s.tick() + def.cooldown());
        }
        AbilityEffects.Cast cast = cast(s, handle, def, unit, px, pz);
        for (AbilityEffects.Effect effect : s.play().abilities().effects(s.templates(), def)) {
            effect.apply(s, cast);
        }
        return null;
    }

    /** Whether the target of unit {@code i}'s use of {@code def} is within the ability's range. */
    public static boolean inRange(MatchState s, int i, AbilityDef def, int unit, double px, double pz) {
        if (def.aim() == Aim.NONE) {
            return true;
        }
        if (unit != Handle.NONE) {
            return Engagement.gap(s, i, Handle.index(unit)) <= def.range() + 1e-9;
        }
        double d = DetMath.length(px - s.motion().x(i), pz - s.motion().z(i));
        return d - Engagement.radius(s, i) <= def.range() + 1e-9;
    }

    /** Where a use lands: the target unit's place, else the point, else (aimless) the user's. */
    static AbilityEffects.Cast cast(MatchState s, int handle, AbilityDef def, int unit, double px, double pz) {
        int index = s.templates().abilityIndex(def.id());
        int i = Handle.index(handle);
        if (def.aim() == Aim.NONE) {
            return new AbilityEffects.Cast(
                    handle, def, index, handle, s.motion().x(i), s.motion().z(i));
        }
        if (unit != Handle.NONE && s.entities().isAlive(unit)) {
            int t = Handle.index(unit);
            return new AbilityEffects.Cast(
                    handle, def, index, unit, s.motion().x(t), s.motion().z(t));
        }
        return new AbilityEffects.Cast(handle, def, index, Handle.NONE, px, pz);
    }

    /** What {@code def} costs, per resource of the match in thousandths; null when it names a resource the match lacks. */
    private static long @Nullable [] cost(MatchState s, AbilityDef def) {
        EconomyRules economy = s.config().rules().economy();
        long[] out = new long[economy.size()];
        int named = 0;
        for (int r = 0; r < out.length; r++) {
            Double units = def.cost().get(economy.resources().get(r).id());
            if (units != null) {
                out[r] = Amount.of(units);
                named++;
            }
        }
        return named == def.cost().size() ? out : null;
    }

    /** Whether unit {@code i}'s faction holds what {@code def} costs, and every resource it names exists in the match. */
    private static boolean affordable(MatchState s, int i, AbilityDef def) {
        long[] cost = cost(s, def);
        int faction = faction(s, i);
        return cost != null && faction < s.config().factions() && s.treasury().missing(faction, cost) == null;
    }

    private static int faction(MatchState s, int index) {
        return s.identity().faction(index);
    }
}
