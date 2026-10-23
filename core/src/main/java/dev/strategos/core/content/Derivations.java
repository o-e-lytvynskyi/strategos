package dev.strategos.core.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.strategos.core.shared.TemplateId;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Evaluates the relationships of a flattened template set (DATA-04): every {@code $derive} marker of a concrete
 * template becomes {@code from}'s value at {@code path} (the template itself for {@code "self"}) times {@code factor}
 * plus {@code offset}, rounded half up to a multiple of {@code round} when it is set, exactly, in decimals; every
 * {@code $override} becomes its number. A source may itself be derived, in any template, abstract ones included; a
 * cycle, a missing source or one that is no number is a problem. Abstract templates keep their markers.
 */
final class Derivations {
    private final Map<TemplateId, JsonObject> flattened;
    private final Function<TemplateId, String> files;
    private final List<TemplateProblem> problems;
    private final Map<String, BigDecimal> memo = new TreeMap<>();
    private final Map<String, Boolean> failed = new TreeMap<>();
    private final List<String> visiting = new ArrayList<>();

    private Derivations(
            Map<TemplateId, JsonObject> flattened, Function<TemplateId, String> files, List<TemplateProblem> problems) {
        this.flattened = flattened;
        this.files = files;
        this.problems = problems;
    }

    /** Replaces the markers of every concrete template in {@code flattened}, in id and path order. */
    static List<Derivation> apply(
            Map<TemplateId, JsonObject> flattened, Function<TemplateId, String> files, List<TemplateProblem> problems) {
        Derivations d = new Derivations(flattened, files, problems);
        List<Derivation> out = new ArrayList<>();
        for (Map.Entry<TemplateId, JsonObject> e : flattened.entrySet()) {
            if (TemplateCompiler.isAbstract(e.getValue())) {
                continue;
            }
            List<String> paths = new ArrayList<>();
            markers(e.getValue(), "", paths);
            for (String path : paths) {
                JsonObject marker =
                        Objects.requireNonNull(at(e.getValue(), path)).getAsJsonObject();
                BigDecimal value = d.value(e.getKey(), path);
                if (value == null) {
                    continue; // a problem says why
                }
                boolean overridden = MergeOps.overridden(marker);
                JsonObject derive = overridden ? marker.getAsJsonObject(MergeOps.OF) : marker;
                BigDecimal formulaValue = overridden ? d.derive(e.getKey(), path, derive) : null;
                String note = overridden
                        ? marker.get("reason").getAsString()
                        : derive.get("note").getAsString();
                out.add(new Derivation(e.getKey(), path, value, formula(derive), note, overridden, formulaValue));
                put(e.getValue(), path, new JsonPrimitive(plain(value)));
            }
        }
        return out;
    }

