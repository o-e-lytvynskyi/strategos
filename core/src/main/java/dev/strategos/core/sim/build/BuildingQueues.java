package dev.strategos.core.sim.build;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.Hasher;
import dev.strategos.core.shared.QueueEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;
import org.jspecify.annotations.Nullable;

/**
 * The production queue and the rally point of every building of a {@link BuildingStore} (PROD-01 to -03), which owns
 * it and reaches it as {@link BuildingStore#queues()}. The methods that take a handle name a live building; the
 * package-private ones take its slot, for the store's own create, grow, hash, save and restore.
 */
@CoreApi
public final class BuildingQueues {
    private final BuildingStore owner;
    private int[][] queue =
            new int[8][]; // per building its entries (a unit's template index, or a tech: -1 - index), head first
    private int[][] batch = new int[8][]; // per entry the units it trains at once (PROD-02), 1 for a single one
    private long[] trained = new long[8]; // the head's ticks of training
    private boolean[] reserved = new boolean[8]; // whether the head holds its population
    private boolean[] waiting = new boolean[8]; // whether the head waits for housing, told once
    private int[] rallyKind = new int[8];
    private double[] rallyX = new double[8];
    private double[] rallyZ = new double[8];
    private int[] rallyTarget = new int[8];

    BuildingQueues(BuildingStore owner) {
        this.owner = owner;
    }

    void grow(int n) {
        queue = Arrays.copyOf(queue, n);
        batch = Arrays.copyOf(batch, n);
        trained = Arrays.copyOf(trained, n);
        reserved = Arrays.copyOf(reserved, n);
        waiting = Arrays.copyOf(waiting, n);
        rallyKind = Arrays.copyOf(rallyKind, n);
        rallyX = Arrays.copyOf(rallyX, n);
        rallyZ = Arrays.copyOf(rallyZ, n);
        rallyTarget = Arrays.copyOf(rallyTarget, n);
    }

    /** Slot {@code k} holds a new building: an empty queue and no rally. */
    void reset(int k) {
        queue[k] = new int[0];
        batch[k] = new int[0];
        trained[k] = 0;
        reserved[k] = false;
        waiting[k] = false;
        rallyKind[k] = BuildingStore.RALLY_NONE;
        rallyX[k] = 0;
        rallyZ[k] = 0;
        rallyTarget[k] = 0;
    }

    /** Building {@code handle}'s queue: its entries, the head first; a tech entry is {@link QueueEntry#isTech}. */
    public int[] queue(int handle) {
        return queue[owner.at(handle)].clone();
    }

    public int queueSize(int handle) {
        return queue[owner.at(handle)].length;
    }

    /** Adds an entry of {@code template} at the queue's end; false when it is full. */
    public boolean enqueue(int handle, int template) {
        return enqueue(handle, template, 1);
    }

    /** Adds an entry of {@code count} units of {@code template} at the queue's end (PROD-02); false when it is full. */
    public boolean enqueue(int handle, int template, int count) {
        int k = owner.at(handle);
        if (queue[k].length >= BuildingStore.QUEUE_MAX) {
            return false;
        }
        queue[k] = Arrays.copyOf(queue[k], queue[k].length + 1);
        queue[k][queue[k].length - 1] = template;
        batch[k] = Arrays.copyOf(batch[k], queue[k].length);
        batch[k][queue[k].length - 1] = Math.max(1, count);
        return true;
    }

    /**
     * Puts an entry of one at the queue's head, the others behind it (BLD-12); false when it is full. Whatever the old
     * head had trained, held or waited for starts afresh: the caller gives back what it held.
     */
    public boolean enqueueHead(int handle, int entry) {
        int k = owner.at(handle);
        if (queue[k].length >= BuildingStore.QUEUE_MAX) {
            return false;
        }
        int[] q = new int[queue[k].length + 1];
        int[] counts = new int[q.length];
        q[0] = entry;
        counts[0] = 1;
        System.arraycopy(queue[k], 0, q, 1, queue[k].length);
        for (int e = 0; e < queue[k].length; e++) {
            counts[e + 1] = batch(handle, e);
        }
        queue[k] = q;
        batch[k] = counts;
        trained[k] = 0;
        reserved[k] = false;
        waiting[k] = false;
        return true;
    }

    /** Every entry's batch count, the head first (PROD-02). */
    public List<Integer> batches(int handle) {
        int k = owner.at(handle);
        List<Integer> out = new ArrayList<>(queue[k].length);
        for (int e = 0; e < queue[k].length; e++) {
            out.add(batch(handle, e));
        }
        return out;
    }

    /** How many units entry {@code index} trains at once (PROD-02): 1 for a single one. */
    public int batch(int handle, int index) {
        int[] b = batch[owner.at(handle)];
        return index < b.length ? Math.max(1, b[index]) : 1;
    }

    /** Takes entry {@code index} out of the queue; its template. The head's training starts afresh. */
    public int dequeue(int handle, int index) {
        int k = owner.at(handle);
        int[] q = queue[k];
        int template = q[index];
        int[] out = new int[q.length - 1];
        System.arraycopy(q, 0, out, 0, index);
        System.arraycopy(q, index + 1, out, index, q.length - index - 1);
        queue[k] = out;
        int[] counts = Arrays.copyOf(batch[k], q.length);
        int[] left = new int[out.length];
        System.arraycopy(counts, 0, left, 0, index);
        System.arraycopy(counts, index + 1, left, index, q.length - index - 1);
        batch[k] = left;
        if (index == 0) {
            trained[k] = 0;
            reserved[k] = false;
            waiting[k] = false;
        }
        return template;
    }

