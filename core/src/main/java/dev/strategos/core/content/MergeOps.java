package dev.strategos.core.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * How a child's JSON lays over what it inherits (04 §8.3, DATA-01.2):
 *
 * <ul>
 *   <li>objects merge deeply, the child winning per field; scalars and arrays replace;
 *   <li>{@code {"$op": "add" | "mul" | "replace", "value": x}} applies to the inherited value (exact decimals);
 *   <li>{@code {"$disable": true}} removes the inherited component;
 *   <li>{@code {"$append": [..]}} and {@code {"$remove": [..]}} edit an inherited array;
 *   <li>a {@code classes} string whose tokens all start with {@code +} or {@code -} edits the inherited tokens; one
 *       without signs replaces them. Tokens come out sorted and unique;
 *   <li>{@code {"$derive": {"from", "path", "factor", "offset", "round"}, "note": ".."}} makes a value derived from
 *       another (DATA-04), evaluated after flattening by {@code Derivations}; it is inherited as written;
 *   <li>{@code {"$override": x, "reason": ".."}} is the only way to set a value something derives; it keeps the
 *       derivation it overrides under {@code $of}, for the balance sheet. A plain value, an {@code $op} or an object
 *       laid over a derived value is a problem.
 * </ul>
 *
 * Problems go to the list with their JSON pointer; the merge goes on, so every problem is reported.
 */
final class MergeOps {
    static final String DERIVE = "$derive";
    static final String OVERRIDE = "$override";
    static final String OF = "$of";

    private MergeOps() {}

    /** Whether {@code e} is a derivation marker, inherited or written here. */
    static boolean derived(@Nullable JsonElement e) {
        return e != null && e.isJsonObject() && e.getAsJsonObject().has(DERIVE);
    }

    /** Whether {@code e} is an override marker. */
    static boolean overridden(@Nullable JsonElement e) {
        return e != null && e.isJsonObject() && e.getAsJsonObject().has(OVERRIDE);
    }

