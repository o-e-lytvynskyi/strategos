package dev.strategos.core.sim.econ;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.Hasher;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Every faction's treasury (ECO-01, 04 §11.1): for each resource, in thousandths, the stock and four counters: granted
 * (the starting resources and an administrator's grants), gathered, spent and refunded. Its invariant, checked after
 * every step ({@link #broken}), is granted + gathered + refunded = stock + spent; a stock never goes below 0, since a
 * payment either covers every resource or changes nothing. Carried loads and node remainders join the check with
 * gathering (ECO-05).
 */
@CoreApi
public final class Treasury {
    private final int factions;
    private final int resources;
    private final long[] stock;
    private final long[] granted;
    private final long[] gathered;
    private final long[] spent;
    private final long[] refunded;

    public Treasury(int factions, int resources) {
        if (factions < 1 || resources < 0) {
            throw new IllegalArgumentException(
                    "a treasury of " + factions + " factions and " + resources + " resources");
        }
        this.factions = factions;
        this.resources = resources;
        int n = factions * resources;
        this.stock = new long[n];
        this.granted = new long[n];
        this.gathered = new long[n];
        this.spent = new long[n];
        this.refunded = new long[n];
    }

    public int factions() {
        return factions;
    }

    public int resources() {
        return resources;
    }

    private int at(int faction, int resource) {
        if (faction < 0 || faction >= factions || resource < 0 || resource >= resources) {
            throw new IndexOutOfBoundsException("faction " + faction + ", resource " + resource);
        }
        return faction * resources + resource;
    }

    public long stock(int faction, int resource) {
        return stock[at(faction, resource)];
    }

    public long granted(int faction, int resource) {
        return granted[at(faction, resource)];
    }

    public long gathered(int faction, int resource) {
        return gathered[at(faction, resource)];
    }

    public long spent(int faction, int resource) {
        return spent[at(faction, resource)];
    }

    public long refunded(int faction, int resource) {
        return refunded[at(faction, resource)];
    }

    /** A copy of {@code faction}'s counters. */
    public FactionTreasury view(int faction) {
        List<Long> s = new ArrayList<>(resources);
        List<Long> g = new ArrayList<>(resources);
        List<Long> ga = new ArrayList<>(resources);
        List<Long> sp = new ArrayList<>(resources);
        List<Long> re = new ArrayList<>(resources);
        for (int r = 0; r < resources; r++) {
            int k = at(faction, r);
            s.add(stock[k]);
            g.add(granted[k]);
            ga.add(gathered[k]);
            sp.add(spent[k]);
            re.add(refunded[k]);
        }
        return new FactionTreasury(s, g, ga, sp, re);
    }

    /** What {@code faction} lacks to pay {@code cost} (per resource, in thousandths), or null when it can pay. */
    public long @Nullable [] missing(int faction, long[] cost) {
        long[] lack = null;
        for (int r = 0; r < resources; r++) {
            long need = r < cost.length ? cost[r] : 0;
            long lacking = need - stock[at(faction, r)];
            if (lacking > 0) {
                if (lack == null) {
                    lack = new long[resources];
                }
                lack[r] = lacking;
            }
        }
        return lack;
    }

    /** Pays {@code cost} from {@code faction}'s stock if it covers every resource; false and no change otherwise. */
    public boolean pay(int faction, long[] cost) {
        checkAmounts(cost);
        if (missing(faction, cost) != null) {
            return false;
        }
        for (int r = 0; r < Math.min(resources, cost.length); r++) {
            int k = at(faction, r);
            stock[k] -= cost[r];
            spent[k] += cost[r];
        }
        return true;
    }

    /** Gives back {@code amount}, as a cancelled entry's cost (PROD-01). */
    public void refund(int faction, long[] amount) {
        checkAmounts(amount);
        for (int r = 0; r < Math.min(resources, amount.length); r++) {
            int k = at(faction, r);
            stock[k] += amount[r];
            refunded[k] += amount[r];
        }
    }

    /** A delivered load (ECO-05). */
    public void gather(int faction, int resource, long amount) {
        check(amount);
        int k = at(faction, resource);
        stock[k] += amount;
        gathered[k] += amount;
    }

    /** The starting resources, or an administrator's grant. */
    public void grant(int faction, int resource, long amount) {
        check(amount);
        int k = at(faction, resource);
        stock[k] += amount;
        granted[k] += amount;
    }

    /** Whether nothing was ever granted, gathered, spent or refunded: such a treasury hashes and saves as none. */
    public boolean empty() {
        for (int k = 0; k < stock.length; k++) {
            if (stock[k] != 0 || granted[k] != 0 || gathered[k] != 0 || spent[k] != 0 || refunded[k] != 0) {
                return false;
            }
        }
        return true;
    }

    /** The first broken invariant, or null: granted + gathered + refunded = stock + spent, and no stock below 0. */
    public @Nullable String broken() {
        for (int f = 0; f < factions; f++) {
            for (int r = 0; r < resources; r++) {
                int k = at(f, r);
                if (stock[k] < 0) {
                    return "faction " + f + " resource " + r + ": stock " + stock[k] + " below 0";
                }
                if (granted[k] + gathered[k] + refunded[k] != stock[k] + spent[k]) {
                    return "faction " + f + " resource " + r + ": granted " + granted[k] + " + gathered " + gathered[k]
                            + " + refunded " + refunded[k] + " != stock " + stock[k] + " + spent " + spent[k];
                }
            }
        }
        return null;
    }

    /** Mixes every counter into {@code h}, faction by faction and resource by resource. */
    public long mix(long h) {
        long x = h;
        for (int k = 0; k < stock.length; k++) {
            x = Hasher.mix(x, stock[k]);
            x = Hasher.mix(x, granted[k]);
            x = Hasher.mix(x, gathered[k]);
            x = Hasher.mix(x, spent[k]);
            x = Hasher.mix(x, refunded[k]);
        }
        return x;
    }

    /** The five counters of every faction and resource, for a snapshot: stock, granted, gathered, spent, refunded. */
    public long[] save() {
        long[] out = new long[stock.length * 5];
        for (int k = 0; k < stock.length; k++) {
            out[k * 5] = stock[k];
            out[k * 5 + 1] = granted[k];
            out[k * 5 + 2] = gathered[k];
            out[k * 5 + 3] = spent[k];
            out[k * 5 + 4] = refunded[k];
        }
        return out;
    }

    /** Sets every counter from {@link #save}'s form; it must have this treasury's size. */
    public void restore(long[] saved) {
        if (saved.length != stock.length * 5) {
            throw new IllegalArgumentException(
                    "a saved treasury of " + saved.length + " counters, not " + stock.length * 5);
        }
        for (int k = 0; k < stock.length; k++) {
            stock[k] = saved[k * 5];
            granted[k] = saved[k * 5 + 1];
            gathered[k] = saved[k * 5 + 2];
            spent[k] = saved[k * 5 + 3];
            refunded[k] = saved[k * 5 + 4];
        }
    }

    private static void check(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("a negative amount " + amount);
        }
    }

    private static void checkAmounts(long[] amounts) {
        for (long a : amounts) {
            check(a);
        }
    }
}
