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
 * template's own body ({@link MergeOps}), evaluates the relationships ({@link Derivations}, DATA-04), and decodes every
 * template that is not {@code abstract} into a {@link UnitTemplate}, field by
 * field, so each problem names its file and JSON pointer. Every problem is collected; templates with problems are left
 * out of the set, and the caller decides whether a set with problems may be used.
 */
public final class TemplateCompiler {
    private TemplateCompiler() {}

    /**
     * The set, every template flattened (abstract ones too), the problems, the id table that gave the set its net
     * indices (the one passed in, with the set's new ids appended), and the values relationships set (DATA-04).
     */
    public record Result(
            TemplateSet set,
            Map<TemplateId, JsonObject> flattened,
            List<TemplateProblem> problems,
            TemplateIdTable table,
            List<Derivation> derivations) {
        public boolean ok() {
            return problems.stream().noneMatch(p -> p.severity() == TemplateProblem.Severity.ERROR);
        }
    }

    /** A flattened template with its net index: a journal's or a client's copy of a set. */
    public record Flat(TemplateId id, int netIndex, JsonObject json) {}

    /** Compiles with a fresh id table: net indices in id order. */
    public static Result compile(List<RawTemplate> raws) {
        return compile(raws, TemplateIdTable.EMPTY);
    }

    /** As {@link #compile(List, TemplateIdTable, Map)}, buildings shaped by the placeholder blueprints. */
    public static Result compile(List<RawTemplate> raws, TemplateIdTable table) {
        return compile(raws, table, PlaceholderBlueprints.byId());
    }