    /** The merged value, or null when the overlay disables the component. */
    static @Nullable JsonElement merge(
            @Nullable JsonElement base,
            JsonElement overlay,
            String file,
            String pointer,
            List<TemplateProblem> problems) {
        if (overlay.isJsonObject() && overlay.getAsJsonObject().has(DERIVE)) {
            return derive(overlay.getAsJsonObject(), file, pointer, problems);
        }
        if (overlay.isJsonObject() && overlay.getAsJsonObject().has(OVERRIDE)) {
            return override(base, overlay.getAsJsonObject(), file, pointer, problems);
        }
        if (base != null && (derived(base) || overridden(base))) {
            JsonObject marker = base.getAsJsonObject();
            JsonElement how = derived(base) ? marker.get(DERIVE) : marker.get(OF);
            problems.add(TemplateProblem.error(
                    file,
                    pointer,
                    "the value is derived (" + how + "); a template sets it only with"
                            + " {\"$override\": x, \"reason\": \"..\"}"));
            return base;
        }
        if (overlay.isJsonObject()) {
            JsonObject o = overlay.getAsJsonObject();
            if (o.has("$disable")) {
                return disable(base, o, file, pointer, problems);
            }
            if (o.has("$op")) {
                return op(base, o, file, pointer, problems);
            }
            if (o.has("$append") || o.has("$remove")) {
                return editArray(base, o, file, pointer, problems);
            }
            JsonObject result =
                    base != null && base.isJsonObject() ? base.getAsJsonObject().deepCopy() : new JsonObject();
            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                JsonElement merged =
                        merge(result.get(e.getKey()), e.getValue(), file, pointer + "/" + escape(e.getKey()), problems);
                if (merged == null) {
                    result.remove(e.getKey());
                } else {
                    result.add(e.getKey(), merged);
                }
            }
            return result;
        }
        if (pointer.endsWith("/classes")
                && overlay.isJsonPrimitive()
                && overlay.getAsJsonPrimitive().isString()) {
            return new JsonPrimitive(classes(base, overlay.getAsString()));
        }
        return overlay.deepCopy();
    }

    /** {@code "~" -> "~0"} and {@code "/" -> "~1"}, as JSON pointers escape keys. */
    static String escape(String key) {
        return key.replace("~", "~0").replace("/", "~1");
    }

    /** The tokens of a classes string, sorted and unique. */
    static List<String> tokens(String classes) {
        TreeSet<String> set = new TreeSet<>();
        int i = 0;
        int n = classes.length();
        while (i < n) {
            while (i < n && Character.isWhitespace(classes.charAt(i))) {
                i++;
            }
            int start = i;
            while (i < n && !Character.isWhitespace(classes.charAt(i))) {
                i++;
            }
            if (i > start) {
                set.add(classes.substring(start, i));
            }
        }
        return new ArrayList<>(set);
    }

    private static @Nullable JsonElement disable(
            @Nullable JsonElement base, JsonObject o, String file, String pointer, List<TemplateProblem> problems) {
        JsonElement flag = o.get("$disable");
        if (o.size() != 1
                || !flag.isJsonPrimitive()
                || !flag.getAsJsonPrimitive().isBoolean()) {
            problems.add(TemplateProblem.error(file, pointer, "$disable stands alone with the value true"));
            return base;
        }
        return flag.getAsBoolean() ? null : base;
    }

    private static @Nullable JsonElement op(
            @Nullable JsonElement base, JsonObject o, String file, String pointer, List<TemplateProblem> problems) {
        JsonElement opName = o.get("$op");
        JsonElement value = o.get("value");
        if (o.size() != 2
                || value == null
                || !opName.isJsonPrimitive()
                || !opName.getAsJsonPrimitive().isString()) {
            problems.add(TemplateProblem.error(file, pointer, "an $op object holds exactly $op and value"));
            return base;
        }
        String name = opName.getAsString();
        if (name.equals("replace")) {
            return value.deepCopy();
        }
        if (!name.equals("add") && !name.equals("mul")) {
            problems.add(
                    TemplateProblem.error(file, pointer + "/$op", "unknown $op '" + name + "'; add, mul or replace"));
            return base;
        }
        if (!isNumber(value)) {
            problems.add(TemplateProblem.error(file, pointer + "/value", "$op " + name + " needs a number"));
            return base;
        }
        if (base == null || !isNumber(base)) {
            problems.add(TemplateProblem.error(file, pointer, "$op " + name + " needs an inherited number"));
            return base;
        }
        BigDecimal a = new BigDecimal(base.getAsString());
        BigDecimal b = new BigDecimal(value.getAsString());
        return new JsonPrimitive(name.equals("add") ? a.add(b) : a.multiply(b));
    }

    /** A derivation marker, checked for its shape and kept as written. */
    private static JsonElement derive(JsonObject o, String file, String pointer, List<TemplateProblem> problems) {
        JsonElement spec = o.get(DERIVE);
        JsonElement note = o.get("note");
        if (o.size() != 2 || !spec.isJsonObject() || note == null || !isText(note)) {
            problems.add(TemplateProblem.error(
                    file, pointer, "a $derive object holds exactly $derive (an object) and a note (text)"));
            return o.deepCopy();
        }
        JsonObject d = spec.getAsJsonObject();
        for (Map.Entry<String, JsonElement> e : d.entrySet()) {
            boolean ok = switch (e.getKey()) {
                case "from", "path" -> isText(e.getValue());
                case "factor", "offset", "round" -> isNumber(e.getValue());
                default -> false;
            };
            if (!ok) {
                problems.add(TemplateProblem.error(
                        file,
                        pointer + "/$derive/" + escape(e.getKey()),
                        "$derive takes from and path (text) and factor, offset and round (numbers)"));
            }
        }
        if (!d.has("from") || !d.has("path")) {
            problems.add(TemplateProblem.error(file, pointer + "/$derive", "$derive needs from and path"));
        }
        return o.deepCopy();
    }

    /** An override of an inherited derivation: the number, the reason and, under {@code $of}, what it overrides. */
    private static JsonElement override(
            @Nullable JsonElement base, JsonObject o, String file, String pointer, List<TemplateProblem> problems) {
        JsonElement value = o.get(OVERRIDE);
        JsonElement reason = o.get("reason");
        if (o.size() != 2 || !isNumber(value) || reason == null || !isText(reason)) {
            problems.add(TemplateProblem.error(
                    file, pointer, "an $override object holds exactly $override (a number) and a reason (text)"));
            return base == null ? o.deepCopy() : base;
        }
        JsonElement of = base == null
                ? null
                : derived(base)
                        ? base
                        : overridden(base) ? base.getAsJsonObject().get(OF) : null;
        if (of == null) {
            problems.add(TemplateProblem.error(file, pointer, "an $override of a value nothing derives"));
            return base == null ? value.deepCopy() : base;
        }
        JsonObject result = o.deepCopy();
        result.add(OF, of.deepCopy());
        return result;
    }

    private static @Nullable JsonElement editArray(
            @Nullable JsonElement base, JsonObject o, String file, String pointer, List<TemplateProblem> problems) {
        if (base != null && !base.isJsonArray()) {
            problems.add(TemplateProblem.error(file, pointer, "$append and $remove need an inherited array"));
            return base;
        }
        JsonArray result =
                base == null ? new JsonArray() : base.getAsJsonArray().deepCopy();
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            if (!e.getValue().isJsonArray()) {
                problems.add(TemplateProblem.error(file, pointer + "/" + e.getKey(), e.getKey() + " needs an array"));
                continue;
            }
            switch (e.getKey()) {
                case "$append" -> e.getValue().getAsJsonArray().forEach(v -> result.add(v.deepCopy()));
                case "$remove" ->
                    e.getValue().getAsJsonArray().forEach(v -> {
                        while (result.remove(v)) {
                            // every copy goes
                        }
                    });
                default ->
                    problems.add(TemplateProblem.error(
                            file, pointer + "/" + escape(e.getKey()), "only $append and $remove may edit an array"));
            }
        }
        return result;
    }

    private static String classes(@Nullable JsonElement base, String overlay) {
        List<String> edits = tokens(overlay);
        boolean signed = !edits.isEmpty();
        for (String t : edits) {
            signed &= t.startsWith("+") || t.startsWith("-");
        }
        if (!signed) {
            return String.join(" ", edits);
        }
        TreeSet<String> result = new TreeSet<>();
        if (base != null && base.isJsonPrimitive() && base.getAsJsonPrimitive().isString()) {
            result.addAll(tokens(base.getAsString()));
        }
        for (String t : edits) {
            if (t.startsWith("+")) {
                result.add(t.substring(1));
            } else {
                result.remove(t.substring(1));
            }
        }
        return String.join(" ", result);
    }

    private static boolean isNumber(JsonElement e) {
        return e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber();
    }

    private static boolean isText(JsonElement e) {
        return e.isJsonPrimitive()
                && e.getAsJsonPrimitive().isString()
                && !e.getAsString().isBlank();
    }
}
