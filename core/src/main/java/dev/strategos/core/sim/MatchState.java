package dev.strategos.core.sim;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.diag.MatchLog;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.combat.Damage;
import dev.strategos.core.sim.combat.HealthStore;
import dev.strategos.core.sim.econ.Amount;
import dev.strategos.core.sim.econ.Costs;
import dev.strategos.core.sim.econ.EconomyRules;
import dev.strategos.core.sim.econ.Treasury;
import dev.strategos.core.sim.nav.MobilityClass;
import dev.strategos.core.sim.nav.PageCoord;
import dev.strategos.core.sim.nav.PathStore;
import dev.strategos.core.sim.nav.SurfaceGrid;
import dev.strategos.core.sim.nav.SurfacePage;
import dev.strategos.core.sim.order.OrderQueues;
import dev.strategos.core.sim.spatial.SpatialQueries;
import dev.strategos.core.sim.stats.StatService;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

/**
 * Everything a snapshot holds plus per-tick scratch, with no Minecraft reference. Public accessors, because the systems
 * live in {@code sim.*} subpackages; not {@code @CoreApi}, so the mod cannot reach it (B2).
 */
public final class MatchState {
    /** The first group id: 0 means an order given to one unit alone. */
    public static final int FIRST_GROUP = 1;

    private long tick;
    private final MatchConfig config;
    private TemplateSet templates;
    private final EntityStore entities;
    private final IdentityStore identity = new IdentityStore();
    private final MotionStore motion = new MotionStore();
    private final OrderQueues orders = new OrderQueues();
    private final AiStateStore ai = new AiStateStore();
    private final PathStore paths = new PathStore();
    private final HealthStore health = new HealthStore();
    private final Treasury treasury;
    private final Costs costs;
    private final List<Damage> damage = new ArrayList<>();
    private final MatchBus bus = new MatchBus();
    private final StatService stats;
    private final SpatialQueries spatial;
    private final ArrayDeque<CommandEnvelope> pendingCommands = new ArrayDeque<>();
    private final List<OutputEvent> outputs = new ArrayList<>();
    private final MatchLog log;
    private BodyFrame.Builder frame = BodyFrame.builder(0);
    private TickInputs input;
    private int nextGroup = FIRST_GROUP;
    private final long[] rungs = new long[Stuck.GIVE_UP + 1]; // the stuck ladder's rungs reached in this run, by rung
    private final List<Agent> agents = new ArrayList<>();
    private final float[] leaders = new float[LeaderUpdate.SLOTS * 3]; // per slot x, z, yaw
    private final boolean[] leading = new boolean[LeaderUpdate.SLOTS];
    private final List<JobDelivery> deliveries = new ArrayList<>();
    private SurfaceGrid surfaces;
    private final long[] pageSlices;
    private final BitSet published;
    /** Pages whose slice is known: published, or named by a restored snapshot whose slice was missing. */
    private final BitSet known;

    private int[] changed = new int[4];
    private int changedCount;

    MatchState(MatchConfig config, TemplateSet templates, EntityStore entities, MatchLog log) {
        this.config = config;
        this.templates = templates;
        this.entities = entities;
        this.log = log;
        this.stats = new StatService(templates, config.factions(), identity, bus);
        EconomyRules economy = config.rules().economy();
        this.treasury = new Treasury(config.factions(), economy.size());
        for (int f = 0; f < config.factions(); f++) {
            for (int r = 0; r < economy.size(); r++) {
                long start = economy.resources().get(r).start();
                if (start > 0) {
                    treasury.grant(f, r, start * Amount.UNIT); // the starting resources (MATCH-01)
                }
            }
        }
        this.costs = new Costs(economy);
        costs.swap(stats);
        this.spatial = new SpatialQueries(config.bounds(), templates);
        this.input = TickInputs.empty(0);
        this.surfaces =
                SurfaceGrid.empty(config.bounds().sizeX(), config.bounds().sizeZ(), MobilityClass.INFANTRY);
        this.pageSlices = new long[surfaces.pagesX() * surfaces.pagesZ()];
        this.published = new BitSet(pageSlices.length);
        this.known = new BitSet(pageSlices.length);
        ensureCapacity(entities.highWater());
    }

    /** The published terrain: a copy-on-write grid, so a job keeps the one it captured. */
    public SurfaceGrid surfaces() {
        return surfaces;
    }

