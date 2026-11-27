package dev.strategos.core.sim.ability;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.strategos.core.content.AbilityDef;
import dev.strategos.core.content.AbilityKinds;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.MotionStore;
import dev.strategos.core.sim.build.Placing;
import dev.strategos.core.sim.combat.Damage;
import dev.strategos.core.sim.combat.DamageType;
import dev.strategos.core.sim.spatial.UnitFilter;
import dev.strategos.core.sim.stats.Modifier;
import dev.strategos.core.sim.stats.ModifierOp;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import org.jspecify.annotations.Nullable;

/**
 * The effect primitives of abilities (UNIT-10, 04 §7.6), a closed registry like {@code RuleCatalogue}: {@code damage},
 * {@code heal}, {@code apply_modifiers}, {@code spawn}, {@code capture_strength} and {@code teleport}, under the ids
 * {@link AbilityKinds#effects()} lists. {@link #compile} turns a definition's effect specs (whose parameters the content
 * lint has checked) into effects once; an effect runs through the pipelines the rest of the core uses: damage packets
 * for {@code CombatSystem}, the hit-point store, personal modifiers, the spawn path and the body's position.
 */
public final class AbilityEffects {
    private AbilityEffects() {}

    /** One use of an ability: who, which, and where it lands ({@code x}, {@code z}; {@code target} a unit or 0). */
    public record Cast(int user, AbilityDef def, int abilityIndex, int target, double x, double z) {}

    /** An effect: what it refuses before the cost is paid, and what it does. */
    public interface Effect {
        /** Why this cast cannot take effect, or null; asked before the ability is paid for. */
        default @Nullable RejectReason refusal(MatchState s, Cast cast) {
            return null;
        }

        void apply(MatchState s, Cast cast);
    }

    /** The source id an ability's timed modifiers go under: {@code MIN_VALUE + 1 + abilityNetIndex} (M10 spec §0.3). */
    public static int source(int abilityIndex) {
        return Integer.MIN_VALUE + 1 + abilityIndex;
    }

    /** The ids of the registry, ascending: the same as {@link AbilityKinds#effects()}. */
    public static List<String> ids() {
        return List.of("apply_modifiers", "capture_strength", "damage", "heal", "spawn", "teleport");
    }

    /** The effects of {@code def}, in order. */
    static Effect[] compile(AbilityDef def) {
        Effect[] out = new Effect[def.effects().size()];
        for (int k = 0; k < out.length; k++) {
            AbilityDef.Effect spec = def.effects().get(k);
            out[k] = create(spec.kind(), spec.params());
        }
        return out;
    }

    /** The effect {@code kind} with {@code params}; an id outside the registry throws. */
    static Effect create(String kind, JsonObject params) {
        return switch (kind) {
            case AbilityKinds.DAMAGE ->
                new DamageEffect(
                        switch (params.get("type").getAsString()) {
                            case "pierce" -> DamageType.PIERCE;
                            case "crush" -> DamageType.CRUSH;
                            default -> DamageType.HACK;
                        },
                        params.get("amount").getAsFloat());
            case AbilityKinds.HEAL -> new HealEffect(params.get("amount").getAsDouble());
            case AbilityKinds.APPLY_MODIFIERS, AbilityKinds.CAPTURE_STRENGTH ->
                new TimedEffect(params.get("ticks").getAsInt(), Relation.of(params));
            case AbilityKinds.SPAWN ->
                new SpawnEffect(
                        params.get("template").getAsString(),
                        params.has("count") ? params.get("count").getAsInt() : 1);
            case AbilityKinds.TELEPORT -> new TeleportEffect();
            default -> throw new IllegalArgumentException("no ability effect '" + kind + "'");
        };
    }

    /** The modifiers {@code def}'s timed effect gives, all under {@code source}; none when it has no timed effect. */
    static List<Modifier> modifiers(AbilityDef def, int source) {
        AbilityDef.Effect timed = def.timed();
        List<Modifier> out = new ArrayList<>();
        if (timed == null) {
            return out;
        }
        JsonObject params = timed.params();
        if (timed.kind().equals(AbilityKinds.CAPTURE_STRENGTH)) {
            out.add(Modifier.add(AbilityKinds.CAPTURE_PATH, params.get("amount").getAsDouble(), source));
            return out;
        }
        JsonArray list = params.getAsJsonArray("modifiers");
        for (JsonElement e : list) {
            JsonObject m = e.getAsJsonObject();
            String path = m.get("path").getAsString();
            double value = m.get("value").getAsDouble();
            out.add(
                    switch (m.get("op").getAsString()) {
                        case "mul" -> Modifier.mul(path, value, source);
                        case "replace" -> Modifier.replace(path, value, source);
                        default -> new Modifier(path, ModifierOp.ADD, value, "", "", 0, 0, source);
                    });
        }
        return out;
    }

