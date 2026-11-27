package dev.strategos.core.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.strategos.core.shared.CivId;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.TechId;
import dev.strategos.core.shared.TemplateId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * A civilisation as content (CIV-01, ADR-0028), compiled from {@code data/<ns>/strategos/civs/<id>.json}: its display
 * names, the {@link Roster} its factions may own, the {@link CivRoles} that name its templates for the engine, its
 * {@code centreClass} (the class token whose buildings count as its centres), its {@link StartKit}, the faction
 * {@code bonuses} (decoded as a technology's {@code effects} are), the {@code rules} it selects per seam (kept raw:
 * the rule catalogue interprets them) and the AI profile it plays.
 *
 * <p>{@link #toJson()} is the canonical form the set hashes and ships: every default written out, keys stable.
 */
@CoreApi
public record CivDef(
        CivId id,
        String name,
        List<String> phases,
        Roster roster,
        String centreClass,
        CivRoles roles,
        StartKit start,
        List<TechEffect> bonuses,
        SortedMap<String, Rule> rules,
        String aiProfile) {
    public static final String DEFAULT_CENTRE_CLASS = "CivicCentre";
    public static final String DEFAULT_AI_PROFILE = "skirmish";

    /** The wildcard a roster list may be: every template, or every technology, of the set. */
    public static final String WILDCARD = "*";

    private static final Pattern CLASS_TOKEN = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");
    private static final Pattern SEAM = Pattern.compile("[a-z][a-z0-9_]*");
    private static final String KEYS = "name, phases, roster, centreClass, roles, start, bonuses, rules, ai";
    private static final String ROSTER_KEYS = "templates, techs";
    private static final String RULE_KEYS = "id, params";

    /** The templates and technologies a civilisation's factions may own; a null list is the wildcard (all of them). */
    public record Roster(
            @Nullable List<TemplateId> templates, @Nullable List<TechId> techs) {
        public static final Roster ALL = new Roster(null, null);

        public Roster {
            templates = templates == null ? null : List.copyOf(templates);
            techs = techs == null ? null : List.copyOf(techs);
        }

        public boolean hasTemplate(TemplateId id) {
            return templates == null || templates.contains(id);
        }

        public boolean hasTech(TechId id) {
            return techs == null || techs.contains(id);
        }
    }

    /** The variant a civilisation selects for one rule seam, and its parameters (T5 interprets both). */
    public record Rule(String id, JsonObject params) {
        public Rule {
            params = params.deepCopy();
        }

        @Override
        public JsonObject params() {
            return params.deepCopy();
        }
    }

    public CivDef {
        phases = List.copyOf(phases);
        bonuses = List.copyOf(bonuses);
        rules = Collections.unmodifiableSortedMap(new TreeMap<>(rules));
    }

    /** The canonical JSON the set hashes and ships: defaults written out, the id left to the key. */
    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("name", name);
        if (!phases.isEmpty()) {
            JsonArray names = new JsonArray();
            phases.forEach(names::add);
            o.add("phases", names);
        }
        JsonObject ro = new JsonObject();
        ro.add("templates", list(roster.templates(), TemplateId::value));
        ro.add("techs", list(roster.techs(), TechId::value));
        o.add("roster", ro);
        o.addProperty("centreClass", centreClass);
        o.add("roles", roles.toJson());
        o.add("start", start.toJson());
        o.add("bonuses", TechDef.effectsJson(bonuses));
        JsonObject rs = new JsonObject();
        rules.forEach((seam, rule) -> {
            JsonObject r = new JsonObject();
            r.addProperty("id", rule.id());
            r.add("params", rule.params());
            rs.add(seam, r);
        });
        o.add("rules", rs);
        JsonObject ai = new JsonObject();
        ai.addProperty("profile", aiProfile);
        o.add("ai", ai);
        return o;
    }

    private static <T> JsonElement list(@Nullable List<T> ids, java.util.function.Function<T, String> value) {
        if (ids == null) {
            return new JsonPrimitive(WILDCARD);
        }
        JsonArray a = new JsonArray();
        new TreeSet<>(ids.stream().map(value).toList()).forEach(a::add);
        return a;
    }

    /**
     * Decodes a civilisation file; null after recording each problem with {@code file} and the JSON pointer. Whether
     * the ids it names exist is for the compiler, which knows the set ({@link CivChecks}).
     */
    public static @Nullable CivDef fromJson(CivId id, JsonObject json, String file, List<TemplateProblem> problems) {
        int before = problems.size();
        StartKit.unknownKeys(json, KEYS, file, "", problems);
        String name = id.value();
        JsonElement n = json.get("name");
        if (n != null) {
            if (isString(n) && !n.getAsString().isBlank()) {
                name = n.getAsString();
            } else {
                problems.add(TemplateProblem.error(file, "/name", "expected a language key string, got " + n));
            }
        }
        List<String> phases = new ArrayList<>();
        JsonElement ph = json.get("phases");
        if (ph != null) {
            if (!ph.isJsonArray()) {
                problems.add(TemplateProblem.error(file, "/phases", "expected an array of language keys"));
            } else {
                for (int i = 0; i < ph.getAsJsonArray().size(); i++) {
                    JsonElement p = ph.getAsJsonArray().get(i);
                    if (isString(p) && !p.getAsString().isBlank()) {
                        phases.add(p.getAsString());
                    } else {
                        problems.add(TemplateProblem.error(file, "/phases/" + i, "expected a language key string"));
                    }
                }
            }
        }
        Roster roster = roster(json.get("roster"), file, problems);
        String centreClass = DEFAULT_CENTRE_CLASS;
        JsonElement cc = json.get("centreClass");
        if (cc != null) {
            if (isString(cc) && CLASS_TOKEN.matcher(cc.getAsString()).matches()) {
                centreClass = cc.getAsString();
            } else {
                problems.add(TemplateProblem.error(file, "/centreClass", "expected a class token, got " + cc));
            }
        }
        CivRoles roles = roles(json.get("roles"), file, problems);
        StartKit start = StartKit.fromJson(json.get("start"), file, "/start", problems);
        List<TechEffect> bonuses = TechDef.effects(json.get("bonuses"), file, "/bonuses", problems);
        SortedMap<String, Rule> rules = rules(json.get("rules"), file, problems);
        String profile = DEFAULT_AI_PROFILE;
        JsonElement ai = json.get("ai");
        if (ai != null) {
            if (!ai.isJsonObject()) {
                problems.add(TemplateProblem.error(file, "/ai", "expected an object with a profile"));
            } else {
                StartKit.unknownKeys(ai.getAsJsonObject(), "profile", file, "/ai", problems);
                JsonElement p = ai.getAsJsonObject().get("profile");
                if (p != null) {
                    if (isString(p) && !p.getAsString().isBlank()) {
                        profile = p.getAsString();
                    } else {
                        problems.add(TemplateProblem.error(file, "/ai/profile", "expected a profile name, got " + p));
                    }
                }
            }
        }
        if (problems.size() > before) {
            return null;
        }
        return new CivDef(id, name, phases, roster, centreClass, roles, start, bonuses, rules, profile);
    }

    private static Roster roster(@Nullable JsonElement json, String file, List<TemplateProblem> problems) {
        if (json == null) {
            return Roster.ALL;
        }
        if (!json.isJsonObject()) {
            problems.add(TemplateProblem.error(file, "/roster", "expected an object with templates and techs"));
            return Roster.ALL;
        }
        JsonObject o = json.getAsJsonObject();
        StartKit.unknownKeys(o, ROSTER_KEYS, file, "/roster", problems);
        List<TemplateId> templates = null;
        JsonElement t = o.get("templates");
        if (t != null && !isWildcard(t)) {
            templates = new ArrayList<>();
            for (String s : ids(t, file, "/roster/templates", problems)) {
                try {
                    templates.add(new TemplateId(s));
                } catch (IllegalArgumentException e) {
                    problems.add(TemplateProblem.error(file, "/roster/templates", String.valueOf(e.getMessage())));
                }
            }
        }
        List<TechId> techs = null;
        JsonElement k = o.get("techs");
        if (k != null && !isWildcard(k)) {
            techs = new ArrayList<>();
            for (String s : ids(k, file, "/roster/techs", problems)) {
                try {
                    techs.add(new TechId(s));
                } catch (IllegalArgumentException e) {
                    problems.add(TemplateProblem.error(file, "/roster/techs", String.valueOf(e.getMessage())));
                }
            }
        }
        return new Roster(
                templates == null ? null : sorted(templates, TemplateId::value),
                techs == null ? null : sorted(techs, TechId::value));
    }

    private static <T> List<T> sorted(List<T> ids, java.util.function.Function<T, String> value) {
        return ids.stream()
                .sorted((a, b) -> value.apply(a).compareTo(value.apply(b)))
                .distinct()
                .toList();
    }

    private static List<String> ids(JsonElement json, String file, String at, List<TemplateProblem> problems) {
        List<String> out = new ArrayList<>();
        if (!json.isJsonArray()) {
            problems.add(
                    TemplateProblem.error(file, at, "expected \"" + WILDCARD + "\" or an array of ids, got " + json));
            return out;
        }
        for (int i = 0; i < json.getAsJsonArray().size(); i++) {
            JsonElement e = json.getAsJsonArray().get(i);
            if (isString(e)) {
                out.add(e.getAsString());
            } else {
                problems.add(TemplateProblem.error(file, at + "/" + i, "expected an id string, got " + e));
            }
        }
        return out;
    }

    private static boolean isWildcard(JsonElement e) {
        return isString(e) && e.getAsString().equals(WILDCARD);
    }

    private static CivRoles roles(@Nullable JsonElement json, String file, List<TemplateProblem> problems) {
        if (json == null) {
            return CivRoles.NONE;
        }
        if (!json.isJsonObject()) {
            problems.add(TemplateProblem.error(file, "/roles", "expected an object of role to template"));
            return CivRoles.NONE;
        }
        SortedMap<String, TemplateId> roles = new TreeMap<>();
        for (Map.Entry<String, JsonElement> e : json.getAsJsonObject().asMap().entrySet()) {
            String at = "/roles/" + e.getKey();
            if (!CivRoles.NAME.matcher(e.getKey()).matches()) {
                problems.add(
                        TemplateProblem.error(file, at, "a role name is lower-case words, got '" + e.getKey() + "'"));
            } else if (!isString(e.getValue())) {
                problems.add(TemplateProblem.error(file, at, "expected a template id string, got " + e.getValue()));
            } else {
                try {
                    roles.put(e.getKey(), new TemplateId(e.getValue().getAsString()));
                } catch (IllegalArgumentException ex) {
                    problems.add(TemplateProblem.error(file, at, String.valueOf(ex.getMessage())));
                }
            }
        }
        return new CivRoles(roles);
    }

    private static SortedMap<String, Rule> rules(
            @Nullable JsonElement json, String file, List<TemplateProblem> problems) {
        SortedMap<String, Rule> rules = new TreeMap<>();
        if (json == null) {
            return rules;
        }
        if (!json.isJsonObject()) {
            problems.add(TemplateProblem.error(file, "/rules", "expected an object of seam to {id, params}"));
            return rules;
        }
        for (Map.Entry<String, JsonElement> e : json.getAsJsonObject().asMap().entrySet()) {
            String at = "/rules/" + e.getKey();
            if (!SEAM.matcher(e.getKey()).matches()) {
                problems.add(
                        TemplateProblem.error(file, at, "a seam name is lower-case words, got '" + e.getKey() + "'"));
                continue;
            }
            if (!e.getValue().isJsonObject()) {
                problems.add(TemplateProblem.error(file, at, "expected {\"id\": string, \"params\": object}"));
                continue;
            }
            JsonObject o = e.getValue().getAsJsonObject();
            int before = problems.size();
            StartKit.unknownKeys(o, RULE_KEYS, file, at, problems);
            JsonElement id = o.get("id");
            String variant = null;
            if (id != null && isString(id) && !id.getAsString().isBlank()) {
                variant = id.getAsString();
            } else {
                problems.add(TemplateProblem.error(file, at + "/id", "expected a rule variant id string, got " + id));
            }
            JsonElement params = o.get("params");
            if (params != null && !params.isJsonObject()) {
                problems.add(TemplateProblem.error(file, at + "/params", "expected an object, got " + params));
            }
            if (variant != null && problems.size() == before) {
                rules.put(e.getKey(), new Rule(variant, params == null ? new JsonObject() : params.getAsJsonObject()));
            }
        }
        return rules;
    }

    private static boolean isString(JsonElement e) {
        return e instanceof JsonPrimitive p && p.isString();
    }
}