    /**
     * Publishes a rebuilt page; a page outside the match's grid is refused. A page whose slice differs from the one
     * last known there counts as changed this tick: routes across it are asked for again (PATH-15).
     */
    public void publish(TerrainPage page) {
        publishAll(List.of(page));
    }

    /** Publishes a tick's rebuilt pages at once, in their order, so the grid's components are joined once. */
    public void publishAll(List<TerrainPage> pages) {
        if (pages.isEmpty()) {
            return;
        }
        List<SurfacePage> built = new ArrayList<>(pages.size());
        for (TerrainPage page : pages) {
            int index = pageIndex(page.coord());
            boolean changes = !known.get(index) || pageSlices[index] != page.sliceHash();
            pageSlices[index] = page.sliceHash();
            published.set(index);
            known.set(index);
            if (changes) {
                if (changedCount == changed.length) {
                    changed = Arrays.copyOf(changed, changedCount * 2);
                }
                changed[changedCount++] = index;
                paths.pageChanged(tick);
            }
            built.add(page.page());
        }
        surfaces = surfaces.publishAll(built);
    }

    /**
     * A restored page whose slice is missing: it stays unpublished, but its slice hash is known, so the same slice
     * published again (a restart's prewarm) does not count as a change.
     */
    void expect(PageRef ref) {
        int index = pageIndex(new PageCoord(ref.x(), ref.z()));
        pageSlices[index] = ref.slice();
        known.set(index);
    }

    private int pageIndex(PageCoord c) {
        if (c.x() >= surfaces.pagesX() || c.z() >= surfaces.pagesZ()) {
            throw new IllegalArgumentException(
                    "page " + c + " outside the match's " + surfaces.pagesX() + " x " + surfaces.pagesZ() + " pages");
        }
        return c.z() * surfaces.pagesX() + c.x();
    }

    /** How many pages changed this tick. */
    public int changedPageCount() {
        return changedCount;
    }

    /** This tick's {@code k}th changed page, by index {@code z * pagesX + x}, in publication order. */
    public int changedPage(int k) {
        if (k >= changedCount) {
            throw new IndexOutOfBoundsException("changed page " + k + " of " + changedCount);
        }
        return changed[k];
    }

    /** The published pages, in page order, by the hash of the slice each was built from. */
    public List<PageRef> pageRefs() {
        List<PageRef> refs = new ArrayList<>(published.cardinality());
        for (int i = published.nextSetBit(0); i >= 0; i = published.nextSetBit(i + 1)) {
            refs.add(new PageRef(i % surfaces.pagesX(), i / surfaces.pagesX(), pageSlices[i]));
        }
        return refs;
    }

    /** Starts a tick: its number and inputs, a cleared output list and a new body frame. */
    void beginTick(TickInputs in) {
        tick = in.tick();
        input = in;
        outputs.clear();
        changedCount = 0;
        frame = BodyFrame.builder(entities.aliveCount());
        agents.clear();
        Arrays.fill(leading, false);
        deliveries.clear();
    }

    void setTick(long tick) {
        this.tick = tick;
    }

    /** Grows every per-unit store to hold {@code size} indices. */
    public void ensureCapacity(int size) {
        identity.ensureCapacity(size);
        motion.ensureCapacity(size);
        orders.ensureCapacity(size);
        ai.ensureCapacity(size);
        paths.ensureCapacity(size);
        health.ensureCapacity(size);
    }

    /** The damage each unit has taken (CMB-01). */
    public HealthStore health() {
        return health;
    }

    /** A damage packet for this tick's COMBAT phase, after those already queued. */
    public void queueDamage(Damage packet) {
        damage.add(packet);
    }

    /** This tick's damage packets, in their order; the queue is empty after. */
    public List<Damage> takeDamage() {
        if (damage.isEmpty()) {
            return List.of();
        }
        List<Damage> taken = List.copyOf(damage);
        damage.clear();
        return taken;
    }

    /**
     * Takes a dead unit out of the match: its index is freed with a new generation, so its handle is stale everywhere
     * (path results and waiting requests for it are dropped on arrival); a unit spawned there later resets every store.
     */
    public void removeUnit(int handle) {
        health.reset(Handle.index(handle));
        entities.destroy(handle);
    }

    public long tick() {
        return tick;
    }

