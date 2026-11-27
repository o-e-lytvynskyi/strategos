package dev.strategos.core.sim.civ;

import com.google.gson.JsonObject;
import dev.strategos.core.content.CivDef;
import dev.strategos.core.content.PlacementRule;
import dev.strategos.core.content.TemplateProblem;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.sim.build.Construction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * The closed, versioned catalogue of rule variants (ADR-0028): per seam, id to {@link RuleFactory}. Data selects, code
 * implements: a civilisation's {@code rules} names a variant and its parameters, and only the ids registered here
 * resolve. {@link #DEFAULT} is built in the core; a test may build a wider one with {@link #with}, and the same
 * catalogue must serve a match's creation, restore and replay (production always uses {@code DEFAULT}).
 *
 * <p><b>Ids never change behaviour.</b> A variant's behaviour is fixed under its id: a changed behaviour is a new id
 * ({@code direct@2}) and the old one stays, because old journals replay through it. Removing an id breaks them;
 * {@code RuleCatalogueTest} pins the set.
 */
@CoreApi
public final class RuleCatalogue {
    /** The id every seam has, and the one a missing entry means: today's behaviour. */
    public static final String DEFAULT_ID = "default";

    /** The core's catalogue: every seam with its {@code default}. */
    public static final RuleCatalogue DEFAULT = defaults();

    private final Map<SeamKind, SortedMap<String, RuleFactory<?>>> byKind;

    private RuleCatalogue(Map<SeamKind, SortedMap<String, RuleFactory<?>>> byKind) {
        this.byKind = byKind;
    }

    private static RuleCatalogue defaults() {
        Map<SeamKind, SortedMap<String, RuleFactory<?>>> m = new EnumMap<>(SeamKind.class);
        for (SeamKind k : SeamKind.values()) {
            m.put(k, new TreeMap<>());
        }
        add(m, SeamKind.POPULATION, RuleFactory.of(DEFAULT_ID, PopulationRule.class, p -> {
            int base = RuleParams.integer(p, "base", 0);
            if (base < 0) {
                throw new RuleFactory.BadParams("/params/base: expected an integer of at least 0, got " + base);
            }
            return base == 0 ? PopulationRule.DEFAULT : PopulationRule.housing(base);
        }));
        add(m, SeamKind.POPULATION, RuleFactory.of("fixed", PopulationRule.class, p -> {
            int cap = RuleParams.requiredInteger(p, "cap");
            if (cap < 0) {
                throw new RuleFactory.BadParams("/params/cap: expected an integer of at least 0, got " + cap);
            }
            return PopulationRule.fixed(cap);
        }));
        add(m, SeamKind.DELIVERY, RuleFactory.fixed(DEFAULT_ID, DeliveryRule.class, DeliveryRule.DEFAULT));
        add(m, SeamKind.DELIVERY, RuleFactory.fixed("direct", DeliveryRule.class, DeliveryRule.DIRECT));
        add(m, SeamKind.CONSTRUCTION, RuleFactory.fixed(DEFAULT_ID, ConstructionRule.class, ConstructionRule.DEFAULT));
        add(m, SeamKind.CONSTRUCTION, RuleFactory.of("self", ConstructionRule.class, p -> {
            double rate = RuleParams.requiredNumber(p, "rate");
            if (!(rate > 0) || Math.round(Construction.MICRO * rate) < 1 || rate > 1_000_000) {
                throw new RuleFactory.BadParams(
                        "/params/rate: expected a number above 0 (at least 0.000001, at most 1000000), got " + rate);
            }
            return ConstructionRule.selfBuilding(rate);
        }));
        add(m, SeamKind.UPKEEP, RuleFactory.fixed(DEFAULT_ID, UpkeepRule.class, UpkeepRule.DEFAULT));
        add(m, SeamKind.RESOURCES, RuleFactory.fixed(DEFAULT_ID, ResourceRule.class, ResourceRule.DEFAULT));
        add(m, SeamKind.PHASE, RuleFactory.fixed(DEFAULT_ID, PhaseRule.class, PhaseRule.DEFAULT));
        add(m, SeamKind.PLACEMENT, RuleFactory.fixed(DEFAULT_ID, PlacementRule.class, PlacementRule.DEFAULT));
        add(m, SeamKind.DEFEAT, RuleFactory.fixed(DEFAULT_ID, DefeatRule.class, DefeatRule.DEFAULT));
        return new RuleCatalogue(freeze(m));
    }

    private static void add(Map<SeamKind, SortedMap<String, RuleFactory<?>>> m, SeamKind kind, RuleFactory<?> factory) {
        if (factory.seam() != kind.type()) {
            throw new IllegalArgumentException(
                    "rule '" + factory.id() + "' makes " + factory.seam().getSimpleName() + ", but the "
                            + kind.dataName() + " seam takes " + kind.type().getSimpleName());
        }
        if (table(m, kind).put(factory.id(), factory) != null) {
            throw new IllegalArgumentException(
                    "the " + kind.dataName() + " seam already has a rule '" + factory.id() + "'");
        }
    }

    private static Map<SeamKind, SortedMap<String, RuleFactory<?>>> freeze(
            Map<SeamKind, SortedMap<String, RuleFactory<?>>> m) {
        Map<SeamKind, SortedMap<String, RuleFactory<?>>> out = new EnumMap<>(SeamKind.class);
        m.forEach((k, v) -> out.put(k, Collections.unmodifiableSortedMap(v)));
        return Collections.unmodifiableMap(out);
    }

    private static SortedMap<String, RuleFactory<?>> table(
            Map<SeamKind, SortedMap<String, RuleFactory<?>>> m, SeamKind kind) {
        return java.util.Objects.requireNonNull(m.get(kind)); // every kind has a table
    }

    /** A catalogue with {@code factory} added to {@code kind}; the factory's seam must be the kind's, its id new. */
    public RuleCatalogue with(SeamKind kind, RuleFactory<?> factory) {
        Map<SeamKind, SortedMap<String, RuleFactory<?>>> m = new EnumMap<>(SeamKind.class);
        byKind.forEach((k, v) -> m.put(k, new TreeMap<>(v)));
        add(m, kind, factory);
        return new RuleCatalogue(freeze(m));
    }

    /** The ids registered for {@code kind}, ascending. */
    public SortedSet<String> ids(SeamKind kind) {
        return Collections.unmodifiableSortedSet(
                new java.util.TreeSet<>(table(byKind, kind).keySet()));
    }

    /** The rule of {@code kind} the civilisation selects (or its default) with its parameters decoded; throws on a bad one. */
    public CivRules resolve(@Nullable CivDef civ) {
        if (civ == null) {
            return CivRules.DEFAULTS;
        }
        return new CivRules(
                create(civ, SeamKind.POPULATION, PopulationRule.class),
                create(civ, SeamKind.DELIVERY, DeliveryRule.class),
                create(civ, SeamKind.CONSTRUCTION, ConstructionRule.class),
                create(civ, SeamKind.UPKEEP, UpkeepRule.class),
                create(civ, SeamKind.RESOURCES, ResourceRule.class),
                create(civ, SeamKind.PHASE, PhaseRule.class),
                create(civ, SeamKind.PLACEMENT, PlacementRule.class),
                create(civ, SeamKind.DEFEAT, DefeatRule.class));
    }

    private <S> S create(CivDef civ, SeamKind kind, Class<S> type) {
        CivDef.Rule rule = civ.rules().get(kind.dataName());
        String id = rule == null ? DEFAULT_ID : rule.id();
        RuleFactory<?> f = table(byKind, kind).get(id);
        if (f == null) {
            throw new IllegalArgumentException(unknown(kind, id));
        }
        return type.cast(f.create(rule == null ? new JsonObject() : rule.params()));
    }

    private String unknown(SeamKind kind, String id) {
        return "unknown rule '" + id + "' for the " + kind.dataName() + " seam; known: "
                + String.join(", ", table(byKind, kind).keySet());
    }

    /** What is wrong with {@code civ}'s rules, with the civilisation's id for the file. */
    public List<TemplateProblem> problems(CivDef civ) {
        return problems(civ, civ.id().value());
    }

    /**
     * What is wrong with {@code civ}'s rules, each a problem of {@code file} at its JSON pointer: a seam no seam has, an
     * id the seam's catalogue lacks, parameters its factory refuses. Only a civilisation with none loads.
     */
    public List<TemplateProblem> problems(CivDef civ, String file) {
        List<TemplateProblem> out = new ArrayList<>();
        civ.rules().forEach((name, rule) -> {
            String at = "/rules/" + name;
            SeamKind kind = SeamKind.ofDataName(name);
            if (kind == null) {
                StringBuilder known = new StringBuilder();
                for (SeamKind k : SeamKind.values()) {
                    known.append(known.length() == 0 ? "" : ", ").append(k.dataName());
                }
                out.add(TemplateProblem.error(file, at, "unknown seam '" + name + "'; the seams are " + known));
                return;
            }
            RuleFactory<?> f = table(byKind, kind).get(rule.id());
            if (f == null) {
                out.add(TemplateProblem.error(file, at + "/id", unknown(kind, rule.id())));
                return;
            }
            try {
                f.create(rule.params());
            } catch (IllegalArgumentException e) {
                out.add(TemplateProblem.error(file, at + "/params", String.valueOf(e.getMessage())));
            }
        });
        return out;
    }
}
