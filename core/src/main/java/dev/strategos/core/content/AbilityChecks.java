package dev.strategos.core.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.strategos.core.shared.AbilityId;
import dev.strategos.core.shared.TemplateId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The lint of {@code Abilities} (UNIT-10): each template's own (shape, targets, effects), then across the set: an id is
 * one ability wherever it appears, so two templates that declare it differently are an error, and the paths and
 * templates its effects name must exist. Every problem is a {@link TemplateProblem}; nothing throws.
 */
final class AbilityChecks {
    private AbilityChecks() {}

    /** One template's abilities, recording at {@code /Abilities/<id>} what is wrong; empty for a template with none. */
    static List<AbilityDef> template(JsonObject json, String file, boolean building, List<TemplateProblem> problems) {
        JsonElement e = json.get("Abilities");
        if (e == null) {
            return List.of();
        }
        if (e.isJsonArray()) {
            return List.of(); // a token list, as the merge operators' fixtures write one: no ability definitions
        }
        if (building) {
            problems.add(TemplateProblem.error(file, "/Abilities", "a building has no abilities: they are for units"));
            return List.of();
        }
        if (!e.isJsonObject()) {
            problems.add(TemplateProblem.error(file, "/Abilities", "expected an object of abilities by id"));
            return List.of();
        }
        Fields f = new Fields(file, problems);
        List<AbilityDef> out = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry :
                new TreeMap<>(e.getAsJsonObject().asMap()).entrySet()) {
            String at = "/Abilities/" + entry.getKey();
            if (!AbilityId.valid(entry.getKey())) {
                problems.add(TemplateProblem.error(file, at, "not an ability id (lower-case letters, digits, _ . -)"));
            } else if (!entry.getValue().isJsonObject()) {
                problems.add(TemplateProblem.error(file, at, "expected an object"));
            } else {
                AbilityDef def =
                        AbilityDef.parse(entry.getKey(), entry.getValue().getAsJsonObject(), at, f);
                if (def != null) {
                    out.add(def);
                }
            }
        }
        return out;
    }

    /** The abilities {@code json} declares that are well formed, as a set built from checked data holds them. */
    static List<AbilityDef> defs(JsonObject json) {
        return template(json, "", false, new ArrayList<>());
    }

    /** A template as the set-wide check sees it. */
    record Source(TemplateId id, String file, JsonObject json, boolean building) {}

    /**
     * The checks across templates: one definition per id, every modifier path a number some template has (or a core
     * path), every spawned template in the set and a unit.
     */
    static void cross(List<Source> sources, List<TemplateProblem> problems) {
        Set<String> paths = numberPaths(sources);
        Set<String> units = new TreeSet<>();
        for (Source s : sources) {
            if (!s.building()) {
                units.add(s.id().value());
            }
        }
        Map<String, Source> first = new TreeMap<>();
        Map<String, JsonObject> firstJson = new TreeMap<>();
        for (Source s : sources) {
            JsonElement e = s.json().get("Abilities");
            if (s.building() || e == null || !e.isJsonObject()) {
                continue;
            }
            for (Map.Entry<String, JsonElement> entry :
                    new TreeMap<>(e.getAsJsonObject().asMap()).entrySet()) {
                if (!AbilityId.valid(entry.getKey()) || !entry.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject body = entry.getValue().getAsJsonObject();
                String id = entry.getKey();
                JsonObject seen = firstJson.putIfAbsent(id, body);
                if (seen == null) {
                    first.put(id, s);
                    effectReferences(s.file(), id, body, paths, units, problems);
                } else if (!seen.equals(body)) {
                    problems.add(TemplateProblem.error(
                            s.file(),
                            "/Abilities/" + id,
                            "ability '" + id + "' differs from its definition in "
                                    + java.util.Objects.requireNonNull(first.get(id))
                                            .file()
                                    + ": an id is one ability, so every template that has it declares it alike"));
                }
            }
        }
    }

    /** Every value path of the sources: the core's and each number some template has. */
    static Set<String> numberPaths(List<Source> sources) {
        Set<String> paths = new TreeSet<>(CorePaths.all());
        for (Source s : sources) {
            List<String> numbers = new ArrayList<>();
            TemplateSet.leafPaths(s.json(), "", numbers, new ArrayList<>());
            paths.addAll(numbers);
        }
        return paths;
    }

    private static void effectReferences(
            String file,
            String id,
            JsonObject body,
            Set<String> paths,
            Set<String> units,
            List<TemplateProblem> problems) {
        JsonElement list = body.get("effects");
        if (!(list instanceof JsonArray effects)) {
            return;
        }
        for (int k = 0; k < effects.size(); k++) {
            if (!effects.get(k).isJsonObject()) {
                continue;
            }
            JsonObject effect = effects.get(k).getAsJsonObject();
            String at = "/Abilities/" + id + "/effects/" + k + "/params";
            JsonElement kind = effect.get("kind");
            JsonElement params = effect.get("params");
            if (kind == null || !kind.isJsonPrimitive() || params == null || !params.isJsonObject()) {
                continue;
            }
            JsonObject p = params.getAsJsonObject();
            switch (kind.getAsString()) {
                case AbilityKinds.APPLY_MODIFIERS -> {
                    JsonElement mods = p.get("modifiers");
                    if (mods instanceof JsonArray array) {
                        for (int m = 0; m < array.size(); m++) {
                            if (array.get(m).isJsonObject()) {
                                JsonElement path =
                                        array.get(m).getAsJsonObject().get("path");
                                if (path != null
                                        && path.isJsonPrimitive()
                                        && path.getAsJsonPrimitive().isString()
                                        && !paths.contains(path.getAsString())) {
                                    problems.add(TemplateProblem.error(
                                            file,
                                            at + "/modifiers/" + m + "/path",
                                            "no template has a number at '" + path.getAsString() + "'"));
                                }
                            }
                        }
                    }
                }
                case AbilityKinds.CAPTURE_STRENGTH -> {
                    if (!paths.contains(AbilityKinds.CAPTURE_PATH)) {
                        problems.add(TemplateProblem.error(
                                file, at, "no template has " + AbilityKinds.CAPTURE_PATH + " to add to"));
                    }
                }
                case AbilityKinds.SPAWN -> {
                    JsonElement template = p.get("template");
                    if (template != null
                            && template.isJsonPrimitive()
                            && template.getAsJsonPrimitive().isString()
                            && !units.contains(template.getAsString())) {
                        problems.add(TemplateProblem.error(
                                file,
                                at + "/template",
                                "'" + template.getAsString() + "' is not a unit template of the set"));
                    }
                }
                default -> {}
            }
        }
    }
}
