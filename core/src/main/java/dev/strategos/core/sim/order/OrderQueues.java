package dev.strategos.core.sim.order;

import dev.strategos.core.shared.CoreApi;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Each unit's order queue (ORD-02, 04 §7.4, spec §5.9), by handle index: a ring of at most {@value #MAX} orders, each
 * a kind ({@link OrderKind}), a target on the ground plane, a group id (0 for an order given to one unit alone) and the
 * unit's slot in its group (0 until ORD-03's slots). The first is the current order, which the unit's machine carries
 * out; the others wait their turn. Replaces M0's single order, which is a queue of one.
 */
public final class OrderQueues {
    /** The most orders a unit holds. */
    public static final int MAX = 16;

    private int[] head = new int[0];
    private int[] count = new int[0];
    private byte[] kinds = new byte[0]; // per unit MAX entries, at i * MAX + its ring position
    private double[] xs = new double[0];
    private double[] zs = new double[0];
    private int[] groups = new int[0];
    private int[] slots = new int[0];

    /** One order as a save keeps it. */
    @CoreApi
    public record Order(int kind, double x, double z, int group, int slot) {}

    /**
     * The part of a unit's queue its save keeps beside the current order's target: that order's group and slot, and
     * the orders after it. {@link #NONE} for M0's single order.
     */
    @CoreApi
    public record Saved(int group, int slot, List<Order> next) {
        public static final Saved NONE = new Saved(0, 0, List.of());

        public Saved {
            next = List.copyOf(next);
            if (next.size() >= MAX) {
                throw new IllegalArgumentException(
                        next.size() + " orders after the current one, more than " + (MAX - 1));
            }
        }
    }

    public void ensureCapacity(int size) {
        if (size > head.length) {
            int capacity = Math.max(size, head.length * 2);
            head = Arrays.copyOf(head, capacity);
            count = Arrays.copyOf(count, capacity);
            kinds = Arrays.copyOf(kinds, capacity * MAX);
            xs = Arrays.copyOf(xs, capacity * MAX);
            zs = Arrays.copyOf(zs, capacity * MAX);
            groups = Arrays.copyOf(groups, capacity * MAX);
            slots = Arrays.copyOf(slots, capacity * MAX);
        }
    }

    /** How many orders unit {@code i} holds, the current one included. */
    public int size(int i) {
        return count[i];
    }

    /** The current order's kind, {@link OrderKind#NONE} when the queue is empty. */
    public int kind(int i) {
        return count[i] == 0 ? OrderKind.NONE : kinds[at(i, 0)];
    }

    public double targetX(int i) {
        return count[i] == 0 ? 0 : xs[at(i, 0)];
    }

    public double targetZ(int i) {
        return count[i] == 0 ? 0 : zs[at(i, 0)];
    }

    public int group(int i) {
        return count[i] == 0 ? 0 : groups[at(i, 0)];
    }

    public int slot(int i) {
        return count[i] == 0 ? 0 : slots[at(i, 0)];
    }

    /** The {@code k}th order of unit {@code i}, 0 being the current one. */
    public Order order(int i, int k) {
        if (k < 0 || k >= count[i]) {
            throw new IndexOutOfBoundsException("order " + k + " of a queue of " + count[i]);
        }
        int at = at(i, k);
        return new Order(kinds[at], xs[at], zs[at], groups[at], slots[at]);
    }

    /** The current order finishes and the queue is cleared; the new order is the only one. */
    public void replace(int i, int kind, double x, double z, int group, int slot) {
        clear(i);
        put(i, 0, kind, x, z, group, slot);
        count[i] = 1;
    }

    /** A single move to (x, z), alone: the arrival latch's walk back and M0's orders. */
    public void replaceMove(int i, double x, double z) {
        replace(i, OrderKind.MOVE, x, z, 0, 0);
    }

    /** Appends an order; false, and nothing changes, when the unit already holds {@value #MAX}. */
    public boolean queue(int i, int kind, double x, double z, int group, int slot) {
        if (count[i] == MAX) {
            return false;
        }
        put(i, count[i], kind, x, z, group, slot);
        count[i]++;
        return true;
    }

    /**
     * Puts an order before the current one, which resumes after it; false, and nothing changes, when the unit already
     * holds {@value #MAX}.
     */
    public boolean pushFront(int i, int kind, double x, double z, int group, int slot) {
        if (count[i] == MAX) {
            return false;
        }
        head[i] = (head[i] + MAX - 1) % MAX;
        count[i]++;
        put(i, 0, kind, x, z, group, slot);
        return true;
    }

    /** Drops the current order: the next, if any, becomes current. */
    public void pop(int i) {
        if (count[i] == 0) {
            return;
        }
        put(i, 0, OrderKind.NONE, 0, 0, 0, 0);
        head[i] = (head[i] + 1) % MAX;
        count[i]--;
        if (count[i] == 0) {
            head[i] = 0;
        }
    }

    /** Every order goes. */
    public void clear(int i) {
        for (int k = 0; k < count[i]; k++) {
            put(i, k, OrderKind.NONE, 0, 0, 0, 0);
        }
        head[i] = 0;
        count[i] = 0;
    }

    /** What a save keeps of unit {@code i}'s queue besides the current order's kind and target. */
    public Saved saved(int i) {
        if (count[i] <= 1 && group(i) == 0 && slot(i) == 0) {
            return Saved.NONE;
        }
        List<Order> next = new ArrayList<>(count[i]);
        for (int k = 1; k < count[i]; k++) {
            next.add(order(i, k));
        }
        return new Saved(group(i), slot(i), next);
    }

    /** Unit {@code i}'s queue as saved: the current move to (x, z), if any, then the saved orders after it. */
    public void restore(int i, int kind, double x, double z, Saved saved) {
        clear(i);
        if (kind == OrderKind.NONE) {
            return;
        }
        replace(i, kind, x, z, saved.group(), saved.slot());
        for (Order o : saved.next()) {
            queue(i, o.kind(), o.x(), o.z(), o.group(), o.slot());
        }
    }

    private void put(int i, int k, int kind, double x, double z, int group, int slot) {
        int at = at(i, k);
        kinds[at] = (byte) kind;
        xs[at] = x;
        zs[at] = z;
        groups[at] = group;
        slots[at] = slot;
    }

    private int at(int i, int k) {
        return i * MAX + (head[i] + k) % MAX;
    }
}