    /** The ticks of def's timed effect, 0 when it has none. */
    static int ticks(AbilityDef def) {
        AbilityDef.Effect timed = def.timed();
        return timed == null ? 0 : timed.params().get("ticks").getAsInt();
    }

    /** Whom an effect reaches among the units it is aimed at. */
    enum Relation {
        /** The user's enemies. */
        HOSTILE,
        /** The user's own and its allies'. */
        FRIEND,
        /** Anyone. */
        ANY;

        static Relation of(JsonObject params) {
            JsonElement on = params.get("on");
            if (on == null) {
                return FRIEND;
            }
            return switch (on.getAsString()) {
                case "enemy" -> HOSTILE;
                case "any" -> ANY;
                default -> FRIEND;
            };
        }
    }

    /**
     * Calls {@code each} with the handle of every unit the cast reaches, in handle order: the target of a unit
     * ability, the units of the area round its point that {@code relation} lets through or, aimless, the user.
     */
    static void recipients(MatchState s, Cast cast, Relation relation, IntConsumer each) {
        AbilityDef def = cast.def();
        if (def.aim() == AbilityDef.Aim.NONE) {
            each.accept(cast.user());
        } else if (def.aim() == AbilityDef.Aim.UNIT) {
            if (s.entities().isAlive(cast.target())) {
                each.accept(cast.target());
            }
        } else if (def.aim() == AbilityDef.Aim.AREA) {
            int faction = s.identity().faction(Handle.index(cast.user()));
            int players = s.config().factions();
            int mask = 0;
            for (int f = 0; f < players; f++) {
                boolean friend = s.allies().allied(faction, f) || f == faction;
                if (relation == Relation.ANY || friend == (relation == Relation.FRIEND)) {
                    mask |= 1 << f;
                }
            }
            s.spatial().forEachInRadius(cast.x(), cast.z(), def.radius(), new UnitFilter(mask, 0), i -> {
                if (s.entities().aliveAt(i) && !s.garrisoned(i)) {
                    each.accept(s.entities().handleAt(i));
                }
            });
        }
    }

    /** Whether {@code target} (a unit handle) is allied to {@code user}'s faction. */
    static boolean friendly(MatchState s, int user, int target) {
        int a = s.identity().faction(Handle.index(user));
        int b = s.identity().faction(Handle.index(target));
        return a == b || s.allies().allied(a, b);
    }

    /** Whether unit {@code handle} is flesh, which a heal mends: {@code Organic}, and not {@code Siege}. */
    static boolean mendable(MatchState s, int handle) {
        List<String> classes = s.stats().classes(Handle.index(handle));
        return classes.contains("Organic") && !classes.contains("Siege");
    }

    /** The refusal for aiming at unit {@code target} (or, aimless, the user) when only {@code relation} may be hit. */
    static @Nullable RejectReason relationRefusal(MatchState s, Cast cast, Relation relation) {
        if (cast.def().aim() != AbilityDef.Aim.UNIT || relation == Relation.ANY) {
            return null;
        }
        return friendly(s, cast.user(), cast.target()) == (relation == Relation.FRIEND)
                ? null
                : RejectReason.INVALID_ARGUMENT;
    }

    private record DamageEffect(DamageType type, float amount) implements Effect {
        @Override
        public @Nullable RejectReason refusal(MatchState s, Cast cast) {
            return relationRefusal(s, cast, Relation.HOSTILE);
        }

        @Override
        public void apply(MatchState s, Cast cast) {
            recipients(s, cast, Relation.HOSTILE, t -> s.queueDamage(Damage.of(t, cast.user(), type, amount)));
        }
    }

    private record HealEffect(double amount) implements Effect {
        @Override
        public @Nullable RejectReason refusal(MatchState s, Cast cast) {
            RejectReason why = relationRefusal(s, cast, Relation.FRIEND);
            boolean one = cast.def().aim() == AbilityDef.Aim.UNIT || cast.def().aim() == AbilityDef.Aim.NONE;
            return why != null || (one && !mendable(s, cast.target())) ? RejectReason.INVALID_ARGUMENT : null;
        }

