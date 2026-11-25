package dev.strategos.core.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.strategos.core.content.AbilityDef.Aim;
import dev.strategos.core.shared.TemplateId;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The closed vocabulary of abilities (UNIT-10, 04 §7.6): the effect kinds and target pickers a template may name, which
 * targets each takes, and the check of each kind's parameters, so a typo is a lint line with a pointer, never a crash.
 * {@code sim.ability.AbilityEffects} and {@code AbilityTargets} hold the behaviour under exactly these ids (a test
 * compares the two); a new id is added here and there together.
 */
public final class AbilityKinds {
    public static final String DAMAGE = "damage";
    public static final String HEAL = "heal";
    public static final String APPLY_MODIFIERS = "apply_modifiers";
    public static final String SPAWN = "spawn";
    public static final String CAPTURE_STRENGTH = "capture_strength";
    public static final String TELEPORT = "teleport";

    /** Target pickers (autocast): the user itself, the nearest enemy within the ability's range. */
    public static final String SELF = "self";

    public static final String NEAREST_ENEMY_IN_RANGE = "nearest_enemy_in_range";

    /** The path {@code capture_strength} adds to (CMB-10's {@code Capture/strength}). */
    public static final String CAPTURE_PATH = "Capture/strength";

    /** The longest a timed effect lasts, in ticks: an hour. */
    public static final int MAX_TICKS = 72_000;

    public static final int MAX_SPAWN = 8;
    public static final int MAX_MODIFIERS = 8;

    private AbilityKinds() {}

    /** The damage types a {@code damage} effect names. */
    public static List<String> damageTypes() {
        return List.of("hack", "pierce", "crush"); // fire is only for buildings (CMB-03), which abilities do not reach
    }

    /** The operations an {@code apply_modifiers} entry names. */
    public static List<String> modifierOps() {
        return List.of("add", "mul", "replace");
    }

    /** The effect kinds, ascending. */
    public static List<String> effects() {
        return List.of(APPLY_MODIFIERS, CAPTURE_STRENGTH, DAMAGE, HEAL, SPAWN, TELEPORT);
    }

    /** The target pickers, ascending. */
    public static List<String> pickers() {
        return List.of(NEAREST_ENEMY_IN_RANGE, SELF);
    }

    /** Whether {@code kind} holds for a duration, whose modifiers the timed store takes back. */
    public static boolean timed(String kind) {
        return kind.equals(APPLY_MODIFIERS) || kind.equals(CAPTURE_STRENGTH);
    }

    /** The targets an effect of {@code kind} makes sense for. */
    static List<Aim> aims(String kind) {
        return switch (kind) {
            case DAMAGE -> List.of(Aim.UNIT, Aim.AREA);
            case HEAL, APPLY_MODIFIERS, CAPTURE_STRENGTH -> List.of(Aim.NONE, Aim.UNIT, Aim.AREA);
            case SPAWN -> List.of(Aim.NONE, Aim.POINT, Aim.UNIT, Aim.AREA);
            case TELEPORT -> List.of(Aim.POINT);
            default -> List.of();
        };
    }

    /** The targets a picker can supply. */
    static List<Aim> pickerAims(String picker) {
        return switch (picker) {
            case SELF -> List.of(Aim.NONE, Aim.UNIT, Aim.AREA);
            case NEAREST_ENEMY_IN_RANGE -> List.of(Aim.POINT, Aim.UNIT, Aim.AREA);
            default -> List.of();
        };
    }

    /** Checks {@code params} of an effect {@code kind} at {@code pointer}, recording what is wrong. */
    static void check(String kind, JsonObject params, String pointer, Fields f) {
        switch (kind) {
            case DAMAGE -> {
                f.known(params, pointer, "type", "amount");
                oneOf(params, pointer, "type", damageTypes(), f);
                number(params, pointer, "amount", 0, 10_000, false, f);
            }
            case HEAL -> {
                f.known(params, pointer, "amount");
                number(params, pointer, "amount", 0, 10_000, false, f);
            }
            case APPLY_MODIFIERS -> {
                f.known(params, pointer, "ticks", "modifiers", "on");
                integer(params, pointer, "ticks", 1, MAX_TICKS, f);
                on(params, pointer, f);
                modifiers(params, pointer, f);
            }
            case CAPTURE_STRENGTH -> {
                f.known(params, pointer, "ticks", "amount", "on");
                integer(params, pointer, "ticks", 1, MAX_TICKS, f);
                on(params, pointer, f);
                number(params, pointer, "amount", 0, 1_000, false, f);
            }
            case SPAWN -> {
                f.known(params, pointer, "template", "count");
                JsonElement t = params.get("template");
                if (t == null || !t.isJsonPrimitive() || !t.getAsJsonPrimitive().isString()) {
                    f.problems().add(TemplateProblem.error(f.file(), pointer + "/template", "expected a template id"));
                } else {
                    try {
                        new TemplateId(t.getAsString());
                    } catch (IllegalArgumentException e) {
                        f.problems()
                                .add(TemplateProblem.error(
                                        f.file(), pointer + "/template", "not a template id: " + t.getAsString()));
                    }
                }
                if (params.has("count")) {
                    integer(params, pointer, "count", 1, MAX_SPAWN, f);
                }
            }
            case TELEPORT -> f.known(params, pointer);
            default -> {}
        }
    }

