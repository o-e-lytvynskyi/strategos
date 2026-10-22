package dev.strategos.core.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.strategos.core.shared.TemplateId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * Compiles template files into a {@link TemplateSet} (04 §8.3, DATA-01): resolves {@code parent} chains with a cycle
 * check that names the chain, lays the {@code mixins} over the parent left to right, each as written (a template some
 * other template lists as a mixin is an overlay and never compiled on its own), and then the
 * template's own body ({@link MergeOps}), and decodes every template that is not {@code abstract} into a {@link
 * UnitTemplate}, field by
 * field, so each problem names its file and JSON pointer. Every problem is collected; templates with problems are left
 * out of the set, and the caller decides whether a set with problems may be used.
 */
public final class TemplateCompiler {
    private TemplateCompiler() {}

    /** The compiled set, the flattened JSON of every resolved template, abstract ones included, and the problems. */
    public record Result(TemplateSet set, Map<TemplateId, JsonObject> flattened, List<TemplateProblem> problems) {
        public boolean ok() {
            return problems.stream().noneMatch(p -> p.severity() == TemplateProblem.Severity.ERROR);
        }
    }

    public static Result compile(List<RawTemplate> raws) {
        List<TemplateProblem> problems = new ArrayList<>();
        Map<String, RawTemplate> byId = new TreeMap<>();
        for (RawTemplate raw : raws) {
            RawTemplate previous = byId.put(raw.id().value(), raw);
            if (previous != null) {
                problems.add(TemplateProblem.error(
                        raw.file(), "", "template " + raw.id() + " is also defined in " + previous.file()));
            }
        }
        Set<String> mixins = mixinIds(byId.values());
        Resolver resolver = new Resolver(byId, problems);
        Map<TemplateId, JsonObject> flattened = new LinkedHashMap<>();
        for (String id : byId.keySet()) {
            if (mixins.contains(id)) {
                continue;
            }
            JsonObject flat = resolver.resolve(id);
            if (flat != null) {
                flattened.put(new TemplateId(id), flat);
            }
        }
        List<UnitTemplate> units = new ArrayList<>();
        List<JsonObject> unitJson = new ArrayList<>();
        for (Map.Entry<TemplateId, JsonObject> e : flattened.entrySet()) {
            if (isAbstract(e.getValue())) {
                continue;
            }
            JsonObject json = withoutAbstract(e.getValue());
            String file = Objects.requireNonNull(byId.get(e.getKey().value())).file();
            UnitTemplate unit = decode(e.getKey(), units.size(), json, file, problems);
            if (unit != null) {
                units.add(unit);
                unitJson.add(json);
            }
        }
        return new Result(new TemplateSet(units, unitJson), flattened, List.copyOf(problems));
    }

    /** The ids some template lists as a mixin: they are overlays, never compiled on their own. */
    private static Set<String> mixinIds(Iterable<RawTemplate> raws) {
        Set<String> ids = new TreeSet<>();
        for (RawTemplate raw : raws) {
            JsonElement mixins = raw.json().get("mixins");
            if (mixins != null && mixins.isJsonArray()) {
                for (JsonElement m : mixins.getAsJsonArray()) {
                    if (m.isJsonPrimitive() && m.getAsJsonPrimitive().isString()) {
                        ids.add(m.getAsString());
                    }
                }
            }
        }
        return ids;
    }

    private static boolean isAbstract(JsonObject flat) {
        JsonElement a = flat.get("abstract");
        return a != null && a.isJsonPrimitive() && a.getAsJsonPrimitive().isBoolean() && a.getAsBoolean();
    }

    private static JsonObject withoutAbstract(JsonObject flat) {
        JsonObject copy = flat.deepCopy();
        copy.remove("abstract");
        return copy;
    }

    /** Resolves parents and mixins once per template, remembering results and failures. */
    private static final class Resolver {
        private final Map<String, RawTemplate> byId;
        private final List<TemplateProblem> problems;
        private final Map<String, JsonObject> done = new TreeMap<>();
        /** True for a member of a cycle, whose problem is already recorded; false for any other failure. */
        private final Map<String, Boolean> failed = new TreeMap<>();

        private final List<String> stack = new ArrayList<>();

        Resolver(Map<String, RawTemplate> byId, List<TemplateProblem> problems) {
            this.byId = byId;
            this.problems = problems;
        }

        @Nullable JsonObject resolve(String id) {
            JsonObject cached = done.get(id);
            if (cached != null) {
                return cached;
            }
            if (failed.containsKey(id)) {
                return null;
            }
            RawTemplate raw = byId.get(id);
            if (raw == null) {
                return null;
            }
            int at = stack.indexOf(id);
            if (at >= 0) {
                List<String> chain = new ArrayList<>(stack.subList(at, stack.size()));
                chain.add(id);
                problems.add(
                        TemplateProblem.error(raw.file(), "/parent", "inheritance cycle " + String.join(" → ", chain)));
                for (String member : chain) {
                    failed.put(member, true);
                }
                return null;
            }
            stack.add(id);
            try {
                return resolveOwn(id, raw);
            } finally {
                stack.remove(stack.size() - 1);
            }
        }

