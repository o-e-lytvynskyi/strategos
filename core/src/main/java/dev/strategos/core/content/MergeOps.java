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
 *       without signs replaces them. Tokens come out sorted and unique.
 * </ul>
 *
 * Problems go to the list with their JSON pointer; the merge goes on, so every problem is reported.
 */
final class MergeOps {
    private MergeOps() {}

    /** The merged value, or null when the overlay disables the component. */
    static @Nullable JsonElement merge(
            @Nullable JsonElement base,
            JsonElement overlay,
            String file,
            String pointer,
            List<TemplateProblem> problems) {
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
}