    /** {@code id}'s value at {@code path}, evaluated if derived; null when it is missing or no number. */
    private @Nullable BigDecimal value(TemplateId id, String path) {
        JsonObject json = flattened.get(id);
        JsonElement e = json == null ? null : at(json, path);
        if (e == null) {
            return null;
        }
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
            return new BigDecimal(e.getAsString());
        }
        if (MergeOps.overridden(e)) {
            return new BigDecimal(e.getAsJsonObject().get(MergeOps.OVERRIDE).getAsString());
        }
        if (MergeOps.derived(e)) {
            return derive(id, path, e.getAsJsonObject());
        }
        return null;
    }

    /** What {@code marker}, a derivation found at {@code id}'s {@code path}, gives there. */
    private @Nullable BigDecimal derive(TemplateId id, String path, JsonObject marker) {
        String key = id.value() + "#" + path;
        BigDecimal known = memo.get(key);
        if (known != null) {
            return known;
        }
        if (failed.containsKey(key)) {
            return null;
        }
        if (visiting.contains(key)) {
            List<String> cycle = new ArrayList<>(visiting.subList(visiting.indexOf(key), visiting.size()));
            cycle.add(key);
            problem(id, path, "a derivation cycle: " + String.join(" -> ", cycle));
            failed.put(key, true);
            return null;
        }
        visiting.add(key);
        try {
            JsonObject spec = marker.getAsJsonObject(MergeOps.DERIVE);
            JsonElement fromField = spec.get("from");
            JsonElement pathField = spec.get("path");
            if (fromField == null || pathField == null) {
                failed.put(key, true);
                return null; // the merge reported it
            }
            String from = fromField.getAsString();
            TemplateId source;
            try {
                source = from.equals("self") ? id : new TemplateId(from);
            } catch (IllegalArgumentException ex) {
                problem(id, path, "$derive from '" + from + "': " + ex.getMessage());
                failed.put(key, true);
                return null;
            }
            String sourcePath = pathField.getAsString();
            if (!flattened.containsKey(source)) {
                problem(id, path, "$derive from " + source + ", which is no template");
                failed.put(key, true);
                return null;
            }
            BigDecimal base = value(source, sourcePath);
            if (base == null) {
                problem(id, path, "$derive from " + source + " " + sourcePath + ", which is no number");
                failed.put(key, true);
                return null;
            }
            BigDecimal v =
                    base.multiply(number(spec, "factor", BigDecimal.ONE)).add(number(spec, "offset", BigDecimal.ZERO));
            BigDecimal round = number(spec, "round", BigDecimal.ZERO);
            if (round.signum() > 0) {
                v = v.divide(round, 0, RoundingMode.HALF_UP).multiply(round);
            }
            memo.put(key, v);
            return v;
        } finally {
            visiting.remove(visiting.size() - 1);
        }
    }

    private void problem(TemplateId id, String path, String message) {
        problems.add(TemplateProblem.error(files.apply(id), "/" + path, message));
    }

    /** The formula as the balance sheet shows it: {@code self Attack/Melee/hack × 1 + 0, to 1}. */
    static String formula(JsonObject marker) {
        JsonObject spec = marker.getAsJsonObject(MergeOps.DERIVE);
        StringBuilder sb = new StringBuilder();
        sb.append(spec.has("from") ? spec.get("from").getAsString() : "?")
                .append(' ')
                .append(spec.has("path") ? spec.get("path").getAsString() : "?");
        BigDecimal factor = number(spec, "factor", BigDecimal.ONE);
        BigDecimal offset = number(spec, "offset", BigDecimal.ZERO);
        BigDecimal round = number(spec, "round", BigDecimal.ZERO);
        if (factor.compareTo(BigDecimal.ONE) != 0) {
            sb.append(" × ").append(plain(factor));
        }
        if (offset.signum() != 0) {
            sb.append(offset.signum() > 0 ? " + " : " − ").append(plain(offset.abs()));
        }
        if (round.signum() > 0) {
            sb.append(", to ").append(plain(round));
        }
        return sb.toString();
    }

    private static BigDecimal number(JsonObject spec, String key, BigDecimal otherwise) {
        JsonElement e = spec.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()
                ? new BigDecimal(e.getAsString())
                : otherwise;
    }

    /** The shortest exact form, as canonical JSON writes numbers. */
    static BigDecimal plain(BigDecimal v) {
        BigDecimal s = v.stripTrailingZeros();
        return s.scale() < 0 ? s.setScale(0) : s;
    }

    /** Every marker's path under {@code json}, objects only, in the JSON's own key order; paths built only for them. */
    private static void markers(JsonObject json, String prefix, List<String> out) {
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            JsonElement e = entry.getValue();
            if (!e.isJsonObject()) {
                continue;
            }
            if (MergeOps.derived(e) || MergeOps.overridden(e)) {
                out.add(prefix.isEmpty() ? entry.getKey() : prefix + "/" + entry.getKey());
            } else if (hasObject(e.getAsJsonObject())) {
                markers(e.getAsJsonObject(), prefix.isEmpty() ? entry.getKey() : prefix + "/" + entry.getKey(), out);
            }
        }
    }

    private static boolean hasObject(JsonObject json) {
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            if (entry.getValue().isJsonObject()) {
                return true;
            }
        }
        return false;
    }

    /** The element at a slash-separated path of object keys, or null. */
    static @Nullable JsonElement at(JsonObject json, String path) {
        JsonElement e = json;
        for (String key : path.split("/", -1)) {
            if (e == null || !e.isJsonObject()) {
                return null;
            }
            e = e.getAsJsonObject().get(key);
        }
        return e;
    }

    private static void put(JsonObject json, String path, JsonElement value) {
        String[] keys = path.split("/", -1);
        JsonObject o = json;
        for (int i = 0; i < keys.length - 1; i++) {
            o = o.getAsJsonObject(keys[i]);
        }
        o.add(keys[keys.length - 1], value);
    }
}
