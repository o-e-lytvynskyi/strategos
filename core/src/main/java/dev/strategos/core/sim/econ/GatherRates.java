package dev.strategos.core.sim.econ;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.sim.stats.StatService;

/**
 * How fast a unit gathers and how much it carries (ECO-05, 04 §11.4): {@code ResourceGatherer/rates/<resource>/<subtype>}
 * per second and {@code ResourceGatherer/capacities/<resource>}, read through the unit's modifiers like every stat and
 * held in thousandths; a path its template lacks, or a value below 0, is 0, so it gathers none of that. The path ids
 * are found again whenever the set changes.
 */
@CoreApi
public final class GatherRates {
    public static final String RATES = "ResourceGatherer/rates/";
    public static final String CAPACITIES = "ResourceGatherer/capacities/";

    private final EconomyRules rules;
    private int[][] rates = new int[0][];
    private int[] capacities = new int[0];

    public GatherRates(EconomyRules rules) {
        this.rules = rules;
    }

    /** Finds each resource's and subtype's paths in {@code stats}' set. */
    public void swap(StatService stats) {
        int n = rules.size();
        int[][] r = new int[n][];
        int[] c = new int[n];
        for (int k = 0; k < n; k++) {
            ResourceDef def = rules.resources().get(k);
            r[k] = new int[def.subtypes().size()];
            for (int s = 0; s < r[k].length; s++) {
                r[k][s] = stats.path(RATES + def.id() + "/" + def.subtypes().get(s));
            }
            c[k] = stats.path(CAPACITIES + def.id());
        }
        this.rates = r;
        this.capacities = c;
    }

    /** Unit {@code i}'s rate at resource {@code resource}'s subtype {@code subtype}, in thousandths a second. */
    public long rate(StatService stats, int i, int resource, int subtype) {
        int path = rates[resource][subtype];
        return path < 0 ? 0 : Math.max(0, Amount.of(stats.read(i, path)));
    }

    /** How much of {@code resource} unit {@code i} carries at most, in thousandths. */
    public long capacity(StatService stats, int i, int resource) {
        int path = capacities[resource];
        return path < 0 ? 0 : Math.max(0, Amount.of(stats.read(i, path)));
    }

    /** Whether unit {@code i} gathers {@code resource}'s {@code subtype} at all: a rate and a capacity above 0. */
    public boolean gathers(StatService stats, int i, int resource, int subtype) {
        return stats.canGather(i) && rate(stats, i, resource, subtype) > 0 && capacity(stats, i, resource) > 0;
    }
}
