package dev.strategos.core.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.TechId;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import org.jspecify.annotations.Nullable;

/**
 * A requirement tree (TECH-01, spec §2.1): {@code all}/{@code any} of requirements, a researched {@code tech}, a reached
 * {@code phase}, or {@code buildings} (own COMPLETE buildings matching a class filter). In JSON an object with exactly
 * one of those keys. {@link Requirements} evaluates it.
 */
@CoreApi
public sealed interface Requirement {
    /** Every member holds; none at all holds. */
    record All(List<Requirement> of) implements Requirement {
        public All {
            of = List.copyOf(of);
        }
    }

    /** One member holds; none at all does not. */
    record Any(List<Requirement> of) implements Requirement {
        public Any {
            of = List.copyOf(of);
        }
    }

    record Tech(TechId tech) implements Requirement {}

    record Phase(String name) implements Requirement {}

    /** A phase name: bare and lower case, the tail of its tech id {@code strategos:phase_<name>}. */
    java.util.regex.Pattern PHASE_NAME = java.util.regex.Pattern.compile("[a-z][a-z0-9_]*");

    record Buildings(String filter, int count) implements Requirement {}

    /** What a missing {@code requires} means. */
    static Requirement none() {
        return new All(List.of());
    }

    /** Decodes {@code json} at {@code pointer}; null after recording what is wrong. */
    static @Nullable Requirement fromJson(
            JsonElement json, String file, String pointer, List<TemplateProblem> problems) {
        if (!json.isJsonObject() || json.getAsJsonObject().size() != 1) {
            problems.add(TemplateProblem.error(
                    file, pointer, "a requirement is an object with exactly one of all, any, tech, phase, buildings"));
            return null;
        }
        String key = json.getAsJsonObject().keySet().iterator().next();
        JsonElement value = json.getAsJsonObject().get(key);
        String at = pointer + "/" + key;
        switch (key) {
            case "all", "any" -> {
                if (!value.isJsonArray()) {
                    problems.add(TemplateProblem.error(file, at, "expected an array of requirements"));
                    return null;
                }
                List<Requirement> members = new ArrayList<>();
                boolean ok = true;
                JsonArray array = value.getAsJsonArray();
                for (int i = 0; i < array.size(); i++) {
                    Requirement member = fromJson(array.get(i), file, at + "/" + i, problems);
                    if (member == null) {
                        ok = false;
                    } else {
                        members.add(member);
                    }
                }
                return !ok ? null : key.equals("all") ? new All(members) : new Any(members);
            }
            case "tech" -> {
                if (!isString(value)) {
                    problems.add(TemplateProblem.error(file, at, "expected a tech id string, got " + value));
                    return null;
                }
                try {
                    return new Tech(new TechId(value.getAsString()));
                } catch (IllegalArgumentException e) {
                    problems.add(TemplateProblem.error(file, at, String.valueOf(e.getMessage())));
                    return null;
                }
            }
            case "phase" -> {
                if (!isString(value) || value.getAsString().isBlank()) {
                    problems.add(TemplateProblem.error(file, at, "expected a phase name string, got " + value));
                    return null;
                }
                if (!PHASE_NAME.matcher(value.getAsString()).matches()
                        || value.getAsString().startsWith("phase_")) {
                    problems.add(TemplateProblem.error(
                            file,
                            at,
                            "a phase is named bare, lower case, as 'town' (not 'phase_town'), got '"
                                    + value.getAsString() + "'"));
                    return null;
                }
                return new Phase(value.getAsString());
            }
            case "buildings" -> {
                return buildings(value, file, at, problems);
            }
            default -> {
                problems.add(TemplateProblem.error(
                        file, at, "unknown requirement '" + key + "'; known: all, any, tech, phase, buildings"));
                return null;
            }
        }
    }

    private static @Nullable Requirement buildings(
            JsonElement value, String file, String at, List<TemplateProblem> problems) {
        if (!value.isJsonObject()) {
            problems.add(TemplateProblem.error(file, at, "expected {\"filter\": ..., \"count\": n}"));
            return null;
        }
        JsonObject o = value.getAsJsonObject();
        int before = problems.size();
        for (String k : o.keySet()) {
            if (!k.equals("filter") && !k.equals("count")) {
                problems.add(
                        TemplateProblem.error(file, at + "/" + k, "unknown key '" + k + "'; known: filter, count"));
            }
        }
        String filter = "";
        JsonElement f = o.get("filter");
        if (f == null || !isString(f)) {
            problems.add(TemplateProblem.error(file, at + "/filter", "expected a class filter string"));
        } else {
            filter = f.getAsString();
            if (!StatSyntax.validFilter(filter)) {
                problems.add(TemplateProblem.error(file, at + "/filter", "malformed class filter '" + filter + "'"));
            }
        }
        int count = 0;
        JsonElement c = o.get("count");
        if (c == null
                || !c.isJsonPrimitive()
                || !c.getAsJsonPrimitive().isNumber()
                || c.getAsDouble() != Math.rint(c.getAsDouble())
                || c.getAsDouble() < 1
                || c.getAsDouble() > 1_000_000) {
            problems.add(TemplateProblem.error(file, at + "/count", "expected a whole number of at least 1, got " + c));
        } else {
            count = c.getAsInt();
        }
        return problems.size() > before ? null : new Buildings(filter, count);
    }

    private static boolean isString(JsonElement e) {
        return e.isJsonPrimitive() && e.getAsJsonPrimitive().isString();
    }

    /** The JSON form {@link #fromJson} reads back. */
    default JsonObject toJson() {
        JsonObject o = new JsonObject();
        switch (this) {
            case All all -> o.add("all", array(all.of()));
            case Any any -> o.add("any", array(any.of()));
            case Tech t -> o.addProperty("tech", t.tech().value());
            case Phase p -> o.addProperty("phase", p.name());
            case Buildings b -> {
                JsonObject inner = new JsonObject();
                inner.addProperty("filter", b.filter());
                inner.addProperty("count", b.count());
                o.add("buildings", inner);
            }
        }
        return o;
    }

    private static JsonArray array(List<Requirement> members) {
        JsonArray a = new JsonArray();
        members.forEach(m -> a.add(m.toJson()));
        return a;
    }

    /** Calls {@code visit} with every buildings leaf and the JSON pointer of its count, below {@code pointer}. */
    default void buildingCounts(String pointer, BiConsumer<Buildings, String> visit) {
        switch (this) {
            case All all -> {
                for (int i = 0; i < all.of().size(); i++) {
                    all.of().get(i).buildingCounts(pointer + "/all/" + i, visit);
                }
            }
            case Any any -> {
                for (int i = 0; i < any.of().size(); i++) {
                    any.of().get(i).buildingCounts(pointer + "/any/" + i, visit);
                }
            }
            case Buildings b -> visit.accept(b, pointer + "/buildings/count");
            case Tech t -> {}
            case Phase p -> {}
        }
    }

    /** Calls {@code visit} with every tech leaf and the JSON pointer of its id, below {@code pointer}. */
    default void techLeaves(String pointer, BiConsumer<TechId, String> visit) {
        switch (this) {
            case All all -> {
                for (int i = 0; i < all.of().size(); i++) {
                    all.of().get(i).techLeaves(pointer + "/all/" + i, visit);
                }
            }
            case Any any -> {
                for (int i = 0; i < any.of().size(); i++) {
                    any.of().get(i).techLeaves(pointer + "/any/" + i, visit);
                }
            }
            case Tech t -> visit.accept(t.tech(), pointer + "/tech");
            case Phase p -> {}
            case Buildings b -> {}
        }
    }
}
