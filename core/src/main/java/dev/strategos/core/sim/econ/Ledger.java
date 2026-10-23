package dev.strategos.core.sim.econ;

import dev.strategos.core.shared.CoreApi;
import java.util.Arrays;
import org.jspecify.annotations.Nullable;

/**
 * Where a match's gathered resources come from and go (ECO-05), per resource in thousandths: what discovery found in
 * the nodes, what bushes regrew, what nodes lost to someone else's edits, what gatherers took from nodes, and what
 * carried loads were dropped. Two balances hold every step, which the core checks in FINAL as it checks the treasury's:
 * found + regrown = node remainders + lost + taken, and taken = carried + gathered + dropped, the gathered summed over
 * the factions' treasuries.
 */
@CoreApi
public final class Ledger {
    private static final int FOUND = 0;
    private static final int REGROWN = 1;
    private static final int LOST = 2;
    private static final int TAKEN = 3;
    private static final int DROPPED = 4;
    private static final int COUNTERS = 5;

    private final int resources;
    private final long[] counters;

    public Ledger(int resources) {
        this.resources = resources;
        this.counters = new long[resources * COUNTERS];
    }

    public void found(int resource, long amount) {
        counters[resource * COUNTERS + FOUND] += amount;
    }

    public void regrown(int resource, long amount) {
        counters[resource * COUNTERS + REGROWN] += amount;
    }

    public void lost(int resource, long amount) {
        counters[resource * COUNTERS + LOST] += amount;
    }

    public void taken(int resource, long amount) {
        counters[resource * COUNTERS + TAKEN] += amount;
    }

    public void dropped(int resource, long amount) {
        counters[resource * COUNTERS + DROPPED] += amount;
    }

    public long found(int resource) {
        return counters[resource * COUNTERS + FOUND];
    }

    public long taken(int resource) {
        return counters[resource * COUNTERS + TAKEN];
    }

    public long dropped(int resource) {
        return counters[resource * COUNTERS + DROPPED];
    }

    /** Whether nothing was ever found or taken: such a ledger saves nothing. */
    public boolean empty() {
        for (long c : counters) {
            if (c != 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * The first balance that does not hold for {@code resource}, or null: given the nodes' remainders, the loads carried
     * and the factions' gathered, all of that resource.
     */
    public @Nullable String broken(int resource, long remainders, long carried, long gathered) {
        int k = resource * COUNTERS;
        long in = counters[k + FOUND] + counters[k + REGROWN];
        long out = remainders + counters[k + LOST] + counters[k + TAKEN];
        if (in != out) {
            return "resource " + resource + ": found " + counters[k + FOUND] + " + regrown " + counters[k + REGROWN]
                    + " != left in nodes " + remainders + " + lost " + counters[k + LOST] + " + taken "
                    + counters[k + TAKEN];
        }
        if (counters[k + TAKEN] != carried + gathered + counters[k + DROPPED]) {
            return "resource " + resource + ": taken " + counters[k + TAKEN] + " != carried " + carried + " + gathered "
                    + gathered + " + dropped " + counters[k + DROPPED];
        }
        return null;
    }

    public int resources() {
        return resources;
    }

    public long[] save() {
        return counters.clone();
    }

    /** As saved; a ledger saved for another number of resources is refused. */
    public void restore(long[] saved) {
        if (saved.length != counters.length) {
            throw new IllegalArgumentException(
                    "a saved ledger of " + saved.length + " counters for " + resources + " resources");
        }
        System.arraycopy(saved, 0, counters, 0, counters.length);
    }

    /** Clears every counter. */
    public void reset() {
        Arrays.fill(counters, 0);
    }
}
