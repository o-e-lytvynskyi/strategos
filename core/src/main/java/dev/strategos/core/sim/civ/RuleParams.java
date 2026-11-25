package dev.strategos.core.sim.civ;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.strategos.core.shared.CoreApi;

/**
 * Readers for a rule variant's parameters: each returns the value or throws {@link RuleFactory.BadParams} saying which
 * parameter (by its JSON pointer under the rule) is of the wrong shape, so a lint line never turns into a crash on
 * Gson's own exceptions.
 */
@CoreApi
public final class RuleParams {
    private RuleParams() {}

    /** An integer parameter, {@code fallback} when absent. */
    public static int integer(JsonObject params, String key, int fallback) {
        JsonElement e = params.get(key);
        if (e == null) {
            return fallback;
        }
        if (e instanceof JsonPrimitive p && p.isNumber()) {
            double d = p.getAsDouble();
            if (d == Math.rint(d) && Math.abs(d) <= Integer.MAX_VALUE) {
                return (int) d;
            }
        }
        throw bad(key, "an integer", e);
    }

    /** A required integer parameter. */
    public static int requiredInteger(JsonObject params, String key) {
        if (!params.has(key)) {
            throw new RuleFactory.BadParams("/params/" + key + ": required");
        }
        return integer(params, key, 0);
    }

    /** A string parameter, {@code fallback} when absent. */
    public static String string(JsonObject params, String key, String fallback) {
        JsonElement e = params.get(key);
        if (e == null) {
            return fallback;
        }
        if (e instanceof JsonPrimitive p && p.isString()) {
            return p.getAsString();
        }
        throw bad(key, "a string", e);
    }

    /** A boolean parameter, {@code fallback} when absent. */
    public static boolean bool(JsonObject params, String key, boolean fallback) {
        JsonElement e = params.get(key);
        if (e == null) {
            return fallback;
        }
        if (e instanceof JsonPrimitive p && p.isBoolean()) {
            return p.getAsBoolean();
        }
        throw bad(key, "a boolean", e);
    }

    private static RuleFactory.BadParams bad(String key, String expected, JsonElement got) {
        return new RuleFactory.BadParams("/params/" + key + ": expected " + expected + ", got " + got);
    }
}