        @Override
        public void apply(MatchState s, Cast cast) {
            recipients(s, cast, Relation.FRIEND, t -> {
                if (mendable(s, t)) { // buildings and siege are repaired, not healed
                    int i = Handle.index(t);
                    s.health().setDamage(i, Math.max(0, s.health().damage(i) - amount)); // never past the most
                }
            });
        }
    }

    /** {@code apply_modifiers} and {@code capture_strength}: the definition's modifiers on every recipient for N ticks. */
    private record TimedEffect(int ticks, Relation on) implements Effect {
        @Override
        public @Nullable RejectReason refusal(MatchState s, Cast cast) {
            return relationRefusal(s, cast, on);
        }

        @Override
        public void apply(MatchState s, Cast cast) {
            int source = source(cast.abilityIndex());
            List<Modifier> modifiers = s.play().abilities().modifiers(s.templates(), cast.def());
            long expiry = s.tick() + ticks;
            recipients(
                    s,
                    cast,
                    on,
                    t -> s.play()
                            .abilities()
                            .timed()
                            .apply(s.stats(), t, cast.def().id(), source, expiry, modifiers));
        }
    }

    private record SpawnEffect(String template, int count) implements Effect {
        /** The cell centre unit {@code k} of the batch stands on, or null where nothing can stand: off the map, in a footprint, no land. */
        private double @Nullable [] spot(MatchState s, Cast cast, int k) {
            Vec2d at = s.config().bounds().clamp(new Vec2d(cast.x() + (k - (count - 1) / 2.0), cast.z()), 0.5);
            int cx = (int) Math.floor(at.x());
            int cz = (int) Math.floor(at.z());
            double floor = Placing.standFloor(
                    s.surfacesFor(Handle.index(cast.user())), s.config().bounds(), cx, cz);
            return Double.isNaN(floor) || s.footprintAt(cx, cz) ? null : new double[] {at.x(), floor, at.z()};
        }

        @Override
        public @Nullable RejectReason refusal(MatchState s, Cast cast) {
            UnitTemplate t = s.templates().byId(new TemplateId(template));
            if (t == null || t.isBuilding()) {
                return RejectReason.UNKNOWN_TEMPLATE;
            }
            if (s.entities().aliveCount() + count > s.config().rules().unitCap()) {
                return RejectReason.UNIT_CAP;
            }
            for (int k = 0; k < count; k++) {
                if (spot(s, cast, k) != null) {
                    return null;
                }
            }
            return RejectReason.BLOCKED;
        }

        @Override
        public void apply(MatchState s, Cast cast) {
            UnitTemplate t = s.templates().byId(new TemplateId(template));
            if (t == null || t.isBuilding()) {
                return;
            }
            int faction = s.identity().faction(Handle.index(cast.user()));
            for (int k = 0; k < count; k++) {
                double[] at = spot(s, cast, k);
                if (at != null && s.entities().aliveCount() < s.config().rules().unitCap()) {
                    s.spawnUnit(t.netIndex(), faction, at[0], at[1], at[2], 0f);
                }
            }
        }
    }

    private record TeleportEffect() implements Effect {
        @Override
        public @Nullable RejectReason refusal(MatchState s, Cast cast) {
            if (!s.config().bounds().contains(cast.x(), cast.z())) {
                return RejectReason.OUT_OF_BOUNDS;
            }
            int cx = (int) Math.floor(cast.x());
            int cz = (int) Math.floor(cast.z());
            double floor = Placing.standFloor(
                    s.surfacesFor(Handle.index(cast.user())), s.config().bounds(), cx, cz);
            return Double.isNaN(floor) || s.footprintAt(cx, cz) ? RejectReason.BLOCKED : null;
        }

        @Override
        public void apply(MatchState s, Cast cast) {
            int i = Handle.index(cast.user());
            int cx = (int) Math.floor(cast.x());
            int cz = (int) Math.floor(cast.z());
            double y = Placing.standFloor(s.surfacesFor(i), s.config().bounds(), cx, cz);
            MotionStore motion = s.motion();
            motion.set(i, cast.x(), y, cast.z(), motion.yaw(i));
            s.frame().add(cast.user(), cast.x(), y, cast.z(), motion.yaw(i), (byte) 0);
            motion.setLatch(i, false, 0, 0); // an arrival latch would walk it back
            s.paths().setRepath(i, true);
            s.play().abilities().markMoved(); // the system rebuilds the spatial grid once, not once a unit
        }
    }
}
