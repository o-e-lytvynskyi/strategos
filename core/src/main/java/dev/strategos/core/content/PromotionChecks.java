package dev.strategos.core.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The lint of {@code Promotion} (CMB-11): {@code value} a number from 0, {@code thresholds} a list of ascending positive
 * experience totals, {@code ranks} one list of modifiers (path, op, value) per threshold. A building has no ranks.
 * Every problem is a {@link TemplateProblem}; nothing throws, whatever shape the data has.
 */
final class PromotionChecks {
    private PromotionChecks() {}

    /** The most ranks a class has, and the largest experience total or value the data may name. */
    static final int MAX_RANKS = 8;

    static final double MAX_EXPERIENCE = 1_000_000;

    /** Records at {@code /Promotion/...} what is wrong with a template's promotion data. */
    static void template(JsonObject json, String file, boolean building, List<TemplateProblem> problems) {
        JsonElement e = json.get("Promotion");
        if (e == null) {
            return;
        }
        if (!e.isJsonObject()) {
            problems.add(TemplateProblem.error(file, "/Promotion", "expected an object"));
            return;
        }
        JsonObject o = e.getAsJsonObject();
        Fields f = new Fields(file, problems);
        f.known(o, "/Promotion", "value", "thresholds", "ranks");
        if (o.has("value")) {
            AbilityKinds.number(o, "/Promotion", "value", 0, MAX_EXPERIENCE, true, f);
        }
        if (building && (o.has("thresholds") || o.has("ranks"))) {
            problems.add(TemplateProblem.error(file, "/Promotion", "a building has no ranks: only a value"));
            return;
        }
        JsonElement t = o.get("thresholds");
        int count = -1;
        if (t != null) {
            count = thresholds(t, file, problems);
        }
        JsonElement r = o.get("ranks");
        if (r != null) {
            if (t == null) {
                problems.add(TemplateProblem.error(file, "/Promotion/ranks", "ranks need thresholds"));
            }
            ranks(r, count, f);
        }
    }

    /** The thresholds' count, or -1 when they are not a well-formed list. */
    private static int thresholds(JsonElement e, String file, List<TemplateProblem> problems) {
        String at = "/Promotion/thresholds";
        if (!e.isJsonArray()
                || e.getAsJsonArray().isEmpty()
                || e.getAsJsonArray().size() > MAX_RANKS) {
            problems.add(
                    TemplateProblem.error(file, at, "expected a list of one to " + MAX_RANKS + " experience totals"));
            return -1;
        }
        JsonArray list = e.getAsJsonArray();
        double last = 0;
        boolean ok = true;
        for (int k = 0; k < list.size(); k++) {
            if (!(list.get(k) instanceof JsonPrimitive p) || !p.isNumber() || !Double.isFinite(p.getAsDouble())) {
                problems.add(TemplateProblem.error(file, at + "/" + k, "expected a number"));
                ok = false;
                continue;
            }
            double v = p.getAsDouble();
            if (v <= last || v > MAX_EXPERIENCE) {
                problems.add(TemplateProblem.error(
                        file,
                        at + "/" + k,
                        "expected a total above the one before (" + last + ") and at most " + MAX_EXPERIENCE + ", got "
                                + v));
                ok = false;
            } else {
                last = v;
            }
        }
        return ok ? list.size() : -1;
    }

    private static void ranks(JsonElement e, int thresholds, Fields f) {
        String at = "/Promotion/ranks";
        if (!e.isJsonArray()) {
            f.problems().add(TemplateProblem.error(f.file(), at, "expected a list with one modifier list per rank"));
            return;
        }
        JsonArray list = e.getAsJsonArray();
        if (thresholds >= 0 && list.size() != thresholds) {
            f.problems()
                    .add(TemplateProblem.error(
                            f.file(), at, "expected " + thresholds + " ranks, one per threshold, got " + list.size()));
        }
        for (int k = 0; k < list.size(); k++) {
            JsonElement rank = list.get(k);
            if (!rank.isJsonArray()) {
                f.problems().add(TemplateProblem.error(f.file(), at + "/" + k, "expected a list of modifiers"));
            } else if (!rank.getAsJsonArray().isEmpty()) {
                AbilityKinds.modifierList(rank, at + "/" + k, f);
            }
        }
    }

    /** The checks across templates: every rank modifier's path is one the core or some template has a number at. */
    static void cross(List<AbilityChecks.Source> sources, List<TemplateProblem> problems) {
        Set<String> paths = null;
        for (AbilityChecks.Source s : sources) {
            if (s.building()
                    || !(s.json().get("Promotion") instanceof JsonObject o)
                    || !(o.get("ranks") instanceof JsonArray ranks)) {
                continue;
            }
            for (int k = 0; k < ranks.size(); k++) {
                if (!(ranks.get(k) instanceof JsonArray mods)) {
                    continue;
                }
                for (int m = 0; m < mods.size(); m++) {
                    if (mods.get(m) instanceof JsonObject mod
                            && mod.get("path") instanceof JsonPrimitive path
                            && path.isString()) {
                        paths = paths == null ? AbilityChecks.numberPaths(sources) : paths;
                        if (!paths.contains(path.getAsString())) {
                            problems.add(TemplateProblem.error(
                                    s.file(),
                                    "/Promotion/ranks/" + k + "/" + m + "/path",
                                    "no template has a number at '" + path.getAsString() + "'"));
                        }
                    }
                }
            }
        }
    }

    /** The promotion {@code json} declares if it is well formed, as a set built from checked data holds it. */
    static @Nullable PromotionDef def(JsonObject json) {
        List<TemplateProblem> problems = new ArrayList<>();
        template(json, "", false, problems);
        if (!problems.isEmpty() || !(json.get("Promotion") instanceof JsonObject o) || !o.has("thresholds")) {
            return null;
        }
        List<Long> thresholds = new ArrayList<>();
        for (JsonElement t : o.getAsJsonArray("thresholds")) {
            thresholds.add(Math.round(t.getAsDouble() * PromotionDef.SCALE));
        }
        List<List<PromotionDef.Mod>> ranks = new ArrayList<>();
        JsonElement r = o.get("ranks");
        for (int k = 0; k < thresholds.size(); k++) {
            List<PromotionDef.Mod> mods = new ArrayList<>();
            if (r != null) {
                for (JsonElement m : r.getAsJsonArray().get(k).getAsJsonArray()) {
                    JsonObject mod = m.getAsJsonObject();
                    mods.add(new PromotionDef.Mod(
                            mod.get("path").getAsString(),
                            mod.get("op").getAsString(),
                            mod.get("value").getAsDouble()));
                }
            }
            ranks.add(mods);
        }
        return new PromotionDef(thresholds, ranks);
    }
}
