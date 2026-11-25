package dev.strategos.core.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * An ability as a template declares it under {@code Abilities/<id>} (UNIT-10, 04 §7.6): what it is aimed at, how far it
 * reaches, the ticks it rests after use, what it costs, whether an idle owner uses it by itself and the effects it has.
 * Compiled with the template; {@code effects} stay as their kinds and parameters, which {@code sim.ability} turns into
 * behaviour. {@code range} is the gap between the edges of the user and its target (the radius of an area is about its
 * centre); {@code cost} is by resource id, in whole units, paid when the ability fires.
 */
public record AbilityDef(
        String id,
        Aim aim,
        double range,
        double radius,
        int cooldown,
        Map<String, Double> cost,
        boolean autocast,
        String pick,
        List<Effect> effects) {
    /** What an ability is aimed at. */
    public enum Aim {
        NONE,
        POINT,
        UNIT,
        AREA;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        static @Nullable Aim of(String id) {
            for (Aim aim : values()) {
                if (aim.id().equals(id)) {
                    return aim;
                }
            }
            return null;
        }
    }

    /** One effect: its kind and its parameters, checked by {@link AbilityKinds}. */
    public record Effect(String kind, JsonObject params) {
        public Effect {
            params = params.deepCopy();
        }

        @Override
        public JsonObject params() {
            return params.deepCopy();
        }
    }

    public static final double MAX_RANGE = 128;
    public static final int MAX_COOLDOWN = 72_000;
    public static final int MAX_EFFECTS = 8;

    public AbilityDef {
        cost = Collections.unmodifiableMap(new TreeMap<>(cost));
        effects = List.copyOf(effects);
    }

    /** Whether this ability has a timed effect (its modifiers come off again). */
    public @Nullable Effect timed() {
        for (Effect e : effects) {
            if (AbilityKinds.timed(e.kind())) {
                return e;
            }
        }
        return null;
    }

    /**
     * The ability {@code id} from {@code json}, the object under {@code Abilities/<id>} of {@code file}; null after
     * recording at {@code pointer}'s children what is wrong.
     */
    static @Nullable AbilityDef parse(String id, JsonObject json, String pointer, Fields f) {
        List<TemplateProblem> problems = f.problems();
        int before = problems.size();
        f.known(json, pointer, "target", "range", "radius", "cooldown", "Cost", "autocast", "pick", "effects");
        Aim aim = null;
        JsonElement t = json.get("target");
        if (t instanceof JsonPrimitive p && p.isString()) {
            aim = Aim.of(p.getAsString());
        }
        if (aim == null) {
            problems.add(TemplateProblem.error(
                    f.file(),
                    pointer + "/target",
                    "expected none, point, unit or area" + (t == null ? "" : ", got " + t)));
            aim = Aim.NONE;
        }
        double range = json.has("range") ? number(json, pointer, "range", MAX_RANGE, f) : 0;
        double radius = 0;
        if (aim == Aim.AREA) {
            AbilityKinds.number(json, pointer, "radius", 0, MAX_RANGE, false, f);
            radius = read(json, "radius");
        } else if (json.has("radius")) {
            problems.add(TemplateProblem.error(f.file(), pointer + "/radius", "only an area ability has a radius"));
        }
        int cooldown = 0;
        if (json.has("cooldown")) {
            AbilityKinds.integer(json, pointer, "cooldown", 0, MAX_COOLDOWN, f);
            cooldown = json.get("cooldown").isJsonPrimitive()
                            && json.get("cooldown").getAsJsonPrimitive().isNumber()
                    ? (int) json.get("cooldown").getAsDouble()
                    : 0;
        }
        Map<String, Double> cost = cost(json, pointer, f);
        boolean autocast = false;
        JsonElement a = json.get("autocast");
        if (a != null) {
            if (a instanceof JsonPrimitive p && p.isBoolean()) {
                autocast = p.getAsBoolean();
            } else {
                problems.add(TemplateProblem.error(f.file(), pointer + "/autocast", "expected true or false"));
            }
        }
        String pick = picker(json, pointer, aim, autocast, f);
        List<Effect> effects = effects(json, pointer, aim, f);
        if (problems.size() > before) {
            return null;
        }
        return new AbilityDef(id, aim, range, radius, cooldown, cost, autocast, pick, effects);
    }

    private static double number(JsonObject json, String pointer, String key, double max, Fields f) {
        AbilityKinds.number(json, pointer, key, 0, max, true, f);
        return read(json, key);
    }

    private static double read(JsonObject json, String key) {
        JsonElement e = json.get(key);
        return e instanceof JsonPrimitive p && p.isNumber() && Double.isFinite(p.getAsDouble()) ? p.getAsDouble() : 0;
    }

    private static Map<String, Double> cost(JsonObject json, String pointer, Fields f) {
        Map<String, Double> out = new TreeMap<>();
        JsonObject cost = f.object(json, pointer, "Cost", false);
        if (cost == null) {
            return out;
        }
        f.known(cost, pointer + "/Cost", "resources");
        JsonObject resources = f.object(cost, pointer + "/Cost", "resources", false);
        if (resources == null) {
            return out;
        }
        String at = pointer + "/Cost/resources";
        for (Map.Entry<String, JsonElement> e : new TreeMap<>(resources.asMap()).entrySet()) {
            if (e.getValue() instanceof JsonPrimitive p
                    && p.isNumber()
                    && Double.isFinite(p.getAsDouble())
                    && p.getAsDouble() >= 0
                    && p.getAsDouble() <= 1_000_000) {
                out.put(e.getKey(), p.getAsDouble());
            } else {
                f.problems()
                        .add(TemplateProblem.error(
                                f.file(), at + "/" + e.getKey(), "expected an amount from 0 to 1000000"));
            }
        }
        return out;
    }

    private static String picker(JsonObject json, String pointer, Aim aim, boolean autocast, Fields f) {
        JsonElement e = json.get("pick");
        if (e == null) {
            if (autocast) {
                f.problems()
                        .add(TemplateProblem.error(f.file(), pointer + "/pick", "an autocast ability needs a pick"));
            }
            return "";
        }
        if (!(e instanceof JsonPrimitive p)
                || !p.isString()
                || !AbilityKinds.pickers().contains(p.getAsString())) {
            f.problems()
                    .add(TemplateProblem.error(
                            f.file(), pointer + "/pick", "expected one of " + AbilityKinds.pickers() + ", got " + e));
            return "";
        }
        String pick = p.getAsString();
        if (!autocast) {
            f.problems().add(TemplateProblem.error(f.file(), pointer + "/pick", "a pick is for an autocast ability"));
        } else if (!AbilityKinds.pickerAims(pick).contains(aim)) {
            f.problems()
                    .add(TemplateProblem.error(
                            f.file(), pointer + "/pick", "'" + pick + "' cannot aim a " + aim.id() + " ability"));
        }
        return pick;
    }

    private static List<Effect> effects(JsonObject json, String pointer, Aim aim, Fields f) {
        List<TemplateProblem> problems = f.problems();
        String at = pointer + "/effects";
        JsonElement e = json.get("effects");
        if (!(e instanceof JsonArray list) || list.isEmpty() || list.size() > MAX_EFFECTS) {
            problems.add(TemplateProblem.error(f.file(), at, "expected a list of 1 to " + MAX_EFFECTS + " effects"));
            return List.of();
        }
        List<Effect> out = new ArrayList<>();
        int timed = 0;
        for (int k = 0; k < list.size(); k++) {
            String ep = at + "/" + k;
            if (!list.get(k).isJsonObject()) {
                problems.add(TemplateProblem.error(f.file(), ep, "expected {kind, params}"));
                continue;
            }
            JsonObject effect = list.get(k).getAsJsonObject();
            f.known(effect, ep, "kind", "params");
            JsonElement kind = effect.get("kind");
            if (!(kind instanceof JsonPrimitive p)
                    || !p.isString()
                    || !AbilityKinds.effects().contains(p.getAsString())) {
                problems.add(TemplateProblem.error(
                        f.file(), ep + "/kind", "expected one of " + AbilityKinds.effects() + ", got " + kind));
                continue;
            }
            String name = p.getAsString();
            JsonObject params = f.object(effect, ep, "params", false);
            if (params == null) {
                params = new JsonObject();
            }
            AbilityKinds.check(name, params, ep + "/params", f);
            if (!AbilityKinds.aims(name).contains(aim)) {
                problems.add(TemplateProblem.error(
                        f.file(), ep + "/kind", "'" + name + "' does not fit a " + aim.id() + " ability"));
            }
            if (AbilityKinds.timed(name) && ++timed > 1) {
                problems.add(TemplateProblem.error(
                        f.file(),
                        ep + "/kind",
                        "an ability has at most one timed effect (apply_modifiers, capture_strength)"));
            }
            out.add(new Effect(name, params));
        }
        return out;
    }
}
