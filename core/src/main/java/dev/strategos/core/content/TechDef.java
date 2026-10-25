package dev.strategos.core.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.TechId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * A technology as data (TECH-01, spec §2.1): {@code data/<ns>/strategos/techs/<path>.json}. {@code cost} is resource
 * name to units, as a template's {@code Cost/resources}; {@code time} is seconds, rounded once to ticks like a
 * template's {@code Cost/time}; {@code requires} is a requirement tree; {@code supersedes} names the tech this one
 * replaces; {@code effects} are modifiers research hands to the stat service.
 */
@CoreApi
public record TechDef(
        TechId id,
        int netIndex,
        String name,
        String icon,
        List<String> classes,
        Map<String, Double> cost,
        double time,
        Requirement requires,
        @Nullable TechId supersedes,
        List<TechEffect> effects) {
    private static final String KEYS = "name, icon, classes, cost, time, requires, supersedes, effects";
    private static final String EFFECT_KEYS = "path, op, value, tokens, filter, stage, priority";

    public TechDef {
        classes = List.copyOf(classes);
        cost = Collections.unmodifiableMap(new TreeMap<>(cost));
        effects = List.copyOf(effects);
    }

    /** The research time in ticks: seconds × 20 rounded once, at least one, as a template's {@code Cost/time}. */
    public long ticks() {
        return Double.isFinite(time) && time > 0 ? Math.max(1, Math.round(time * 20)) : 1;
    }

    public TechDef atIndex(int index) {
        return new TechDef(id, index, name, icon, classes, cost, time, requires, supersedes, effects);
    }

    /** The canonical JSON the set hashes and ships: defaults written out, the id and index left to the key. */
    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("name", name);
        o.addProperty("icon", icon);
        JsonArray cls = new JsonArray();
        classes.forEach(cls::add);
        o.add("classes", cls);
        JsonObject costs = new JsonObject();
        new TreeMap<>(cost).forEach(costs::addProperty);
        o.add("cost", costs);
        o.addProperty("time", time);
        if (!requires.equals(Requirement.none())) {
            o.add("requires", requires.toJson());
        }
        if (supersedes != null) {
            o.addProperty("supersedes", supersedes.value());
        }
        JsonArray list = new JsonArray();
        for (TechEffect e : effects) {
            JsonObject fx = new JsonObject();
            fx.addProperty("path", e.path());
            fx.addProperty("op", e.op().name().toLowerCase(Locale.ROOT));
            if (e.op() == TechEffect.Op.TOKENS) {
                fx.addProperty("tokens", e.tokens());
            } else {
                fx.addProperty("value", e.value());
            }
            fx.addProperty("filter", e.filter());
            fx.addProperty("stage", e.stage());
            fx.addProperty("priority", e.priority());
            list.add(fx);
        }
        o.add("effects", list);
        return o;
    }

    /**
     * Decodes a tech file; null after recording each problem with {@code file} and the JSON pointer. References to
     * other techs are checked by the compiler, which knows them all.
     */
    public static @Nullable TechDef fromJson(
            TechId id, int netIndex, JsonObject json, String file, List<TemplateProblem> problems) {
        int before = problems.size();
        for (String key : new TreeSet<>(json.keySet())) {
            if (!List.of(KEYS.split(", ")).contains(key)) {
                problems.add(TemplateProblem.error(file, "/" + key, "unknown key '" + key + "'; known here: " + KEYS));
            }
        }
        String name = string(json, "name", id.value(), file, problems);
        String icon = string(json, "icon", null, file, problems);
        if (json.get("icon") instanceof JsonPrimitive p && p.isString() && icon.isEmpty()) {
            problems.add(TemplateProblem.error(file, "/icon", "an icon is not empty"));
        }
        List<String> classes = new ArrayList<>();
        JsonElement cls = json.get("classes");
        if (cls != null) {
            if (!cls.isJsonArray()) {
                problems.add(TemplateProblem.error(file, "/classes", "expected an array of class names"));
            } else {
                for (int i = 0; i < cls.getAsJsonArray().size(); i++) {
                    JsonElement c = cls.getAsJsonArray().get(i);
                    if (isString(c) && !c.getAsString().isBlank()) {
                        classes.add(c.getAsString());
                    } else {
                        problems.add(TemplateProblem.error(file, "/classes/" + i, "expected a class name string"));
                    }
                }
            }
        }
        Map<String, Double> cost = new TreeMap<>();
        JsonElement costs = json.get("cost");
        if (costs != null) {
            if (!costs.isJsonObject()) {
                problems.add(TemplateProblem.error(file, "/cost", "expected an object of resource amounts"));
            } else {
                for (Map.Entry<String, JsonElement> e :
                        costs.getAsJsonObject().asMap().entrySet()) {
                    JsonElement v = e.getValue();
                    if (!isNumber(v) || !(v.getAsDouble() >= 0) || !Double.isFinite(v.getAsDouble())) {
                        problems.add(TemplateProblem.error(
                                file, "/cost/" + e.getKey(), "expected an amount of 0 or more, got " + v));
                    } else {
                        cost.put(e.getKey(), v.getAsDouble());
                    }
                }
            }
        }
        double time = 0;
        JsonElement t = json.get("time");
        if (t == null || !isNumber(t) || !(t.getAsDouble() > 0) || !Double.isFinite(t.getAsDouble())) {
            problems.add(TemplateProblem.error(file, "/time", "expected seconds above 0, got " + t));
        } else {
            time = t.getAsDouble();
        }
        Requirement requires = Requirement.none();
        if (json.has("requires")) {
            Requirement r = Requirement.fromJson(json.get("requires"), file, "/requires", problems);
            if (r != null) {
                requires = r;
            }
        }
        TechId supersedes = null;
        JsonElement sup = json.get("supersedes");
        if (sup != null) {
            if (!isString(sup)) {
                problems.add(TemplateProblem.error(file, "/supersedes", "expected a tech id string, got " + sup));
            } else {
                try {
                    supersedes = new TechId(sup.getAsString());
                } catch (IllegalArgumentException e) {
                    problems.add(TemplateProblem.error(file, "/supersedes", String.valueOf(e.getMessage())));
                }
            }
        }
        List<TechEffect> effects = new ArrayList<>();
        JsonElement fx = json.get("effects");
        if (fx != null) {
            if (!fx.isJsonArray()) {
                problems.add(TemplateProblem.error(file, "/effects", "expected an array of effects"));
            } else {
                for (int i = 0; i < fx.getAsJsonArray().size(); i++) {
                    TechEffect e = effect(fx.getAsJsonArray().get(i), file, "/effects/" + i, problems);
                    if (e != null) {
                        effects.add(e);
                    }
                }
            }
        }
        if (problems.size() > before) {
            return null;
        }
        return new TechDef(id, netIndex, name, icon, classes, cost, time, requires, supersedes, effects);
    }

    private static @Nullable TechEffect effect(
            JsonElement json, String file, String at, List<TemplateProblem> problems) {
        if (!json.isJsonObject()) {
            problems.add(TemplateProblem.error(file, at, "expected an effect object"));
            return null;
        }
        JsonObject o = json.getAsJsonObject();
        int before = problems.size();
        for (String key : new TreeSet<>(o.keySet())) {
            if (!List.of(EFFECT_KEYS.split(", ")).contains(key)) {
                problems.add(TemplateProblem.error(
                        file, at + "/" + key, "unknown key '" + key + "'; known here: " + EFFECT_KEYS));
            }
        }
        String path = "";
        JsonElement p = o.get("path");
        if (p == null || !isString(p) || p.getAsString().isBlank()) {
            problems.add(TemplateProblem.error(file, at + "/path", "expected a value path string"));
        } else {
            path = p.getAsString();
        }
        TechEffect.Op op = TechEffect.Op.MUL;
        JsonElement opJson = o.get("op");
        TechEffect.Op parsed = null;
        if (opJson != null && isString(opJson)) {
            for (TechEffect.Op candidate : TechEffect.Op.values()) {
                if (candidate.name().equalsIgnoreCase(opJson.getAsString())) {
                    parsed = candidate;
                }
            }
        }
        if (opJson == null) {
            problems.add(TemplateProblem.error(file, at + "/op", "missing op; known: replace, mul, add, tokens"));
        } else if (parsed == null) {
            problems.add(TemplateProblem.error(
                    file, at + "/op", "unknown op " + opJson + "; known: replace, mul, add, tokens"));
        } else {
            op = parsed;
        }
        double value = 0;
        String tokens = null;
        if (parsed != null) {
            if (op == TechEffect.Op.TOKENS) {
                JsonElement tk = o.get("tokens");
                if (tk == null || !isString(tk)) {
                    problems.add(TemplateProblem.error(file, at + "/tokens", "a TOKENS effect needs tokens, '+a -b'"));
                } else if (!StatSyntax.validTokenEdit(tk.getAsString())) {
                    problems.add(
                            TemplateProblem.error(file, at + "/tokens", "a TOKENS effect edits by '+a -b', not " + tk));
                } else {
                    tokens = tk.getAsString();
                }
                if (o.has("value")) {
                    problems.add(TemplateProblem.error(file, at + "/value", "a TOKENS effect has no value"));
                }
            } else {
                JsonElement v = o.get("value");
                if (v == null || !isNumber(v) || !Double.isFinite(v.getAsDouble())) {
                    problems.add(TemplateProblem.error(file, at + "/value", "a " + op + " effect needs a number"));
                } else {
                    value = v.getAsDouble();
                }
                if (o.has("tokens")) {
                    problems.add(TemplateProblem.error(file, at + "/tokens", "a " + op + " effect has no tokens"));
                }
            }
        }
        String filter = "";
        JsonElement f = o.get("filter");
        if (f != null) {
            if (!isString(f)) {
                problems.add(TemplateProblem.error(file, at + "/filter", "expected a class filter string"));
            } else {
                filter = f.getAsString();
                if (!StatSyntax.validFilter(filter)) {
                    problems.add(
                            TemplateProblem.error(file, at + "/filter", "malformed class filter '" + filter + "'"));
                }
            }
        }
        int stage = integer(o, "stage", at, file, problems);
        int priority = integer(o, "priority", at, file, problems);
        if (problems.size() > before) {
            return null;
        }
        return new TechEffect(path, op, value, tokens, filter, stage, priority);
    }

    private static int integer(JsonObject o, String key, String at, String file, List<TemplateProblem> problems) {
        JsonElement e = o.get(key);
        if (e == null) {
            return 0;
        }
        if (!isNumber(e) || e.getAsDouble() != Math.rint(e.getAsDouble()) || Math.abs(e.getAsDouble()) > 1_000_000) {
            problems.add(TemplateProblem.error(file, at + "/" + key, "expected a whole number, got " + e));
            return 0;
        }
        return e.getAsInt();
    }

    private static String string(
            JsonObject o, String key, @Nullable String fallback, String file, List<TemplateProblem> problems) {
        JsonElement e = o.get(key);
        if (e == null) {
            if (fallback == null) {
                problems.add(TemplateProblem.error(file, "/" + key, "missing " + key));
                return "";
            }
            return fallback;
        }
        if (!isString(e)) {
            problems.add(TemplateProblem.error(file, "/" + key, "expected a string, got " + e));
            return "";
        }
        return e.getAsString();
    }

    private static boolean isString(JsonElement e) {
        return e instanceof JsonPrimitive p && p.isString();
    }

    private static boolean isNumber(JsonElement e) {
        return e instanceof JsonPrimitive p && p.isNumber();
    }
}