    /** The optional {@code on}: whom a timed effect aimed at a unit may reach, ally (the default), enemy or any. */
    private static void on(JsonObject params, String pointer, Fields f) {
        if (params.has("on")) {
            oneOf(params, pointer, "on", List.of("ally", "enemy", "any"), f);
        }
    }

    private static void modifiers(JsonObject params, String pointer, Fields f) {
        modifierList(params.get("modifiers"), pointer + "/modifiers", f);
    }

    /** Checks a list of one to {@link #MAX_MODIFIERS} modifier objects ({@code path}, {@code op}, {@code value}) at {@code at}. */
    static void modifierList(@Nullable JsonElement e, String at, Fields f) {
        if (e == null || !e.isJsonArray() || e.getAsJsonArray().isEmpty()) {
            f.problems().add(TemplateProblem.error(f.file(), at, "expected a list of at least one modifier"));
            return;
        }
        JsonArray list = e.getAsJsonArray();
        if (list.size() > MAX_MODIFIERS) {
            f.problems().add(TemplateProblem.error(f.file(), at, "at most " + MAX_MODIFIERS + " modifiers"));
            return;
        }
        for (int k = 0; k < list.size(); k++) {
            String m = at + "/" + k;
            if (!list.get(k).isJsonObject()) {
                f.problems().add(TemplateProblem.error(f.file(), m, "expected an object"));
                continue;
            }
            JsonObject mod = list.get(k).getAsJsonObject();
            f.known(mod, m, "path", "op", "value");
            JsonElement path = mod.get("path");
            if (path == null
                    || !path.isJsonPrimitive()
                    || !path.getAsJsonPrimitive().isString()
                    || path.getAsString().isBlank()) {
                f.problems().add(TemplateProblem.error(f.file(), m + "/path", "expected a value path"));
            }
            oneOf(mod, m, "op", modifierOps(), f);
            JsonElement v = mod.get("value");
            if (!(v instanceof JsonPrimitive p) || !p.isNumber() || !Double.isFinite(p.getAsDouble())) {
                f.problems().add(TemplateProblem.error(f.file(), m + "/value", "expected a finite number"));
            }
        }
    }

    /** Records an error at {@code key} unless it is a string among {@code allowed}. */
    static void oneOf(JsonObject o, String pointer, String key, List<String> allowed, Fields f) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) {
            f.problems().add(TemplateProblem.error(f.file(), pointer + "/" + key, "expected one of " + allowed));
        } else if (!allowed.contains(e.getAsString())) {
            f.problems()
                    .add(TemplateProblem.error(
                            f.file(), pointer + "/" + key, "unknown '" + e.getAsString() + "'; expected " + allowed));
        }
    }

    /** A required number above {@code min} (or at it when {@code zero} allows) and at most {@code max}. */
    static void number(JsonObject o, String pointer, String key, double min, double max, boolean zero, Fields f) {
        JsonElement e = o.get(key);
        if (!(e instanceof JsonPrimitive p) || !p.isNumber() || !Double.isFinite(p.getAsDouble())) {
            f.problems().add(TemplateProblem.error(f.file(), pointer + "/" + key, "expected a number"));
            return;
        }
        double v = p.getAsDouble();
        if (v > max || v < min || (v == min && !zero)) {
            f.problems()
                    .add(TemplateProblem.error(
                            f.file(),
                            pointer + "/" + key,
                            "expected a number " + (zero ? "from " : "above ") + min + " to " + max + ", got " + v));
        }
    }

    /** A required whole number from {@code min} to {@code max}. */
    static void integer(JsonObject o, String pointer, String key, int min, int max, Fields f) {
        JsonElement e = o.get(key);
        if (!(e instanceof JsonPrimitive p) || !p.isNumber()) {
            f.problems().add(TemplateProblem.error(f.file(), pointer + "/" + key, "expected a whole number"));
            return;
        }
        double v = p.getAsDouble();
        if (v != Math.rint(v) || v < min || v > max) {
            f.problems()
                    .add(TemplateProblem.error(
                            f.file(),
                            pointer + "/" + key,
                            "expected a whole number from " + min + " to " + max + ", got " + v));
        }
    }
}
