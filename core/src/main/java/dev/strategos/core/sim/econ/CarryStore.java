package dev.strategos.core.sim.econ;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.Hasher;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * What each unit carries (ECO-05, 04 §11.4), by handle index: one resource and subtype at a time (indices into the
 * match's {@link EconomyRules}) and the amount in thousandths. A unit carries nothing until it gathers; a load of
 * another resource is dropped when it starts on a new one, as is the load of a unit that dies. The store also keeps the
 * node whose work slot each unit holds, and the building whose builder slot it holds, which the nodes' and the
 * buildings' claims say too: it is how a unit gives its slot up.
 */
@CoreApi
public final class CarryStore {
    /** No resource: an empty load. */
    public static final int NONE = -1;

    private int[] resource = new int[0];
    private int[] subtype = new int[0];
    private long[] amount = new long[0];
    private int[] claimed = new int[0];
    private int[] site = new int[0];
    private int carrying;

    /** One unit's load as a snapshot keeps it. */
    public record Saved(int handle, int resource, int subtype, long amount) {}

    public void ensureCapacity(int size) {
        if (size > resource.length) {
            int capacity = Math.max(size, resource.length * 2);
            int old = resource.length;
            resource = Arrays.copyOf(resource, capacity);
            subtype = Arrays.copyOf(subtype, capacity);
            amount = Arrays.copyOf(amount, capacity);
            claimed = Arrays.copyOf(claimed, capacity);
            site = Arrays.copyOf(site, capacity);
            Arrays.fill(resource, old, capacity, NONE);
        }
    }

    /** The resource unit {@code i} carries, {@link #NONE} for none. */
    public int resource(int i) {
        return i < resource.length ? resource[i] : NONE;
    }

    public int subtype(int i) {
        return i < subtype.length ? subtype[i] : 0;
    }

    /** What unit {@code i} carries, in thousandths. */
    public long amount(int i) {
        return i < amount.length ? amount[i] : 0;
    }

    /** The node whose work slot unit {@code i} holds, 0 for none. */
    public int claimed(int i) {
        return i < claimed.length ? claimed[i] : 0;
    }

    public void setClaimed(int i, int node) {
        claimed[i] = node;
    }

    /** The building whose builder slot unit {@code i} holds, 0 for none (BLD-03). */
    public int site(int i) {
        return i < site.length ? site[i] : 0;
    }

    public void setSite(int i, int building) {
        site[i] = building;
    }

    /** Whether any unit carries anything: only then does the store hash and save. */
    public boolean empty() {
        return carrying == 0;
    }

    /** Unit {@code i} adds {@code taken} thousandths of {@code resource} and {@code subtype} to the load it carries. */
    public void add(int i, int resource, int subtype, long taken) {
        if (this.resource[i] == NONE) {
            carrying++;
        } else if (this.resource[i] != resource || this.subtype[i] != subtype) {
            throw new IllegalStateException("unit " + i + " carries resource " + this.resource[i] + "."
                    + this.subtype[i] + " and was given " + resource + "." + subtype);
        }
        this.resource[i] = resource;
        this.subtype[i] = subtype;
        amount[i] += taken;
    }

    /** Unit {@code i} puts its load down: what it carried, in thousandths, its resource {@link #resource} before. */
    public long clear(int i) {
        if (i >= resource.length || resource[i] == NONE) {
            return 0;
        }
        long was = amount[i];
        resource[i] = NONE;
        subtype[i] = 0;
        amount[i] = 0;
        carrying--;
        return was;
    }

    /** The sum carried of {@code resource} by the units below {@code highWater}. */
    public long total(int resource, int highWater) {
        long sum = 0;
        for (int i = 0; i < Math.min(highWater, this.resource.length); i++) {
            if (this.resource[i] == resource) {
                sum += amount[i];
            }
        }
        return sum;
    }

    /** Mixes the loads of the units below {@code highWater}, in index order. */
    public long mix(long h, int highWater) {
        long x = Hasher.mix(h, carrying);
        for (int i = 0; i < Math.min(highWater, resource.length); i++) {
            if (resource[i] != NONE) {
                x = Hasher.mix(x, i);
                x = Hasher.mix(x, resource[i]);
                x = Hasher.mix(x, subtype[i]);
                x = Hasher.mix(x, amount[i]);
            }
        }
        return x;
    }

    /** The loads of the units below {@code highWater}, by index; {@code handles} gives each index's handle. */
    public List<Saved> save(int highWater, java.util.function.IntUnaryOperator handles) {
        List<Saved> out = new ArrayList<>();
        for (int i = 0; i < Math.min(highWater, resource.length); i++) {
            if (resource[i] != NONE) {
                out.add(new Saved(handles.applyAsInt(i), resource[i], subtype[i], amount[i]));
            }
        }
        return out;
    }

    /** A restored unit's load, at index {@code i}. */
    public void restore(int i, Saved saved) {
        ensureCapacity(i + 1);
        clear(i);
        add(i, saved.resource(), saved.subtype(), saved.amount());
    }
}
