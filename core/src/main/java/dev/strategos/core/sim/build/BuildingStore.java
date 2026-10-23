package dev.strategos.core.sim.build;

import dev.strategos.core.content.Footprint;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.Hasher;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A match's buildings (BLD-01): each a template (a building's), an owning faction, where it stands ({@link Footprint}:
 * anchor and rotation, and the height its blueprint's lowest layer takes), its state, its construction progress in
 * thousandths, and the damage it has taken. Buildings are not units, so they keep a store of their own, but their
 * handles share the units' form: their slot indices start at {@link #BASE}, above any unit's, so a command, a query or
 * a selection names either with one {@code int} (M3 spec §1.6 row 3, as built).
 */
@CoreApi
public final class BuildingStore {
    /** The first slot index of a building's handle; units' indices stay below it. */
    public static final int BASE = 1 << 19;

    /** A building placed and not yet begun (BLD-02, BLD-03). */
    public static final int FOUNDATION = 0;

    /** A building whose builders are raising it (BLD-03). */
    public static final int CONSTRUCTING = 1;

    /** A finished building. */
    public static final int COMPLETE = 2;

    /** Progress of a finished building, in thousandths. */
    public static final long FULL = 1_000;

    private int high;
    private int[] generation = new int[8];
    private boolean[] alive = new boolean[8];
    private int[] template = new int[8];
    private int[] faction = new int[8];
    private int[] anchorX = new int[8];
    private int[] anchorZ = new int[8];
    private int[] rotation = new int[8];
    private int[] baseY = new int[8];
    private int[] state = new int[8];
    private long[] progress = new long[8];
    private double[] damage = new double[8];
    private long[] work = new long[8];
    private int[] placed = new int[8];
    private int[][] slots = new int[8][];
    private int[][] claims = new int[8][];
    // a step's scratch, neither hashed nor saved: the builders at work this tick and their rates
    private int[] tickBuilders = new int[8];
    private double[] tickRates = new double[8];

    /** Whether {@code handle} names a building's slot, alive or not. */
    public static boolean isBuilding(int handle) {
        return handle != Handle.NONE && Handle.index(handle) >= BASE;
    }

    /** A new building; its handle. A freed slot is reused with a new generation, the lowest first. */
    public int create(int template, int faction, Footprint at, int baseY, int state, long progress) {
        int k = 0;
        while (k < high && alive[k]) {
            k++;
        }
        if (k == high) {
            if (high == generation.length) {
                grow(high * 2);
            }
            high++;
        }
        generation[k] = Handle.nextGeneration(generation[k]);
        alive[k] = true;
        this.template[k] = template;
        this.faction[k] = faction;
        this.anchorX[k] = at.anchorX();
        this.anchorZ[k] = at.anchorZ();
        this.rotation[k] = at.rotation();
        this.baseY[k] = baseY;
        this.state[k] = state;
        this.progress[k] = progress;
        this.damage[k] = 0;
        this.work[k] = 0;
        this.placed[k] = 0;
        this.slots[k] = new int[0];
        this.claims[k] = new int[0];
        return Handle.of(BASE + k, generation[k]);
    }

    private void grow(int n) {
        generation = Arrays.copyOf(generation, n);
        alive = Arrays.copyOf(alive, n);
        template = Arrays.copyOf(template, n);
        faction = Arrays.copyOf(faction, n);
        anchorX = Arrays.copyOf(anchorX, n);
        anchorZ = Arrays.copyOf(anchorZ, n);
        rotation = Arrays.copyOf(rotation, n);
        baseY = Arrays.copyOf(baseY, n);
        state = Arrays.copyOf(state, n);
        progress = Arrays.copyOf(progress, n);
        damage = Arrays.copyOf(damage, n);
        work = Arrays.copyOf(work, n);
        placed = Arrays.copyOf(placed, n);
        slots = Arrays.copyOf(slots, n);
        claims = Arrays.copyOf(claims, n);
        tickBuilders = Arrays.copyOf(tickBuilders, n);
        tickRates = Arrays.copyOf(tickRates, n);
    }

    /** A builder at work on building {@code handle} this tick, at {@code rate} (BLD-03). */
    public void contribute(int handle, double rate) {
        int k = at(handle);
        tickBuilders[k]++;
        tickRates[k] += rate;
    }

    /** The builders at work on building {@code handle} this tick. */
    public int tickBuilders(int handle) {
        return tickBuilders[at(handle)];
    }

    /** The sum of their rates. */
    public double tickRates(int handle) {
        return tickRates[at(handle)];
    }

    /** Forgets this tick's builders, ready for the next. */
    public void clearTick(int handle) {
        int k = at(handle);
        tickBuilders[k] = 0;
        tickRates[k] = 0;
    }

    /** The builders' work so far on building {@code handle}, in millionths of a tick at one builder's rate (BLD-03). */
    public long work(int handle) {
        return work[at(handle)];
    }

    public void setWork(int handle, long value) {
        work[at(handle)] = value;
    }

    /** How many of its blueprint's blocks, in build order, the core has asked the writer for. */
    public int placed(int handle) {
        return placed[at(handle)];
    }

    public void setPlaced(int handle, int value) {
        placed[at(handle)] = value;
    }

    /** Building {@code handle}'s builder slots: the cells round its footprint, each {@code x << 16 | z}, ascending. */
    public void setSlots(int handle, int[] cells) {
        int k = at(handle);
        slots[k] = cells.clone();
        claims[k] = new int[cells.length];
    }

    public int slotCount(int handle) {
        return slots[at(handle)].length;
    }

    public int slot(int handle, int slot) {
        return slots[at(handle)][slot];
    }

    /** The unit holding builder slot {@code slot}, 0 when free. */
    public int claim(int handle, int slot) {
        return claims[at(handle)][slot];
    }

    /**
     * Unit {@code unit} takes the free builder slot nearest (x, z) whose cell {@code standable} accepts, the lowest on a
     * tie, or keeps the one it holds; its index, or -1 when none is free.
     */
    public int claimNearest(int handle, int unit, double x, double z, java.util.function.IntPredicate standable) {
        int k = at(handle);
        int best = -1;
        double bestD = Double.POSITIVE_INFINITY;
        for (int s = 0; s < slots[k].length; s++) {
            if (claims[k][s] == unit) {
                return s;
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
            claims[k][best] = unit;
        }
        return best;
    }

    /** Unit {@code unit} gives up any builder slot it holds on building {@code handle}. */
    public void release(int handle, int unit) {
        if (!alive(handle)) {
            return;
        }
        int[] c = claims[at(handle)];
        for (int s = 0; s < c.length; s++) {
            if (c[s] == unit) {
                c[s] = 0;
            }
        }
    }

    /** The live building whose builder slot unit {@code unit} holds, 0 for none: a restored unit's claim. */
    public int claimedBy(int unit) {
        for (int k = 0; k < high; k++) {
            if (alive[k]) {
                for (int c : claims[k]) {
                    if (c == unit) {
                        return Handle.of(BASE + k, generation[k]);
                    }
                }
            }
        }
        return 0;
    }

    /** Whether {@code handle} is a building that stands now. */
    public boolean alive(int handle) {
        if (!isBuilding(handle)) {
            return false;
        }
        int k = Handle.index(handle) - BASE;
        return k < high && alive[k] && generation[k] == Handle.generation(handle);
    }

    private int at(int handle) {
        if (!alive(handle)) {
            throw new IllegalArgumentException("no building " + Handle.toString(handle));
        }
        return Handle.index(handle) - BASE;
    }

    /** Takes the building out; its handle never names it again. */
    public void remove(int handle) {
        alive[at(handle)] = false;
    }

    /** The live buildings' handles, by slot. */
    public int[] handles() {
        int n = 0;
        for (int k = 0; k < high; k++) {
            if (alive[k]) {
                n++;
            }
        }
        int[] out = new int[n];
        int i = 0;
        for (int k = 0; k < high; k++) {
            if (alive[k]) {
                out[i++] = Handle.of(BASE + k, generation[k]);
            }
        }
        return out;
    }

    /** Whether no building ever stood: such a store hashes and saves as none. */
    public boolean empty() {
        return high == 0;
    }

    public int template(int handle) {
        return template[at(handle)];
    }

    public int faction(int handle) {
        return faction[at(handle)];
    }

    public int baseY(int handle) {
        return baseY[at(handle)];
    }

    public int state(int handle) {
        return state[at(handle)];
    }

    public long progress(int handle) {
        return progress[at(handle)];
    }

    public double damage(int handle) {
        return damage[at(handle)];
    }

    public void setDamage(int handle, double value) {
        damage[at(handle)] = value;
    }

    public void setState(int handle, int value, long progress) {
        int k = at(handle);
        state[k] = value;
        this.progress[k] = progress;
    }

    /** Where it stands, given its blueprint's size. */
    public Footprint footprint(int handle, int sizeX, int sizeZ) {
        int k = at(handle);
        return new Footprint(anchorX[k], anchorZ[k], rotation[k], sizeX, sizeZ);
    }

    public int anchorX(int handle) {
        return anchorX[at(handle)];
    }

    public int anchorZ(int handle) {
        return anchorZ[at(handle)];
    }

    public int rotation(int handle) {
        return rotation[at(handle)];
    }

    public long mix(long h) {
        long x = Hasher.mix(h, high);
        for (int k = 0; k < high; k++) {
            x = Hasher.mix(x, generation[k]);
            x = Hasher.mix(x, alive[k] ? 1 : 0);
            if (alive[k]) {
                x = Hasher.mix(x, template[k]);
                x = Hasher.mix(x, faction[k]);
                x = Hasher.mix(x, anchorX[k]);
                x = Hasher.mix(x, anchorZ[k]);
                x = Hasher.mix(x, rotation[k]);
                x = Hasher.mix(x, baseY[k]);
                x = Hasher.mix(x, state[k]);
                x = Hasher.mix(x, progress[k]);
                x = Hasher.mix(x, Double.doubleToLongBits(damage[k]));
                if (state[k] != COMPLETE) { // only a foundation or a site (BLD-03), so finished ones hash as before
                    x = Hasher.mix(x, work[k]);
                    x = Hasher.mix(x, placed[k]);
                    for (int c : claims[k]) {
                        x = Hasher.mix(x, c);
                    }
                }
            }
        }
        return x;
    }

    /** One slot as a snapshot keeps it; a free slot keeps only its generation. */
    public record Saved(
            int generation,
            boolean alive,
            int template,
            int faction,
            int anchorX,
            int anchorZ,
            int rotation,
            int baseY,
            int state,
            long progress,
            double damage,
            long work,
            int placed,
            List<Integer> slots,
            List<Integer> claims) {
        public Saved {
            slots = List.copyOf(slots);
            claims = List.copyOf(claims);
        }
    }

    public List<Saved> save() {
        List<Saved> out = new ArrayList<>(high);
        for (int k = 0; k < high; k++) {
            out.add(new Saved(
                    generation[k],
                    alive[k],
                    template[k],
                    faction[k],
                    anchorX[k],
                    anchorZ[k],
                    rotation[k],
                    baseY[k],
                    state[k],
                    progress[k],
                    damage[k],
                    work[k],
                    placed[k],
                    Arrays.stream(slots[k] == null ? new int[0] : slots[k])
                            .boxed()
                            .toList(),
                    Arrays.stream(claims[k] == null ? new int[0] : claims[k])
                            .boxed()
                            .toList()));
        }
        return out;
    }

    /** Restores a snapshot's slots into this empty store. */
    public void restore(List<Saved> saved) {
        if (high != 0) {
            throw new IllegalStateException("restoring buildings into a store that holds " + high + " slots");
        }
        grow(Math.max(8, saved.size()));
        for (Saved s : saved) {
            int k = high++;
            generation[k] = s.generation();
            alive[k] = s.alive();
            template[k] = s.template();
            faction[k] = s.faction();
            anchorX[k] = s.anchorX();
            anchorZ[k] = s.anchorZ();
            rotation[k] = s.rotation();
            baseY[k] = s.baseY();
            state[k] = s.state();
            progress[k] = s.progress();
            damage[k] = s.damage();
            work[k] = s.work();
            placed[k] = s.placed();
            slots[k] = s.slots().stream().mapToInt(Integer::intValue).toArray();
            claims[k] = new int[slots[k].length];
            for (int i = 0; i < claims[k].length && i < s.claims().size(); i++) {
                claims[k][i] = s.claims().get(i);
            }
        }
    }
}