    /** The head's ticks of training. */
    public long trained(int handle) {
        return trained[owner.at(handle)];
    }

    public void setTrained(int handle, long ticks) {
        trained[owner.at(handle)] = ticks;
    }

    /** Whether the head holds its population. */
    public boolean reserved(int handle) {
        return reserved[owner.at(handle)];
    }

    public void setReserved(int handle, boolean value) {
        reserved[owner.at(handle)] = value;
    }

    /** Whether the head waits for housing, so the notice is told once. */
    public boolean waiting(int handle) {
        return waiting[owner.at(handle)];
    }

    public void setWaiting(int handle, boolean value) {
        waiting[owner.at(handle)] = value;
    }

    public int rallyKind(int handle) {
        return rallyKind[owner.at(handle)];
    }

    public double rallyX(int handle) {
        return rallyX[owner.at(handle)];
    }

    public double rallyZ(int handle) {
        return rallyZ[owner.at(handle)];
    }

    /** The rally's node id or building handle, 0 for a point. */
    public int rallyTarget(int handle) {
        return rallyTarget[owner.at(handle)];
    }

    /** Building {@code handle}'s rally point: its kind, the point, and the node or building it names. */
    public void setRally(int handle, int kind, double x, double z, int target) {
        int k = owner.at(handle);
        rallyKind[k] = kind;
        rallyX[k] = x;
        rallyZ[k] = z;
        rallyTarget[k] = target;
    }

    /** Folds slot {@code k} into the hash, only once it trains or rallies (PROD-01, -03). */
    long mix(long x, int k) {
        if (queue[k].length == 0 && rallyKind[k] == BuildingStore.RALLY_NONE) {
            return x;
        }
        x = Hasher.mix(x, queue[k].length);
        for (int t : queue[k]) {
            x = Hasher.mix(x, t);
        }
        for (int c : batch[k]) {
            if (c > 1) { // only batches (PROD-02), so single entries hash as before
                x = Hasher.mix(x, c);
            }
        }
        x = Hasher.mix(x, trained[k]);
        x = Hasher.mix(x, reserved[k] ? 1 : 0);
        x = Hasher.mix(x, waiting[k] ? 1 : 0);
        x = Hasher.mix(x, rallyKind[k]);
        x = Hasher.mix(x, Double.doubleToLongBits(rallyX[k]));
        x = Hasher.mix(x, Double.doubleToLongBits(rallyZ[k]));
        x = Hasher.mix(x, rallyTarget[k]);
        return x;
    }

    private boolean batched(int k) {
        for (int c : batch[k] == null ? new int[0] : batch[k]) {
            if (c > 1) {
                return true;
            }
        }
        return false;
    }

    /** Slot {@code k}'s queue as a snapshot keeps it; {@code techId} names a tech by net index. */
    BuildingStore.Production production(int k, IntFunction<@Nullable String> techId, boolean autoReseed) {
        List<Integer> entries = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        List<String> techs = new ArrayList<>();
        int[] q = queue[k] == null ? new int[0] : queue[k];
        for (int e = 0; e < q.length; e++) {
            int entry = q[e];
            if (QueueEntry.isTech(entry)) {
                String id = techId.apply(QueueEntry.techIndex(entry));
                if (id == null) {
                    continue;
                }
                entry = QueueEntry.techEntry(techs.size()); // -1 - the id's place in the list
                techs.add(id);
            }
            entries.add(entry);
            counts.add(batch[k] != null && e < batch[k].length ? Math.max(1, batch[k][e]) : 1);
        }
        return new BuildingStore.Production(
                entries,
                trained[k],
                reserved[k],
                waiting[k],
                batched(k) ? counts : List.of(), // single entries save as before batches
                autoReseed,
                techs);
    }

    /** Slot {@code k}'s rally point as a snapshot keeps it. */
    BuildingStore.Rally rally(int k) {
        return new BuildingStore.Rally(rallyKind[k], rallyX[k], rallyZ[k], rallyTarget[k]);
    }

    /** Restores slot {@code k}'s queue and rally from a snapshot's. */
    void restore(int k, BuildingStore.Saved s, ToIntFunction<String> techIndex) {
        List<Integer> savedQueue = s.production().queue();
        int[] entries = new int[savedQueue.size()];
        int[] counts = new int[entries.length];
        int kept = 0;
        boolean headDropped = false;
        for (int e = 0; e < entries.length; e++) {
            int entry = savedQueue.get(e);
            if (QueueEntry.isTech(entry)) {
                int place = QueueEntry.techIndex(entry);
                int index = place < s.production().techs().size()
                        ? techIndex.applyAsInt(s.production().techs().get(place))
                        : -1;
                if (index < 0) {
                    headDropped |= e == 0;
                    continue;
                }
                entry = QueueEntry.techEntry(index);
            }
            entries[kept] = entry;
            counts[kept] = e < s.production().batches().size()
                    ? Math.max(1, s.production().batches().get(e))
                    : 1;
            kept++;
        }
        queue[k] = Arrays.copyOf(entries, kept);
        batch[k] = Arrays.copyOf(counts, kept);
        trained[k] = headDropped ? 0 : s.production().trained(); // the next entry starts afresh
        reserved[k] = s.production().reserved();
        waiting[k] = s.production().waiting();
        rallyKind[k] = s.rally().kind();
        rallyX[k] = s.rally().x();
        rallyZ[k] = s.rally().z();
        rallyTarget[k] = s.rally().target();
    }
}
