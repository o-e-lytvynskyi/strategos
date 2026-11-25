package dev.strategos.core.sim.econ;

import dev.strategos.core.content.TechDef;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.sim.stats.StatService;

/**
 * What a template costs a faction (ECO-01): each resource's {@code Cost/resources/<id>} through the faction's
 * modifiers, in thousandths, 0 where the template names none. The path ids are found again whenever the set changes.
 */
@CoreApi
public final class Costs {
    public static final String PREFIX = "Cost/resources/";

    /** What a kill of a unit or building of the template pays its killer's faction, per resource (CMB-13). */
    public static final String LOOT_PREFIX = "Loot/resources/";

    private final EconomyRules rules;
    private int[] paths = new int[0];
    private int[] upgradePaths = new int[0];
    private int[] lootPaths = new int[0];

    public Costs(EconomyRules rules) {
        this.rules = rules;
    }

    /** Finds each resource's cost path in {@code stats}' set. */
    public void swap(StatService stats) {
        int[] p = new int[rules.size()];
        for (int r = 0; r < p.length; r++) {
            p[r] = stats.path(PREFIX + rules.resources().get(r).id());
        }
        this.paths = p;
        int[] u = new int[rules.size()];
        for (int r = 0; r < u.length; r++) {
            u[r] = stats.path(dev.strategos.core.content.CorePaths.UPGRADE_COST_PREFIX
                    + rules.resources().get(r).id());
        }
        this.upgradePaths = u;
        int[] l = new int[rules.size()];
        for (int r = 0; r < l.length; r++) {
            l[r] = stats.path(LOOT_PREFIX + rules.resources().get(r).id());
        }
        this.lootPaths = l;
    }

    /** The value path of resource {@code r}'s {@code Loot/resources/<id>} in the swapped-in set, or -1 (CMB-13). */
    public int lootPath(int r) {
        return r < lootPaths.length ? lootPaths[r] : -1;
    }

    /** What template {@code template}'s upgrade costs {@code faction} (BLD-12), per resource in thousandths. */
    public long[] ofUpgrade(StatService stats, int template, int faction) {
        long[] cost = new long[upgradePaths.length];
        for (int r = 0; r < cost.length; r++) {
            if (upgradePaths[r] >= 0) {
                double v = stats.blockValue(template, faction, upgradePaths[r]);
                cost[r] = Double.isFinite(v) ? Math.max(0, Amount.of(v)) : 0;
            }
        }
        return cost;
    }

    /** Technology {@code tech}'s cost, per resource in thousandths: its {@code cost} by resource id; no modifier reaches it. */
    public long[] ofTech(TechDef tech) {
        long[] cost = new long[rules.size()];
        for (int r = 0; r < cost.length; r++) {
            Double units = tech.cost().get(rules.resources().get(r).id());
            cost[r] = units == null ? 0 : Math.max(0, Amount.of(units));
        }
        return cost;
    }

    /** Template {@code template}'s cost for {@code faction}, per resource in thousandths. */
    public long[] of(StatService stats, int template, int faction) {
        long[] cost = new long[paths.length];
        for (int r = 0; r < paths.length; r++) {
            if (paths[r] >= 0) {
                cost[r] = Math.max(0, Amount.of(stats.blockValue(template, faction, paths[r])));
            }
        }
        return cost;
    }
}
