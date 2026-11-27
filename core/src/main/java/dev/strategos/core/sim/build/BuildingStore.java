package dev.strategos.core.sim.build;

import dev.strategos.core.content.Footprint;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.Hasher;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;
import org.jspecify.annotations.Nullable;

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

    private long changes;
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
    private final BuildingQueues queues = new BuildingQueues(this);
    private int[] field = new int[8]; // a field's crop node, 0 for none (ECO-09)
    private int[] stage = new int[8]; // a finished building's damage stage, 0 whole (BLD-05)
    private boolean[] autoReseed = new boolean[8]; // a farmstead's toggle: it sows its reach's empty fields (ECO-17)
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
        changes++;
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
        queues.reset(k);
        this.field[k] = 0;
        this.stage[k] = 0;
        this.autoReseed[k] = false;
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
        queues.grow(n);
        field = Arrays.copyOf(field, n);
        stage = Arrays.copyOf(stage, n);
        autoReseed = Arrays.copyOf(autoReseed, n);
    }

    /** Whether building {@code handle} sows the empty fields in its reach by itself (ECO-17, a farmstead's toggle). */
    public boolean autoReseed(int handle) {
        return autoReseed[at(handle)];
    }

    public void setAutoReseed(int handle, boolean on) {
        autoReseed[at(handle)] = on;
    }

    /** A finished building's damage stage (BLD-05): 0 whole, up to {@link DamageStages#STAGES}. */
    public int stage(int handle) {
        return stage[at(handle)];
    }

    public void setStage(int handle, int value) {
        stage[at(handle)] = value;
    }

    /** A field's crop node, 0 for none or for a building that is no field (ECO-09). */
    public int field(int handle) {
        return field[at(handle)];
    }

    public void setField(int handle, int node) {
        field[at(handle)] = node;
    }

    /** The most entries a queue holds (PROD-01). */
    public static final int QUEUE_MAX = 5;

    /** No rally point; a point on the ground; a resource node; a building (PROD-03). */
    public static final int RALLY_NONE = 0;

    public static final int RALLY_POINT = 1;
    public static final int RALLY_NODE = 2;
    public static final int RALLY_BUILDING = 3;

    /** The production queues and rally points of the buildings (PROD-01 to -03). */
    public BuildingQueues queues() {
        return queues;
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

    int at(int handle) {
        if (!alive(handle)) {
            throw new IllegalArgumentException("no building " + Handle.toString(handle));
        }
        return Handle.index(handle) - BASE;
    }

    /** Takes the building out; its handle never names it again. */
    public void remove(int handle) {
        alive[at(handle)] = false;
        changes++;
    }

    /**
     * A number that moves whenever a building is placed, removed or restored or changes state; not saved or hashed, a
     * reader caches against it.
     */
    public long changes() {
        return changes;
    }

    /** How many slots {@link #handleAt} may be asked: an allocation-free walk over the live buildings. */
    public int capacity() {
        return high;
    }

    /** The handle of the building in slot {@code k} (below {@link #capacity}), or {@link Handle#NONE} for a free one. */
    public int handleAt(int k) {
        return alive[k] ? Handle.of(BASE + k, generation[k]) : Handle.NONE;
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

    /** Building {@code handle} changes owner (CMB-10): only {@link BuildingLife#transfer} calls this. */
    void setFaction(int handle, int faction) {
        this.faction[at(handle)] = faction;
        changes++;
    }

    /** Building {@code handle} becomes another template in place (BLD-12): only {@code Upgrades} calls this. */
    public void setTemplate(int handle, int template) {
        this.template[at(handle)] = template;
        changes++;
    }

    public void setState(int handle, int value, long progress) {
        int k = at(handle);
        if (state[k] != value) {
            changes++;
        }
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
                if (stage[k] != 0) { // only once damaged past a threshold (BLD-05)
                    x = Hasher.mix(x, stage[k]);
                }
                if (autoReseed[k]) { // only a farmstead's (ECO-17)
                    x = Hasher.mix(x, 1);
                }
                if (state[k] != COMPLETE) { // only a foundation or a site (BLD-03), so finished ones hash as before
                    x = Hasher.mix(x, work[k]);
                    x = Hasher.mix(x, placed[k]);
                    for (int c : claims[k]) {
                        x = Hasher.mix(x, c);
                    }
                }
                x = queues.mix(x, k);
            }
        }
        return x;
    }

    /** A site's construction as a snapshot keeps it (BLD-03). */
    public record Site(long work, int placed, List<Integer> slots, List<Integer> claims) {
        public static final Site NONE = new Site(0, 0, List.of(), List.of());

        public Site {
            slots = List.copyOf(slots);
            claims = List.copyOf(claims);
        }
    }

    /**
     * A queue as a snapshot keeps it (PROD-01): its entries, the head's ticks, its reservation and its wait. A tech
     * entry is saved by its id (spec row 20): {@code -1 - k} in {@code queue}, {@code k} the place of its id in {@code
     * techs}.
     */
    public record Production(
            List<Integer> queue,
            long trained,
            boolean reserved,
            boolean waiting,
            List<Integer> batches,
            boolean autoReseed,
            List<String> techs) {
        public static final Production NONE = new Production(List.of(), 0, false, false, List.of(), false, List.of());

        /** A queue of single entries, as before batches (PROD-02). */
        public Production(List<Integer> queue, long trained, boolean reserved, boolean waiting) {
            this(queue, trained, reserved, waiting, List.of(), false, List.of());
        }

        /** A queue of units, as before technologies (TECH-01). */
        public Production(
                List<Integer> queue,
                long trained,
                boolean reserved,
                boolean waiting,
                List<Integer> batches,
                boolean autoReseed) {
            this(queue, trained, reserved, waiting, batches, autoReseed, List.of());
        }

        public Production {
            queue = List.copyOf(queue);
            batches = List.copyOf(batches);
            techs = List.copyOf(techs);
        }
    }

    /** A rally point as a snapshot keeps it (PROD-03). */
    public record Rally(int kind, double x, double z, int target) {
        public static final Rally NONE = new Rally(RALLY_NONE, 0, 0, 0);
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
            Site site,
            Production production,
            Rally rally,
            int field,
            int stage) {}

    /**
     * The slots as a snapshot keeps them; {@code techId} names a tech by net index, so a queued tech is saved by its id
     * (an entry whose tech it cannot name is left out).
     */
    public List<Saved> save(IntFunction<@Nullable String> techId) {
        List<Saved> out = new ArrayList<>(high);
        for (int k = 0; k < high; k++) {
            Site site = new Site(
                    work[k],
                    placed[k],
                    Arrays.stream(slots[k] == null ? new int[0] : slots[k])
                            .boxed()
                            .toList(),
                    Arrays.stream(claims[k] == null ? new int[0] : claims[k])
                            .boxed()
                            .toList());
            Production production = queues.production(k, techId, autoReseed[k]);
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
                    site,
                    production,
                    queues.rally(k),
                    field[k],
                    stage[k]));
        }
        return out;
    }

    /**
     * Restores a snapshot's slots into this empty store. {@code techIndex} gives a saved tech id its net index in the
     * current set, or -1; an entry whose id the set lacks is dropped without a refund (its cost is no longer known).
     */
    public void restore(List<Saved> saved, ToIntFunction<String> techIndex) {
        if (high != 0) {
            throw new IllegalStateException("restoring buildings into a store that holds " + high + " slots");
        }
        grow(Math.max(8, saved.size()));
        changes++;
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
            work[k] = s.site().work();
            placed[k] = s.site().placed();
            slots[k] = s.site().slots().stream().mapToInt(Integer::intValue).toArray();
            claims[k] = new int[slots[k].length];
            for (int i = 0; i < claims[k].length && i < s.site().claims().size(); i++) {
                claims[k][i] = s.site().claims().get(i);
            }
            queues.restore(k, s, techIndex);
            autoReseed[k] = s.production().autoReseed();
            field[k] = s.field();
            stage[k] = s.stage();
        }
    }
}
