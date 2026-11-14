package dev.strategos.core.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.strategos.core.shared.Stance;
import java.util.List;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/** Reads typed fields, recording a problem at the field's pointer for anything missing or of the wrong type. */
record Fields(String file, List<TemplateProblem> problems) {
    /**
     * Records an error at each key of a component the core decodes that is not one of its fields: a typo. Other
     * components pass unchecked: their numbers are value paths, and later cards decode them.
     */
    void known(JsonObject object, String pointer, String... fields) {
        List<String> known = List.of(fields);
        for (String key : new TreeSet<>(object.keySet())) {
            if (!known.contains(key)) {
                problems.add(TemplateProblem.error(
                        file, pointer + "/" + key, "unknown key '" + key + "'; known here: " + known));
            }
        }
    }

    @Nullable JsonObject object(JsonObject parent, String pointer, String key, boolean required) {
        JsonElement e = parent.get(key);
        if (e == null) {
            if (required) {
                problems.add(TemplateProblem.error(file, pointer + "/" + key, "missing component " + key));
            }
            return null;
        }
        if (!e.isJsonObject()) {
            problems.add(TemplateProblem.error(file, pointer + "/" + key, "expected an object"));
            return null;
        }
        return e.getAsJsonObject();
    }

    String string(JsonObject parent, String pointer, String key, @Nullable String fallback) {
        JsonElement e = parent.get(key);
        if (e == null) {
            if (fallback == null) {
                problems.add(TemplateProblem.error(file, pointer + "/" + key, "missing " + key));
                return "";
            }
            return fallback;
        }
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) {
            problems.add(TemplateProblem.error(file, pointer + "/" + key, "expected a string, got " + e));
            return "";
        }
        return e.getAsString();
    }

    double positive(JsonObject parent, String pointer, String key, @Nullable Double fallback) {
        JsonElement e = parent.get(key);
        if (e == null) {
            if (fallback == null) {
                problems.add(TemplateProblem.error(file, pointer + "/" + key, "missing " + key));
                return 0;
            }
            return fallback;
        }
        if (!(e instanceof JsonPrimitive p) || !p.isNumber()) {
            problems.add(TemplateProblem.error(file, pointer + "/" + key, "expected a number, got " + e));
            return 0;
        }
        double v = p.getAsDouble();
        if (!(v > 0) || !Double.isFinite(v)) {
            problems.add(TemplateProblem.error(file, pointer + "/" + key, "expected a positive number, got " + e));
            return 0;
        }
        return v;
    }

    /** An attack's preferred classes, if it has any: a string of class tokens (CMB-05). */
    void preferred(JsonObject attack, String pointer) {
        if (attack.has("preferred")) {
            string(attack, pointer, "preferred", "");
        }
    }

    /**
     * A building's town arrows (CMB-07), if it has any: known keys, each number within its range, the counts whole,
     * and arrows that can fly (a pierce, a range and a period).
     */
    void buildingAttack(JsonObject building) {
        JsonObject attack = object(building, "", "Attack", false);
        JsonObject arrows = attack == null ? null : object(attack, "/Attack", "Building", false);
        if (arrows == null) return;
        known(
                arrows,
                "/Attack/Building",
                "arrows",
                "perGarrisoned",
                "interval",
                "pierce",
                "range",
                "speed",
                "spread",
                "preferred");
        preferred(arrows, "/Attack/Building");
        for (String path : CorePaths.buildingAttack()) {
            rangedNumber(arrows, path);
        }
        for (String path :
                List.of(CorePaths.BUILDING_ARROWS, CorePaths.BUILDING_PER_GARRISONED, CorePaths.BUILDING_INTERVAL)) {
            String key = path.substring(path.lastIndexOf('/') + 1);
            if (arrows.get(key) instanceof JsonPrimitive p && p.isNumber() && p.getAsDouble() % 1 != 0) {
                problems.add(TemplateProblem.error(file, "/" + path, "expected a whole number, got " + p));
            }
        }
        if (arrows.has("arrows")
                && arrows.get("arrows") instanceof JsonPrimitive n
                && n.isNumber()
                && n.getAsDouble() > 0) {
            for (String path : List.of(CorePaths.BUILDING_PIERCE, CorePaths.BUILDING_RANGE)) {
                String key = path.substring(path.lastIndexOf('/') + 1);
                if (!(arrows.get(key) instanceof JsonPrimitive p) || !p.isNumber() || !(p.getAsDouble() > 0)) {
                    problems.add(TemplateProblem.error(file, "/" + path, "arrows need a positive " + key));
                }
            }
            if (!arrows.has("interval")) {
                problems.add(TemplateProblem.error(file, "/" + CorePaths.BUILDING_INTERVAL, "arrows need an interval"));
            }
        }
    }

    /** A building's garrison (BLD-16), if it has one: known keys, a whole capacity of at least 1 and class tokens. */
    void garrisonHolder(JsonObject building) {
        JsonObject holder = object(building, "", "GarrisonHolder", false);
        if (holder == null) return;
        known(holder, "/GarrisonHolder", "capacity", "classes");
        rangedNumber(holder, CorePaths.GARRISON_CAPACITY);
        if (!(holder.get("capacity") instanceof JsonPrimitive n)
                || !n.isNumber()
                || n.getAsDouble() < 1
                || n.getAsDouble() % 1 != 0) {
            problems.add(TemplateProblem.error(
                    file, "/" + CorePaths.GARRISON_CAPACITY, "a garrison needs a whole capacity of at least 1"));
        }
        if (!(holder.get("classes") instanceof JsonPrimitive c)
                || !c.isString()
                || c.getAsString().isBlank()) {
            problems.add(TemplateProblem.error(
                    file, "/" + CorePaths.GARRISON_CLASSES, "a garrison needs the class tokens it admits"));
        }
    }

    /** A stance by name, or by the code of one (CMB-05). */
    void stance(JsonObject behaviour) {
        JsonElement e = behaviour.get("stance");
        if (e == null) return;
        String pointer = "/Behaviour/stance";
        if (e instanceof JsonPrimitive p && p.isString()) {
            if (Stance.byId(p.getAsString()) == null) {
                problems.add(TemplateProblem.error(
                        file,
                        pointer,
                        "unknown stance '" + p.getAsString()
                                + "'; expected violent, aggressive, defensive, stand_ground or passive"));
            }
        } else if (!(e instanceof JsonPrimitive n
                && n.isNumber()
                && Stance.of(n.getAsInt()) != null
                && n.getAsDouble() == n.getAsInt())) {
            problems.add(TemplateProblem.error(file, pointer, "expected a stance name, got " + e));
        }
    }

    void rangedNumber(JsonObject ranged, String path) {
        String key = path.substring(path.lastIndexOf('/') + 1);
        JsonElement e = ranged.get(key);
        if (e == null) return;
        String pointer = "/" + path.substring(0, path.lastIndexOf('/') + 1) + key;
        if (!(e instanceof JsonPrimitive p) || !p.isNumber()) {
            problems.add(TemplateProblem.error(file, pointer, "expected a number, got " + e));
            return;
        }
        double value = p.getAsDouble();
        if (!Double.isFinite(value) || value < CorePaths.min(path) || value > CorePaths.max(path)) {
            problems.add(TemplateProblem.error(file, pointer, "number outside its allowed range: " + e));
        }
    }
}