    public MatchConfig config() {
        return config;
    }

    public TemplateSet templates() {
        return templates;
    }

    public EntityStore entities() {
        return entities;
    }

    public IdentityStore identity() {
        return identity;
    }

    public MotionStore motion() {
        return motion;
    }

    public OrderQueues orders() {
        return orders;
    }

    public AiStateStore ai() {
        return ai;
    }

    public PathStore paths() {
        return paths;
    }

    /** Where units and static entities are, for range queries (CORE-08). */
    public SpatialQueries spatial() {
        return spatial;
    }

    /** Every faction's resources and their counters (ECO-01). */
    public Treasury treasury() {
        return treasury;
    }

    /** What templates cost each faction (ECO-01). */
    public Costs costs() {
        return costs;
    }

    /** Every unit's numbers, through the modifier pipeline (ADR-0013). */
    public StatService stats() {
        return stats;
    }

    public MatchBus bus() {
        return bus;
    }

    /** Commands waiting to apply, FIFO; part of the snapshot. */
    public ArrayDeque<CommandEnvelope> pendingCommands() {
        return pendingCommands;
    }

    /** This tick's output events, cleared when a tick starts. */
    public List<OutputEvent> outputs() {
        return outputs;
    }

    /** This tick's body poses. */
    public BodyFrame.Builder frame() {
        return frame;
    }

    /** Counts a rung of the stuck ladder reached (PATH-07): statistics for the overlay and the soak, not state. */
    public void countRung(int rung) {
        rungs[rung]++;
    }

    /** How often each rung of the stuck ladder was reached in this run, by rung; index 0 is unused. */
    public long[] rungs() {
        return rungs.clone();
    }

    /** A new group id for the orders of one command to several units (ORD-02); ids are never reused. */
    public int newGroup() {
        return nextGroup++;
    }

    /** The id the next group gets. */
    public int nextGroup() {
        return nextGroup;
    }

    void setNextGroup(int next) {
        this.nextGroup = next;
    }

    /** This tick's agents, from its {@link AgentUpdate}s in order; none on a tick without one. */
    public List<Agent> agents() {
        return agents;
    }

    void addAgents(List<Agent> more) {
        agents.addAll(more);
    }

    void setLeader(LeaderUpdate leader) {
        int at = leader.slot() * 3;
        leaders[at] = leader.x();
        leaders[at + 1] = leader.z();
        leaders[at + 2] = leader.yaw();
        leading[leader.slot()] = true;
    }

    /** Whether the commander of player slot {@code slot} stands in first person this tick (CAM-07). */
    public boolean leads(int slot) {
        return slot >= 0 && slot < LeaderUpdate.SLOTS && leading[slot];
    }

    /** This tick's X of the commander of {@code slot}, when it {@link #leads}. */
    public double leaderX(int slot) {
        return leaders[slot * 3];
    }

    public double leaderZ(int slot) {
        return leaders[slot * 3 + 1];
    }

    public double leaderYaw(int slot) {
        return leaders[slot * 3 + 2];
    }

    /**
     * INGEST's template swap (DATA-03): the stats and the spatial index take the new set's values; refused, keeping the
     * set, when a live unit's template is missing from it or holds another net index there.
     */
    void swapTemplates(TemplateSet set) {
        for (int i = 0; i < entities.highWater(); i++) {
            if (!entities.aliveAt(i)) {
                continue;
            }
            int index = identity.templateIndex(i);
            UnitTemplate now = templates.byIndex(index);
            UnitTemplate next = set.byId(now.id());
            if (next == null || next.netIndex() != index) {
                log.warn(tick, "template set {} refused: unit {} uses {}, which it lacks", set, i, now.id());
                return;
            }
        }
        templates = set;
        stats.swap(set);
        costs.swap(stats);
        spatial.swap(set);
    }

    /** Whether this tick's {@link JobDelivery} inputs list path request {@code id}; none on a tick without one. */
    public boolean delivered(int id) {
        for (JobDelivery delivery : deliveries) {
            if (delivery.contains(id)) {
                return true;
            }
        }
        return false;
    }

    void addDelivery(JobDelivery delivery) {
        deliveries.add(delivery);
    }

    /** This tick's inputs, which INGEST reads. */
    public TickInputs input() {
        return input;
    }

    public MatchLog log() {
        return log;
    }
}
