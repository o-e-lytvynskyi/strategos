package dev.strategos.core.sim.econ;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.Hasher;
import dev.strategos.core.sim.nav.MaterialKind;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * A match's resource nodes (ECO-02, 04 §11.2): trees and forage plants found in its terrain at prepare. Node ids are 1
 * up, 0 none; a node keeps its kind, the resource and subtype it yields (indices into the match's {@link EconomyRules}),
 * the amount left in thousandths and the amount each block yields, where its work centres (a trunk's base), its blocks
 * top-down with how many are gone, and its work slots: the cells round its base, each claimed by a unit's handle or
 * free. A bush also regrows (ECO-05): so many thousandths a second, kept in a fixed-point remainder, up to the amount it
 * was found with, while it holds any; a field (ECO-09) keeps in the same remainder the ticks it has left to ripen. Not
 * commanded, so not entities (M3 spec §1.6 row 3).
 */
@CoreApi
public final class NodeStore {
    private static final long TICKS_PER_SECOND = 20;

    private int count;
    private boolean discovered;
    private MaterialKind[] kind = new MaterialKind[16];
    private int[] resource = new int[16];
    private int[] subtype = new int[16];
    private long[] amount = new long[16];
    private long[] perBlock = new long[16];
    private double[] anchorX = new double[16];
    private double[] anchorZ = new double[16];
    private int[] baseY = new int[16];
    private long[][] blocks = new long[16][];
    private int[] gone = new int[16];
    private int[][] slots = new int[16][];
    private int[][] claims = new int[16][];
    private long[] most = new long[16];
    private long[] regrow = new long[16];
    private int[] growth = new int[16];

    /** A position packed as nodes store it: match-local x and z, absolute y. */
    public static long pack(int x, int y, int z) {
        return ((long) x << 40) | ((long) (z & 0xFFFFF) << 20) | (y & 0xFFFFF);
    }

    public static int x(long packed) {
        return (int) (packed >>> 40);
    }

    public static int z(long packed) {
        return (int) ((packed >>> 20) & 0xFFFFF);
    }

    public static int y(long packed) {
        int y = (int) (packed & 0xFFFFF);
        return y >= 0x80000 ? y - 0x100000 : y; // the band may reach below 0
    }

    /** A work slot's cell packed: match-local x and z. */
    public static int cell(int x, int z) {
        return (x << 16) | z;
    }

    /** Adds a node; its id. {@code blocks} top-down; {@code slots} the cells round its base, in order. */
    public int add(
            MaterialKind kind,
            int resource,
            int subtype,
            long perBlock,
            long amount,
            double anchorX,
            double anchorZ,
            int baseY,
            long[] blocks,
            int[] slots) {
        if (count == this.kind.length) {
            int n = count * 2;
            this.kind = Arrays.copyOf(this.kind, n);
            this.resource = Arrays.copyOf(this.resource, n);
            this.subtype = Arrays.copyOf(this.subtype, n);
            this.amount = Arrays.copyOf(this.amount, n);
            this.perBlock = Arrays.copyOf(this.perBlock, n);
            this.anchorX = Arrays.copyOf(this.anchorX, n);
            this.anchorZ = Arrays.copyOf(this.anchorZ, n);
            this.baseY = Arrays.copyOf(this.baseY, n);
            this.blocks = Arrays.copyOf(this.blocks, n);
            this.gone = Arrays.copyOf(this.gone, n);
            this.slots = Arrays.copyOf(this.slots, n);
            this.claims = Arrays.copyOf(this.claims, n);
            this.most = Arrays.copyOf(this.most, n);
            this.regrow = Arrays.copyOf(this.regrow, n);
            this.growth = Arrays.copyOf(this.growth, n);
        }
        int k = count++;
        this.kind[k] = kind;
        this.resource[k] = resource;
        this.subtype[k] = subtype;
        this.perBlock[k] = perBlock;
        this.amount[k] = amount;
        this.anchorX[k] = anchorX;
        this.anchorZ[k] = anchorZ;
        this.baseY[k] = baseY;
        this.blocks[k] = blocks.clone();
        this.slots[k] = slots.clone();
        this.claims[k] = new int[slots.length];
        this.most[k] = amount;
        this.regrow[k] = 0;
        this.growth[k] = 0;
        return k + 1;
    }

    public int count() {
        return count;
    }

    /** Whether discovery ran: it runs once, when every page of the match space is first published. */
    public boolean discovered() {
        return discovered;
    }

    void markDiscovered() {
        discovered = true;
    }

    private int at(int id) {
        if (id < 1 || id > count) {
            throw new IndexOutOfBoundsException("node " + id + " of " + count);
        }
        return id - 1;
    }

    public MaterialKind kind(int id) {
        return kind[at(id)];
    }

    public int resource(int id) {
        return resource[at(id)];
    }

    public int subtype(int id) {
        return subtype[at(id)];
    }

    /** What is left, in thousandths. */
    public long amount(int id) {
        return amount[at(id)];
    }

    /** What one block yields, in thousandths: a log's share of its tree, a bush's whole amount. */
    public long perBlock(int id) {
        return perBlock[at(id)];
    }

    public double anchorX(int id) {
        return anchorX[at(id)];
    }

    public double anchorZ(int id) {
        return anchorZ[at(id)];
    }

    public int baseY(int id) {
        return baseY[at(id)];
    }

    /** The node's blocks, top-down, packed ({@link #pack}); the first {@link #gone} of them are gone. */
    public long[] blocks(int id) {
        return blocks[at(id)].clone();
    }

    public int gone(int id) {
        return gone[at(id)];
    }

    public int slotCount(int id) {
        return slots[at(id)].length;
    }

    /** Slot {@code slot}'s cell ({@link #cell}). */
    public int slot(int id, int slot) {
        return slots[at(id)][slot];
    }

    /** The handle holding slot {@code slot}, 0 when free. */
    public int claim(int id, int slot) {
        return claims[at(id)][slot];
    }

    /** The amount the node was found with, in thousandths: what a bush regrows up to. */
    public long most(int id) {
        return most[at(id)];
    }

    /** What the node regrows a second, in thousandths; 0 for a tree. */
    public long regrow(int id) {
        return regrow[at(id)];
    }

    /** Node {@code id} regrows {@code perSecond} thousandths a second (a bush, ECO-05). */
    public void setRegrow(int id, long perSecond) {
        regrow[at(id)] = Math.max(0, perSecond);
    }

    /**
     * One tick of regrowth: a node that holds some and less than it was found with gains its rate's share, the
     * remainder kept for the next tick; what it gained, in thousandths.
     */
    public long grow(int id) {
        int k = at(id);
        if (regrow[k] == 0 || amount[k] <= 0 || amount[k] >= most[k]) {
            growth[k] = 0;
            return 0;
        }
        long acc = growth[k] + regrow[k];
        long gained = Math.min(acc / TICKS_PER_SECOND, most[k] - amount[k]);
        growth[k] = (int) (acc - (acc / TICKS_PER_SECOND) * TICKS_PER_SECOND);
        amount[k] += gained;
        return gained;
    }

    /** Whether the node yields nothing more: empty, and not a field that ripens. */
    public boolean exhausted(int id) {
        int k = at(id);
        return amount[k] <= 0 && !(kind[k] == MaterialKind.CROP && growth[k] > 0);
    }

    /** A field's ticks left to ripen, 0 for a ripe or empty one or any other node (ECO-09). */
    public long ripening(int id) {
        int k = at(id);
        return kind[k] == MaterialKind.CROP ? growth[k] : 0;
    }

    /** Field node {@code id} ripens for {@code ticks} more. */
    public void setRipening(int id, long ticks) {
        growth[at(id)] = (int) Math.max(0, ticks);
    }

    /** A ripe field holds {@code value} thousandths again. */
    public void refill(int id, long value) {
        amount[at(id)] = Math.max(0, value);
    }

    /** The free slots. */
    public int freeSlots(int id) {
        int free = 0;
        for (int c : claims[at(id)]) {
            if (c == 0) {
                free++;
            }
        }
        return free;
    }

    /** {@code handle} takes the free slot nearest (x, z), the lowest on a tie; its index, or -1 when none is free. */
    public int claimNearest(int id, int handle, double x, double z) {
        return claimNearest(id, handle, x, z, cell -> true);
    }

    /**
     * {@code handle} takes the free slot nearest (x, z) whose cell {@code standable} accepts, the lowest on a tie; its
     * index, or -1 when none is free. A slot the handle holds already is kept.
     */
    public int claimNearest(int id, int handle, double x, double z, IntPredicate standable) {
        int k = at(id);
        int best = -1;
        double bestD = Double.POSITIVE_INFINITY;
        for (int s = 0; s < slots[k].length; s++) {
            if (claims[k][s] == handle) {
                return s; // it holds one already
            }
            if (claims[k][s] != 0 || !standable.test(slots[k][s])) {
                continue;
            }
            double dx = (slots[k][s] >>> 16) + 0.5 - x;
            double dz = (slots[k][s] & 0xFFFF) + 0.5 - z;
            double d = dx * dx + dz * dz;
            if (d < bestD) {
                bestD = d;
                best = s;
            }
        }
        if (best >= 0) {
            claims[k][best] = handle;
        }
        return best;
    }

    /** Whether node {@code id} has a free slot whose cell {@code standable} accepts, or one {@code handle} holds. */
    public boolean hasRoom(int id, int handle, IntPredicate standable) {
        int k = at(id);
        for (int s = 0; s < slots[k].length; s++) {
            if (claims[k][s] == handle || (claims[k][s] == 0 && standable.test(slots[k][s]))) {
                return true;
            }
        }
        return false;
    }

    /** The node whose slot {@code handle} holds, 0 for none: a restored unit's claim. */
    public int claimedBy(int handle) {
        for (int k = 0; k < count; k++) {
            for (int c : claims[k]) {
                if (c == handle) {
                    return k + 1;
                }
            }
        }
        return 0;
    }

    /** {@code handle} gives up any slot it holds on the node. */
    public void release(int id, int handle) {
        int[] c = claims[at(id)];
        for (int s = 0; s < c.length; s++) {
            if (c[s] == handle) {
                c[s] = 0;
            }
        }
    }

    /** Takes up to {@code want} thousandths from the node; what it gave. */
    public long take(int id, long want) {
        int k = at(id);
        long given = Math.max(0, Math.min(want, amount[k]));
        amount[k] -= given;
        if (amount[k] == 0) {
            growth[k] = 0;
        }
        return given;
    }

    /** The node one of whose standing blocks is at {@code packed}, or 0. */
    public int holding(long packed) {
        for (int k = 0; k < count; k++) {
            for (int i = gone[k]; i < blocks[k].length; i++) {
                if (blocks[k][i] == packed) {
                    return k + 1;
                }
            }
        }
        return 0;
    }

    /**
     * The node of {@code kind} (and, unless {@code resource} is -1, of that resource and subtype) with a standing block
     * nearest {@code packed}, within {@code reach} blocks; the lower id on a tie; 0 for none.
     */
    public int nearestBlock(long packed, MaterialKind kind, int resource, int subtype, double reach) {
        int x = x(packed);
        int y = y(packed);
        int z = z(packed);
        int best = 0;
        double bestD = reach * reach;
        for (int k = 0; k < count; k++) {
            if (this.kind[k] != kind
                    || (resource >= 0 && (this.resource[k] != resource || this.subtype[k] != subtype))) {
                continue;
            }
            for (int i = gone[k]; i < blocks[k].length; i++) {
                double dx = x(blocks[k][i]) - x;
                double dy = y(blocks[k][i]) - y;
                double dz = z(blocks[k][i]) - z;
                double d = dx * dx + dy * dy + dz * dz;
                if (d < bestD || (d == bestD && best == 0)) {
                    best = k + 1;
                    bestD = d;
                }
            }
        }
        return best;
    }

    /** The sum of what the nodes of {@code resource} hold, in thousandths. */
    public long remaining(int resource) {
        long sum = 0;
        for (int k = 0; k < count; k++) {
            if (this.resource[k] == resource) {
                sum += amount[k];
            }
        }
        return sum;
    }

    /**
     * Block {@code packed} of the node went without the core (ECO-02, 04 §11.4: a player broke it): it leaves the node
     * and takes its yield, credited to nobody: what it lost, in thousandths, or -1 if the node holds no such block still
     * standing.
     */
    public long lose(int id, long packed) {
        int k = at(id);
        for (int i = gone[k]; i < blocks[k].length; i++) {
            if (blocks[k][i] == packed) {
                long[] b = blocks[k];
                System.arraycopy(b, i + 1, b, i, b.length - i - 1);
                blocks[k] = Arrays.copyOf(b, b.length - 1);
                long lost = Math.min(amount[k], perBlock[k]);
                amount[k] -= lost;
                return lost;
            }
        }
        return -1;
    }

    /** The node's next block is gone: the top one first. */
    public void removeTop(int id) {
        int k = at(id);
        if (gone[k] < blocks[k].length) {
            gone[k]++;
        }
    }

    public long mix(long h) {
        long x = Hasher.mix(h, count);
        for (int k = 0; k < count; k++) {
            x = Hasher.mix(x, kind[k].code());
            x = Hasher.mix(x, resource[k]);
            x = Hasher.mix(x, subtype[k]);
            x = Hasher.mix(x, amount[k]);
            x = Hasher.mix(x, gone[k]);
            for (int c : claims[k]) {
                x = Hasher.mix(x, c);
            }
            if (regrow[k] != 0 || kind[k] == MaterialKind.CROP) { // only bushes and fields (ECO-05, -09) grow
                x = Hasher.mix(x, regrow[k]);
                x = Hasher.mix(x, growth[k]);
                x = Hasher.mix(x, most[k]);
            }
        }
        return x;
    }

    /** One node as a snapshot keeps it. */
    public record Saved(
            int kind,
            int resource,
            int subtype,
            long perBlock,
            long amount,
            double anchorX,
            double anchorZ,
            int baseY,
            List<Long> blocks,
            int gone,
            List<Integer> slots,
            List<Integer> claims,
            long most,
            long regrow,
            int growth) {
        public Saved {
            blocks = List.copyOf(blocks);
            slots = List.copyOf(slots);
            claims = List.copyOf(claims);
        }
    }

    public List<Saved> save() {
        List<Saved> out = new ArrayList<>(count);
        for (int k = 0; k < count; k++) {
            List<Long> b = new ArrayList<>(blocks[k].length);
            for (long v : blocks[k]) {
                b.add(v);
            }
            List<Integer> s = new ArrayList<>(slots[k].length);
            List<Integer> c = new ArrayList<>(slots[k].length);
            for (int i = 0; i < slots[k].length; i++) {
                s.add(slots[k][i]);
                c.add(claims[k][i]);
            }
            out.add(new Saved(
                    kind[k].code(),
                    resource[k],
                    subtype[k],
                    perBlock[k],
                    amount[k],
                    anchorX[k],
                    anchorZ[k],
                    baseY[k],
                    b,
                    gone[k],
                    s,
                    c,
                    most[k],
                    regrow[k],
                    growth[k]));
        }
        return out;
    }

    /** Restores a snapshot's nodes into this empty store, discovered. */
    public void restore(List<Saved> saved) {
        if (count != 0) {
            throw new IllegalStateException("restoring nodes into a store that holds " + count);
        }
        for (Saved n : saved) {
            long[] b = new long[n.blocks().size()];
            for (int i = 0; i < b.length; i++) {
                b[i] = n.blocks().get(i);
            }
            int[] s = new int[n.slots().size()];
            for (int i = 0; i < s.length; i++) {
                s[i] = n.slots().get(i);
            }
            int id = add(
                    MaterialKind.ofCode(n.kind()),
                    n.resource(),
                    n.subtype(),
                    n.perBlock(),
                    n.amount(),
                    n.anchorX(),
                    n.anchorZ(),
                    n.baseY(),
                    b,
                    s);
            int k = id - 1;
            gone[k] = n.gone();
            most[k] = n.most() > 0 ? n.most() : n.amount(); // a save from before regrowth: its amount
            regrow[k] = n.regrow();
            growth[k] = n.growth();
            for (int i = 0; i < s.length && i < n.claims().size(); i++) {
                claims[k][i] = n.claims().get(i);
            }
        }
        discovered = true;
    }
}
