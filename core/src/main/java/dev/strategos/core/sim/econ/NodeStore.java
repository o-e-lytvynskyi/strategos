package dev.strategos.core.sim.econ;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.Hasher;
import dev.strategos.core.sim.nav.MaterialKind;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A match's resource nodes (ECO-02, 04 §11.2): trees and forage plants found in its terrain at prepare. Node ids are 1
 * up, 0 none; a node keeps its kind, the resource and subtype it yields (indices into the match's {@link EconomyRules}),
 * the amount left in thousandths and the amount each block yields, where its work centres (a trunk's base), its blocks
 * top-down with how many are gone, and its work slots: the cells round its base, each claimed by a unit's handle or
 * free. Not commanded, so not entities (M3 spec §1.6 row 3).
 */
@CoreApi
public final class NodeStore {
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

    /** Whether the node yields nothing more. */
    public boolean exhausted(int id) {
        return amount[at(id)] <= 0;
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
        int k = at(id);
        int best = -1;
        double bestD = Double.POSITIVE_INFINITY;
        for (int s = 0; s < slots[k].length; s++) {
            if (claims[k][s] == handle) {
                return s; // it holds one already
            }
            if (claims[k][s] != 0) {
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
        return given;
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
            List<Integer> claims) {
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
                    c));
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
            for (int i = 0; i < s.length && i < n.claims().size(); i++) {
                claims[k][i] = n.claims().get(i);
            }
        }
        discovered = true;
    }
}