    /**
     * The set's templates hold their indices in {@code table}; a template the table lacks is appended (DATA-03), so a
     * reload keeps every index the session gave. A building template names its blueprint in {@code Building/blueprint};
     * {@code blueprints} supplies it, and its JSON joins the flattened template under {@code Blueprint} (BLD-01).
     */
    public static Result compile(List<RawTemplate> raws, TemplateIdTable table, Map<String, Blueprint> blueprints) {
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
        List<Derivation> derivations = Derivations.apply(
                flattened, id -> Objects.requireNonNull(byId.get(id.value())).file(), problems);
        for (Map.Entry<TemplateId, JsonObject> e : flattened.entrySet()) {
            JsonElement b = e.getValue().get("Building");
            if (isAbstract(e.getValue())
                    || b == null
                    || !b.isJsonObject()
                    || e.getValue().has("Blueprint")) {
                continue;
            }
            JsonObject building = b.getAsJsonObject();
            JsonElement name = building.get("blueprint");
            Blueprint bp = name != null && name.isJsonPrimitive() ? blueprints.get(name.getAsString()) : null;
            if (bp == null) {
                problems.add(TemplateProblem.error(
                        Objects.requireNonNull(byId.get(e.getKey().value())).file(),
                        "/Building/blueprint",
                        "unknown blueprint " + name));
            } else {
                e.getValue().add("Blueprint", bp.toJson());
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
        TemplateIdTable next = table.with(units.stream().map(UnitTemplate::id).toList());
        List<Flat> flats = new ArrayList<>();
        for (int k = 0; k < units.size(); k++) {
            flats.add(new Flat(units.get(k).id(), next.indexOf(units.get(k).id()), unitJson.get(k)));
        }
        return new Result(set(flats, units), flattened, List.copyOf(problems), next, List.copyOf(derivations));
    }

    /**
     * A set from flattened templates at their net indices, decoded as {@link #compile} decodes them: a journal's or a
     * client's copy. The problems name each template by its id.
     */
    public static Result fromFlattened(List<Flat> flats) {
        List<TemplateProblem> problems = new ArrayList<>();
        List<UnitTemplate> units = new ArrayList<>();
        List<Flat> kept = new ArrayList<>();
        Map<TemplateId, JsonObject> flattened = new LinkedHashMap<>();
        List<TemplateId> ids = new ArrayList<>();
        for (Flat f : flats) {
            flattened.put(f.id(), f.json().deepCopy());
            UnitTemplate unit = decode(f.id(), f.netIndex(), f.json(), f.id().value(), problems);
            if (unit != null) {
                units.add(unit);
                kept.add(f);
            }
        }
        int top = kept.stream().mapToInt(Flat::netIndex).max().orElse(-1);
        for (int k = 0; k <= top; k++) {
            int index = k;
            ids.add(kept.stream()
                    .filter(f -> f.netIndex() == index)
                    .map(Flat::id)
                    .findFirst()
                    .orElse(new TemplateId("strategos:retired/" + k)));
        }
        return new Result(set(kept, units), flattened, List.copyOf(problems), new TemplateIdTable(ids), List.of());
    }

    /** The decoded units at their flats' net indices, ascending. */
    private static TemplateSet set(List<Flat> flats, List<UnitTemplate> units) {
        List<Integer> order = new ArrayList<>();
        for (int k = 0; k < flats.size(); k++) {
            order.add(k);
        }
        order.sort(
                (a, b) -> Integer.compare(flats.get(a).netIndex(), flats.get(b).netIndex()));
        List<UnitTemplate> placed = new ArrayList<>();
        List<JsonObject> json = new ArrayList<>();
        for (int k : order) {
            placed.add(units.get(k).atIndex(flats.get(k).netIndex()));
            json.add(flats.get(k).json());
        }
        return new TemplateSet(placed, json);
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

    static boolean isAbstract(JsonObject flat) {
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
        if (json.has("Building")) {
            return decodeBuilding(id, netIndex, json, file, problems);
        }
        int before = problems.size();
        Fields f = new Fields(file, problems);
        JsonObject identity = f.object(json, "", "Identity", true);
        JsonObject motion = f.object(json, "", "UnitMotion", true);
        JsonObject body = f.object(json, "", "Body", true);
        if (identity != null) {
            f.known(identity, "/Identity", "name", "classes", "icon");
        }
        if (motion != null) {
            f.known(motion, "/UnitMotion", "walkSpeed", "turnRate", "mobility");
        }
        if (body != null) {
            f.known(body, "/Body", "entityType", "model", "skin", "width", "height", "radius");
        }
        JsonObject attack = f.object(json, "", "Attack", false);
        if (attack != null) {
            JsonObject ranged = f.object(attack, "/Attack", "Ranged", false);
            if (ranged != null) {
                f.known(
                        ranged,
                        "/Attack/Ranged",
                        "hack",
                        "pierce",
                        "crush",
                        "fire",
                        "range",
                        "prepare",
                        "repeat",
                        "speed",
                        "spread",
                        "highGround");
                JsonObject high = f.object(ranged, "/Attack/Ranged", "highGround", false);
                if (high != null) {
                    f.known(high, "/Attack/Ranged/highGround", "rangePerBlock", "damagePerBlock", "maxBlocks");
                    for (String path : List.of(CorePaths.HIGH_RANGE, CorePaths.HIGH_DAMAGE, CorePaths.HIGH_MAX)) {
                        f.rangedNumber(high, path);
                    }
                }
                for (String path : List.of(
                        CorePaths.RANGED_HACK,
                        CorePaths.RANGED_PIERCE,
                        CorePaths.RANGED_CRUSH,
                        CorePaths.RANGED_FIRE,
                        CorePaths.RANGED_RANGE,
                        CorePaths.RANGED_PREPARE,
                        CorePaths.RANGED_REPEAT,
                        CorePaths.RANGED_SPEED,
                        CorePaths.RANGED_SPREAD)) {
                    f.rangedNumber(ranged, path);
                }
            }
        }
        String name = identity == null ? id.value() : f.string(identity, "/Identity", "name", id.value());
        String classes = identity == null ? "" : f.string(identity, "/Identity", "classes", "");
        double walkSpeed = motion == null ? 0 : f.positive(motion, "/UnitMotion", "walkSpeed", null);
        double turnRate =
                motion == null ? 0 : f.positive(motion, "/UnitMotion", "turnRate", UnitTemplate.DEFAULT_TURN_RATE);
        String mobility = motion == null ? "" : f.string(motion, "/UnitMotion", "mobility", "infantry");
        if (motion != null && !mobility.equals("infantry") && !mobility.equals("large")) {
            problems.add(TemplateProblem.error(
                    file, "/UnitMotion/mobility", "unknown mobility '" + mobility + "'; expected infantry or large"));
        }
        String entityType = body == null ? "" : f.string(body, "/Body", "entityType", null);
        String model = body == null ? "" : f.string(body, "/Body", "model", "humanoid");
        String skin = body == null ? "" : f.string(body, "/Body", "skin", "");
        double width = body == null ? 0 : f.positive(body, "/Body", "width", null);
        double height = body == null ? 0 : f.positive(body, "/Body", "height", null);
        double radius = body == null
                ? 0
                : f.positive(
                        body,
                        "/Body",
                        "radius",
                        mobility.equals("large") ? UnitTemplate.DEFAULT_LARGE_RADIUS : UnitTemplate.DEFAULT_RADIUS);
        if (mobility.equals("large") && radius < UnitTemplate.DEFAULT_LARGE_RADIUS) {
            problems.add(TemplateProblem.error(file, "/Body/radius", "large mobility needs a radius of at least 0.7"));
        }
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

    /**
     * A building's template (BLD-01): its identity and its blueprint, which must keep the blueprint rules (a door; ranks
     * that respect supports and spare the two lowest outer layers); a trainer also needs a spawn marker.
     */
    private static @Nullable UnitTemplate decodeBuilding(
            TemplateId id, int netIndex, JsonObject json, String file, List<TemplateProblem> problems) {
        int before = problems.size();
        Fields f = new Fields(file, problems);
        JsonObject identity = f.object(json, "", "Identity", true);
        String name = identity == null ? id.value() : f.string(identity, "/Identity", "name", id.value());
        String classes = identity == null ? "" : f.string(identity, "/Identity", "classes", "");
        JsonElement shape = json.get("Blueprint");
        if (shape == null || !shape.isJsonObject()) {
            problems.add(TemplateProblem.error(file, "/Building/blueprint", "a building needs its blueprint"));
            return null;
        }
        Blueprint bp = Blueprint.fromJson(shape.getAsJsonObject(), file, problems);
        if (bp != null) {
            for (String p : PlaceholderBlueprints.problems(bp)) {
                problems.add(TemplateProblem.error(file, "/Blueprint", p));
            }
            if (json.has("Trainer") && bp.marker("spawn") == null) {
                problems.add(
                        TemplateProblem.error(file, "/Blueprint", bp.id() + " trains units but has no spawn marker"));
            }
        }
        if (problems.size() > before) {
            return null;
        }
        return UnitTemplate.building(id, netIndex, name, MergeOps.tokens(classes));
    }

    /** Reads typed fields, recording a problem at the field's pointer for anything missing or of the wrong type. */
    private record Fields(String file, List<TemplateProblem> problems) {
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
}