        private @Nullable JsonObject resolveOwn(String id, RawTemplate raw) {
            JsonObject json = raw.json();
            boolean ok = true;
            JsonObject base = new JsonObject();
            JsonElement parent = json.remove("parent");
            if (parent != null) {
                JsonObject p = reference(id, raw, parent, "/parent", "parent");
                if (p == null) {
                    ok = false;
                } else {
                    base = p.deepCopy();
                    base.remove("abstract");
                }
            }
            JsonElement mixins = json.remove("mixins");
            if (mixins != null) {
                if (!mixins.isJsonArray()) {
                    problems.add(TemplateProblem.error(raw.file(), "/mixins", "mixins is an array of template ids"));
                    ok = false;
                } else {
                    JsonArray list = mixins.getAsJsonArray();
                    for (int i = 0; i < list.size(); i++) {
                        RawTemplate m = mixin(id, raw, list.get(i), "/mixins/" + i);
                        if (m == null) {
                            ok = false;
                            continue;
                        }
                        JsonObject overlay = m.json();
                        overlay.remove("abstract");
                        JsonElement merged = MergeOps.merge(base, overlay, m.file(), "", problems);
                        base = merged != null && merged.isJsonObject() ? merged.getAsJsonObject() : new JsonObject();
                    }
                }
            }
            JsonElement merged = MergeOps.merge(base, json, raw.file(), "", problems);
            if (!ok || merged == null || !merged.isJsonObject()) {
                failed.putIfAbsent(id, false);
                return null;
            }
            JsonObject flat = merged.getAsJsonObject();
            done.put(id, flat);
            return flat;
        }

        /**
         * The template a mixin entry names, applied as written: its class edits stay edits and its {@code $op}s apply
         * to what the child inherits, so a mixin has no parent or mixins of its own.
         */
        private @Nullable RawTemplate mixin(String id, RawTemplate raw, JsonElement ref, String pointer) {
            if (!ref.isJsonPrimitive() || !ref.getAsJsonPrimitive().isString()) {
                problems.add(TemplateProblem.error(raw.file(), pointer, "a mixin is a template id string"));
                return null;
            }
            String target = ref.getAsString();
            RawTemplate m = byId.get(target);
            if (m == null) {
                problems.add(TemplateProblem.error(
                        raw.file(), pointer, "unknown mixin '" + target + "' in " + chainTo(id, target)));
                return null;
            }
            JsonObject json = m.json();
            if (json.has("parent") || json.has("mixins")) {
                problems.add(TemplateProblem.error(
                        m.file(),
                        json.has("parent") ? "/parent" : "/mixins",
                        "a mixin is applied as written and has no parent or mixins of its own"));
                return null;
            }
            return m;
        }

        /** The resolved template a {@code parent} names, or null after recording why not. */
        private @Nullable JsonObject reference(
                String id, RawTemplate raw, JsonElement ref, String pointer, String what) {
            if (!ref.isJsonPrimitive() || !ref.getAsJsonPrimitive().isString()) {
                problems.add(TemplateProblem.error(raw.file(), pointer, "a " + what + " is a template id string"));
                return null;
            }
            String target = ref.getAsString();
            if (!byId.containsKey(target)) {
                problems.add(TemplateProblem.error(
                        raw.file(), pointer, "unknown " + what + " '" + target + "' in " + chainTo(id, target)));
                return null;
            }
            JsonObject resolved = resolve(target);
            boolean sameCycle = Boolean.TRUE.equals(failed.get(target)) && Boolean.TRUE.equals(failed.get(id));
            if (resolved == null && !sameCycle) {
                problems.add(TemplateProblem.error(
                        raw.file(), pointer, "its " + what + " '" + target + "' does not compile"));
            }
            return resolved;
        }

        private String chainTo(String id, String target) {
            List<String> chain = new ArrayList<>(stack);
            if (chain.isEmpty() || !chain.get(chain.size() - 1).equals(id)) {
                chain.add(id);
            }
            chain.add(target);
            return String.join(" → ", chain);
        }
    }

    /** The unit components of a flattened template, field by field; null after recording what is wrong. */
    private static @Nullable UnitTemplate decode(
            TemplateId id, int netIndex, JsonObject json, String file, List<TemplateProblem> problems) {
        int before = problems.size();
        Fields f = new Fields(file, problems);
        JsonObject identity = f.object(json, "", "Identity", true);
        JsonObject motion = f.object(json, "", "UnitMotion", true);
        JsonObject body = f.object(json, "", "Body", true);
        String name = identity == null ? id.value() : f.string(identity, "/Identity", "name", id.value());
        String classes = identity == null ? "" : f.string(identity, "/Identity", "classes", "");
        double walkSpeed = motion == null ? 0 : f.positive(motion, "/UnitMotion", "walkSpeed", null);
        double turnRate =
                motion == null ? 0 : f.positive(motion, "/UnitMotion", "turnRate", UnitTemplate.DEFAULT_TURN_RATE);
        String mobility = motion == null ? "" : f.string(motion, "/UnitMotion", "mobility", "infantry");
        if (motion != null && !mobility.equals("infantry")) {
            problems.add(TemplateProblem.error(
                    file, "/UnitMotion/mobility", "unknown mobility '" + mobility + "'; M1 has infantry"));
        }
        String entityType = body == null ? "" : f.string(body, "/Body", "entityType", null);
        String model = body == null ? "" : f.string(body, "/Body", "model", "humanoid");
        String skin = body == null ? "" : f.string(body, "/Body", "skin", "");
        double width = body == null ? 0 : f.positive(body, "/Body", "width", null);
        double height = body == null ? 0 : f.positive(body, "/Body", "height", null);
        double radius = body == null ? 0 : f.positive(body, "/Body", "radius", UnitTemplate.DEFAULT_RADIUS);
        if (problems.size() > before) {
            return null;
        }
        return new UnitTemplate(
                id,
                netIndex,
                name,
                MergeOps.tokens(classes),
                walkSpeed,
                turnRate,
                mobility,
                entityType,
                model,
                skin,
                (float) width,
                (float) height,
                radius);
    }

    /** Reads typed fields, recording a problem at the field's pointer for anything missing or of the wrong type. */
    private record Fields(String file, List<TemplateProblem> problems) {
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
    }
}
