package dev.strategos.core.content.balance;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.strategos.core.content.CorePaths;
import dev.strategos.core.content.Derivation;
import dev.strategos.core.content.RawTemplate;
import dev.strategos.core.content.TechDef;
import dev.strategos.core.content.TechEffect;
import dev.strategos.core.content.TemplateCompiler;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.content.ValuePath;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.shared.TemplateId;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * The balance sheet (DATA-04): every number of every concrete template, where it comes from (the template in its chain
 * that sets it, the relationship that derives it, an override and its reason, or the core's default for a path the
 * core reads, under the provenance key {@value #CORE}) and its provenance note, with the
 * Normal opening's timing targets, every override, and the resource sinks. Rendered as Markdown and CSV from a compiled
 * set; its {@link #problems()} are what the build refuses: a number set without a provenance note, a note for a number
 * nobody sets, and a gatherable resource spent by fewer than two templates.
 */
public final class BalanceSheet {
    /** A timing target of the Normal opening: what, by when (m:ss), and why. */
    public record Target(String event, String by, String note) {}

    /** One number of a concrete template. */
    public record Row(TemplateId template, String path, BigDecimal value, String source, String provenance) {}

    /**
     * A {@code strategos:resource_yield} entry (ECO-02): the block or tag, its kind, what it yields and how much, and
     * for forage what it regrows a second (ECO-05).
     */
    public record Yield(
            String key, String kind, String resource, String subtype, BigDecimal amount, BigDecimal regrow) {
        /** An entry that does not regrow. */
        public Yield(String key, String kind, String resource, String subtype, BigDecimal amount) {
            this(key, kind, resource, subtype, amount, BigDecimal.ZERO);
        }
    }

    /**
     * One load a gatherer carries (ECO-05): its template, the resource and subtype, the rate a second and the most it
     * carries, and how long a full load takes at that rate: {@code ticks} = ceil(20 × capacity ÷ rate), as the core
     * gathers it.
     */
    public record Load(
            TemplateId template, String resource, String subtype, BigDecimal rate, BigDecimal capacity, long ticks) {}

    /**
     * How a torch bearer burns a building down (CMB-03): {@code hits} blows of its fire, each times 0.9 to the
     * building's {@code Resistance/fire}, added up as the core adds them, take its hit points; ten bearers striking
     * together from one tick finish in {@code tenTicks}: the wind-up, then a period for each further round of ten.
     */
    public record Burn(TemplateId attacker, TemplateId building, BigDecimal perHit, long hits, long tenTicks) {}

    /** The provenance key of the resource yields: notes by entry key. */
    public static final String YIELDS = "resource_yield";

    /** The provenance key of the core's own defaults, for the paths a template leaves out ({@link CorePaths}). */
    public static final String CORE = "core";

    private final List<Row> rows = new ArrayList<>();
    private final List<Derivation> overrides = new ArrayList<>();
    private final List<Derivation> derived = new ArrayList<>();
    private final List<Target> targets;
    private final List<Yield> yields = new ArrayList<>();
    private final List<String> yieldNotes = new ArrayList<>();
    private final Map<String, List<String>> gatherers = new TreeMap<>();
    private final List<Load> loads = new ArrayList<>();
    private final List<Burn> burns = new ArrayList<>();
    private final Map<String, List<String>> spenders = new TreeMap<>();
    private final List<String> problems = new ArrayList<>();

    private BalanceSheet(List<Target> targets) {
        this.targets = List.copyOf(targets);
    }

    /**
     * The sheet of {@code result}, compiled from {@code raws}, with {@code provenance} (for each template, the note of
     * each value path it sets) and {@code targets}.
     */
    public static BalanceSheet of(
            TemplateCompiler.Result result,
            List<RawTemplate> raws,
            Map<String, Map<String, String>> provenance,
            List<Target> targets) {
        return of(result, raws, provenance, targets, List.of());
    }

    /** As the four-argument form, with the data map's resource yields, each needing its provenance note too. */
    public static BalanceSheet of(
            TemplateCompiler.Result result,
            List<RawTemplate> raws,
            Map<String, Map<String, String>> provenance,
            List<Target> targets,
            List<Yield> yields) {
        BalanceSheet sheet = new BalanceSheet(targets);
        Map<String, JsonObject> byId = new TreeMap<>();
        raws.forEach(r -> byId.put(r.id().value(), r.json()));
        Map<String, Derivation> derivations = new TreeMap<>();
        result.derivations().forEach(d -> derivations.put(d.template().value() + "#" + d.path(), d));
        Map<String, TreeSet<String>> used = new TreeMap<>();
        List<UnitTemplate> concrete = new ArrayList<>(result.set().all());
        concrete.sort((a, b) -> a.id().value().compareTo(b.id().value()));
        for (UnitTemplate t : concrete) {
            JsonObject flat = Objects.requireNonNull(result.set().flattened(t.id()));
            List<String> paths = new ArrayList<>();
            numbers(flat, "", paths);
            paths.removeIf(p -> p.startsWith("Blueprint/")); // a building's shape is geometry, not balance
            for (String path : paths) {
                BigDecimal value =
                        new BigDecimal(Objects.requireNonNull(at(flat, path)).getAsString());
                Derivation d = derivations.get(t.id().value() + "#" + path);
                if (d != null) {
                    String source = d.overridden()
                            ? "override of " + d.formula() + " (" + plain(d.derived()) + ")"
                            : "derived: " + d.formula();
                    sheet.rows.add(new Row(t.id(), path, value, source, d.note()));
                    (d.overridden() ? sheet.overrides : sheet.derived).add(d);
                } else {
                    String key = keyPath(path);
                    String author = author(byId, t.id().value(), key, new ArrayList<>());
                    String note = author == null
                            ? null
                            : provenance
                                    .getOrDefault(author, Collections.emptyMap())
                                    .get(key);
                    if (author != null) {
                        used.computeIfAbsent(author, a -> new TreeSet<>()).add(key);
                    }
                    if (note == null || note.isBlank()) {
                        sheet.problems.add(t.id() + " " + path + " (set in " + author + "): no provenance note");
                    }
                    sheet.rows.add(new Row(t.id(), path, value, "set in " + author, note == null ? "" : note));
                }
                sheet.sinks(t.id(), path, value);
            }
            sheet.loads(t.id(), flat);
            for (String path : CorePaths.all()) {
                if (t.isBuilding() || paths.contains(path)) { // the core's unit defaults mean nothing to a building
                    continue;
                }
                ValuePath vp = Objects.requireNonNull(result.set().valuePaths().byPath(path));
                BigDecimal value = BigDecimal.valueOf(result.set().value(t.netIndex(), vp.id()));
                String note =
                        provenance.getOrDefault(CORE, Collections.emptyMap()).get(path);
                used.computeIfAbsent(CORE, a -> new TreeSet<>()).add(path);
                if (note == null || note.isBlank()) {
                    sheet.problems.add(t.id() + " " + path + " (the core's default): no provenance note");
                }
                sheet.rows.add(new Row(t.id(), path, value, "the core's default", note == null ? "" : note));
            }
        }
        sheet.burns(result.set(), concrete);
        for (Yield y : yields) {
            String note =
                    provenance.getOrDefault(YIELDS, Collections.emptyMap()).get(y.key());
            used.computeIfAbsent(YIELDS, a -> new TreeSet<>()).add(y.key());
            if (note == null || note.isBlank()) {
                sheet.problems.add("resource yield " + y.key() + ": no provenance note");
            }
            sheet.yields.add(y);
            sheet.yieldNotes.add(note == null ? "" : note);
        }
        for (TechDef tech :
                result.set().techs()) { // a tech's numbers need notes, keyed by its id; the sheet has no rows for them
            for (String path : techNumbers(tech)) {
                String note = provenance
                        .getOrDefault(tech.id().value(), Collections.emptyMap())
                        .get(path);
                used.computeIfAbsent(tech.id().value(), a -> new TreeSet<>()).add(path);
                if (note == null || note.isBlank()) {
                    sheet.problems.add("tech " + tech.id() + " " + path + ": no provenance note");
                }
            }
        }
        provenance.forEach((template, notes) -> notes.keySet().forEach(key -> {
            if (!used.getOrDefault(template, new TreeSet<>()).contains(key)) {
                sheet.problems.add("provenance names " + template + " " + key + ", which sets no number there");
            }
        }));
        sheet.gatherers.forEach((resource, who) -> {
            int spent = sheet.spenders.getOrDefault(resource, List.of()).size();
            if (spent < 2) {
                sheet.problems.add("resource " + resource + " is gathered (" + String.join(", ", who) + ") but"
                        + " spent by " + spent + " template" + (spent == 1 ? "" : "s") + "; at least two must cost it");
            }
        });
        return sheet;
    }

    /** The paths of a tech's numbers: {@code cost/<resource>}, {@code time}, the building counts of {@code requires} at their JSON pointers, and {@code effects/<n>/value}. */
    private static List<String> techNumbers(TechDef tech) {
        List<String> paths = new ArrayList<>();
        new TreeMap<>(tech.cost()).keySet().forEach(r -> paths.add("cost/" + r));
        paths.add("time");
        tech.requires().buildingCounts("requires", (b, pointer) -> paths.add(pointer));
        for (int i = 0; i < tech.effects().size(); i++) {
            if (tech.effects().get(i).op() != TechEffect.Op.TOKENS) {
                paths.add("effects/" + i + "/value");
            }
        }
        return paths;
    }

    public List<Row> rows() {
        return List.copyOf(rows);
    }

    /** How long torches take to burn each building, by attacker and building. */
    public List<Burn> burns() {
        return List.copyOf(burns);
    }

    /** Every torch bearer against every building with hit points. */
    private void burns(TemplateSet set, List<UnitTemplate> templates) {
        ValuePath fire = set.valuePaths().byPath("Attack/Structure/fire");
        ValuePath prepare = set.valuePaths().byPath("Attack/Structure/prepare");
        ValuePath repeat = set.valuePaths().byPath("Attack/Structure/repeat");
        ValuePath resist = set.valuePaths().byPath("Resistance/fire");
        ValuePath health = set.valuePaths().byPath(CorePaths.HEALTH);
        if (fire == null || prepare == null || repeat == null || health == null) {
            return;
        }
        for (UnitTemplate a : templates) {
            double blow = set.value(a.netIndex(), fire.id());
            if (a.isBuilding() || !(blow > 0)) {
                continue;
            }
            for (UnitTemplate b : templates) {
                double hp = set.value(b.netIndex(), health.id());
                if (!b.isBuilding() || !(hp > 0)) {
                    continue;
                }
                double level = resist == null ? 0 : set.value(b.netIndex(), resist.id());
                double dealt = (float) blow * DetMath.pow(0.9, Double.isFinite(level) ? level : 0);
                long hits = 0;
                double taken = 0;
                while (taken < hp) { // as the core adds blows up
                    taken += dealt;
                    hits++;
                }
                long rounds = (hits + 9) / 10;
                long ticks = (long) set.value(a.netIndex(), prepare.id())
                        + (rounds - 1) * (long) set.value(a.netIndex(), repeat.id());
                burns.add(new Burn(
                        a.id(), b.id(), BigDecimal.valueOf(dealt).setScale(3, RoundingMode.HALF_UP), hits, ticks));
            }
        }
    }

    /** The loads gatherers carry, by template, resource and subtype. */
    public List<Load> loads() {
        return List.copyOf(loads);
    }

    public List<Derivation> overrides() {
        return List.copyOf(overrides);
    }

    /** What the build refuses; empty for a good sheet. */
    public List<String> problems() {
        return List.copyOf(problems);
    }

    /** Template {@code template}'s loads: each rate above 0 with the capacity for its resource. */
    private void loads(TemplateId template, JsonObject flat) {
        JsonElement rates = at(flat, "ResourceGatherer/rates");
        if (rates == null || !rates.isJsonObject()) {
            return;
        }
        for (Map.Entry<String, JsonElement> r :
                new TreeMap<>(rates.getAsJsonObject().asMap()).entrySet()) {
            if (!r.getValue().isJsonObject()) {
                continue;
            }
            JsonElement cap = at(flat, "ResourceGatherer/capacities/" + r.getKey());
            BigDecimal capacity = cap == null ? BigDecimal.ZERO : new BigDecimal(cap.getAsString());
            for (Map.Entry<String, JsonElement> st :
                    new TreeMap<>(r.getValue().getAsJsonObject().asMap()).entrySet()) {
                BigDecimal rate = new BigDecimal(st.getValue().getAsString());
                if (rate.signum() <= 0 || capacity.signum() <= 0) {
                    continue;
                }
                BigDecimal ticks = capacity.multiply(BigDecimal.valueOf(20)).divide(rate, 0, RoundingMode.CEILING);
                loads.add(new Load(template, r.getKey(), st.getKey(), rate, capacity, ticks.longValueExact()));
            }
        }
    }

    /** Gathered resources and who gathers them, and spent resources and who spends them, for {@link #markdown()}. */
    private void sinks(TemplateId template, String path, BigDecimal value) {
        if (value.signum() <= 0) {
            return;
        }
        String[] p = path.split("/", -1);
        if (p.length >= 3 && p[0].equals("ResourceGatherer") && p[1].equals("rates")) {
            gatherers.computeIfAbsent(p[2], r -> new ArrayList<>()).add(template.value());
        }
        if (p.length == 3 && p[0].equals("Cost") && p[1].equals("resources")) {
            spenders.computeIfAbsent(p[2], r -> new ArrayList<>()).add(template.value());
        }
    }

    public String markdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Balance sheet\n\n");
        sb.append("Generated by `./gradlew :core:balanceSheet` from the templates and `strategos/balance/` in the mod's"
                + " data; `BalanceSheetTest` fails while this file is stale. A change is approved like a golden"
                + " (DATA-04): every number is Strategos's own, authored at Minecraft's scale (ADR-0005).\n\n");
        sb.append("## Timing targets: the Normal opening\n\n");
        sb.append("| Event | By | Note |\n|---|---|---|\n");
        for (Target t : targets) {
            sb.append("| ")
                    .append(cell(t.event()))
                    .append(" | ")
                    .append(cell(t.by()))
                    .append(" | ")
                    .append(cell(t.note()))
                    .append(" |\n");
        }
        sb.append("\n## Templates\n");
        TemplateId current = null;
        for (Row r : rows) {
            if (!r.template().equals(current)) {
                current = r.template();
                sb.append("\n### ").append(current.value()).append("\n\n");
                sb.append("| Value | Amount | Source | Provenance |\n|---|---|---|---|\n");
            }
            sb.append("| ")
                    .append(cell(r.path()))
                    .append(" | ")
                    .append(plain(r.value()))
                    .append(" | ")
                    .append(cell(r.source()))
                    .append(" | ")
                    .append(cell(r.provenance()))
                    .append(" |\n");
        }
        sb.append("\n## Relationships\n\n");
        if (derived.isEmpty()) {
            sb.append("None yet.\n");
        } else {
            sb.append("| Template | Value | Formula | Gives | Note |\n|---|---|---|---|---|\n");
            for (Derivation d : derived) {
                sb.append("| ")
                        .append(d.template().value())
                        .append(" | ")
                        .append(cell(d.path()))
                        .append(" | ")
                        .append(cell(d.formula()))
                        .append(" | ")
                        .append(plain(d.value()))
                        .append(" | ")
                        .append(cell(d.note()))
                        .append(" |\n");
            }
        }
        sb.append("\n## Overrides\n\n");
        if (overrides.isEmpty()) {
            sb.append("None.\n");
        } else {
            sb.append("| Template | Value | Set to | The formula gives | Reason |\n|---|---|---|---|---|\n");
            for (Derivation d : overrides) {
                sb.append("| ")
                        .append(d.template().value())
                        .append(" | ")
                        .append(cell(d.path()))
                        .append(" | ")
                        .append(plain(d.value()))
                        .append(" | ")
                        .append(plain(d.derived()))
                        .append(" | ")
                        .append(cell(d.note()))
                        .append(" |\n");
            }
        }
        sb.append("\n## Resource yields\n\n");
        if (yields.isEmpty()) {
            sb.append("None.\n");
        } else {
            sb.append("| Block or tag | Kind | Yields | Amount | Regrows a second | Provenance |\n");
            sb.append("|---|---|---|---|---|---|\n");
            for (int i = 0; i < yields.size(); i++) {
                Yield y = yields.get(i);
                String what = y.resource().isEmpty() ? "nothing" : y.resource() + " (" + y.subtype() + ")";
                sb.append("| ")
                        .append(cell(y.key()))
                        .append(" | ")
                        .append(y.kind())
                        .append(" | ")
                        .append(what)
                        .append(" | ")
                        .append(plain(y.amount()))
                        .append(" | ")
                        .append(y.regrow().signum() > 0 ? plain(y.regrow()) : "–")
                        .append(" | ")
                        .append(cell(yieldNotes.get(i)))
                        .append(" |\n");
            }
        }
        sb.append("\n## Gathering\n\n");
        if (loads.isEmpty()) {
            sb.append("No template gathers yet.\n");
        } else {
            sb.append(
                    "A full load takes ceil(20 × carried ÷ rate) ticks of gathering, walking not counted (ECO-05).\n\n");
            sb.append("| Template | Resource | Rate a second | Carries | A load in |\n|---|---|---|---|---|\n");
            for (Load l : loads) {
                sb.append("| ")
                        .append(l.template().value())
                        .append(" | ")
                        .append(l.resource())
                        .append(" (")
                        .append(l.subtype())
                        .append(") | ")
                        .append(plain(l.rate()))
                        .append(" | ")
                        .append(plain(l.capacity()))
                        .append(" | ")
                        .append(seconds(l.ticks()))
                        .append(" |\n");
            }
        }
        sb.append("\n## Burning down\n\n");
        if (burns.isEmpty()) {
            sb.append("No template carries a torch yet.\n");
        } else {
            sb.append("A torch's fire times 0.9 to the building's fire resistance a blow; ten bearers striking together"
                    + " from one tick take the wind-up and a period for each further round of ten (CMB-03).\n\n");
            sb.append("| Attacker | Building | Fire a blow | Blows | Ten bearers in |\n|---|---|---|---|---|\n");
            for (Burn b : burns) {
                sb.append("| ")
                        .append(b.attacker().value())
                        .append(" | ")
                        .append(b.building().value())
                        .append(" | ")
                        .append(plain(b.perHit()))
                        .append(" | ")
                        .append(b.hits())
                        .append(" | ")
                        .append(seconds(b.tenTicks()))
                        .append(" |\n");
            }
        }
        sb.append("\n## Cancelling and demolishing\n\n");
        sb.append(
                "A foundation or a site cancelled gives back its cost but for the share built, cost × (1000 − progress) ÷"
                        + " 1000 per resource, its progress in thousandths, rounded down to the thousandth; a finished building"
                        + " demolished gives nothing back (BLD-09). Repairing is free, at half the build speed (D-15).\n");
        sb.append("\n## Resource sinks\n\n");
        TreeSet<String> resources = new TreeSet<>(gatherers.keySet());
        resources.addAll(spenders.keySet());
        if (resources.isEmpty()) {
            sb.append("No template gathers or costs a resource yet.\n");
        } else {
            sb.append("| Resource | Gathered by | Spent by |\n|---|---|---|\n");
            for (String r : resources) {
                sb.append("| ")
                        .append(r)
                        .append(" | ")
                        .append(String.join(", ", gatherers.getOrDefault(r, List.of())))
                        .append(" | ")
                        .append(String.join(", ", spenders.getOrDefault(r, List.of())))
                        .append(" |\n");
            }
        }
        return sb.toString();
    }

    /** {@code ticks} as seconds, to the tenth, and the ticks: "16.7 s (334 ticks)". */
    private static String seconds(long ticks) {
        BigDecimal s = BigDecimal.valueOf(ticks).divide(BigDecimal.valueOf(20), 1, RoundingMode.HALF_UP);
        return s.toPlainString() + " s (" + ticks + " ticks)";
    }

    public String csv() {
        StringBuilder sb = new StringBuilder("template,path,value,source,provenance\n");
        for (Row r : rows) {
            sb.append(r.template().value())
                    .append(',')
                    .append(r.path())
                    .append(',')
                    .append(plain(r.value()))
                    .append(',')
                    .append(quote(r.source()))
                    .append(',')
                    .append(quote(r.provenance()))
                    .append('\n');
        }
        return sb.toString();
    }

    /**
     * The template in {@code id}'s chain that sets {@code key}: its own file, then its mixins from the last, then its
     * parent's chain; null if none does.
     */
    private static @Nullable String author(Map<String, JsonObject> raws, String id, String key, List<String> seen) {
        JsonObject raw = raws.get(id);
        if (raw == null || seen.contains(id)) {
            return null;
        }
        seen.add(id);
        if (at(raw, key) != null) {
            return id;
        }
        JsonElement mixins = raw.get("mixins");
        if (mixins != null && mixins.isJsonArray()) {
            for (int i = mixins.getAsJsonArray().size() - 1; i >= 0; i--) {
                String found = author(raws, mixins.getAsJsonArray().get(i).getAsString(), key, seen);
                if (found != null) {
                    return found;
                }
            }
        }
        JsonElement parent = raw.get("parent");
        return parent == null ? null : author(raws, parent.getAsString(), key, seen);
    }

    /** A path up to its first array index: arrays are replaced whole, so whoever sets the array sets its numbers. */
    private static String keyPath(String path) {
        StringBuilder sb = new StringBuilder();
        for (String part : path.split("/", -1)) {
            if (!part.isEmpty() && part.chars().allMatch(Character::isDigit)) {
                break;
            }
            sb.append(sb.length() == 0 ? "" : "/").append(part);
        }
        return sb.toString();
    }

    private static void numbers(JsonElement e, String prefix, List<String> out) {
        if (e.isJsonObject()) {
            for (String key : new TreeSet<>(e.getAsJsonObject().keySet())) {
                numbers(e.getAsJsonObject().get(key), prefix.isEmpty() ? key : prefix + "/" + key, out);
            }
        } else if (e.isJsonArray()) {
            for (int i = 0; i < e.getAsJsonArray().size(); i++) {
                numbers(e.getAsJsonArray().get(i), prefix + "/" + i, out);
            }
        } else if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
            out.add(prefix);
        }
    }

    private static @Nullable JsonElement at(JsonObject json, String path) {
        JsonElement e = json;
        for (String part : path.split("/", -1)) {
            if (e != null && e.isJsonObject()) {
                e = e.getAsJsonObject().get(part);
            } else if (e != null
                    && e.isJsonArray()
                    && !part.isEmpty()
                    && part.chars().allMatch(Character::isDigit)) {
                int i = Integer.parseInt(part);
                e = i < e.getAsJsonArray().size() ? e.getAsJsonArray().get(i) : null;
            } else {
                return null;
            }
        }
        return e;
    }

    private static String plain(@Nullable BigDecimal v) {
        if (v == null) {
            return "?";
        }
        BigDecimal s = v.stripTrailingZeros();
        return (s.scale() < 0 ? s.setScale(0) : s).toPlainString();
    }

    private static String cell(String s) {
        return s.replace("|", "\\|").replace("\n", " ");
    }

    private static String quote(String s) {
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
