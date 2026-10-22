package dev.strategos.core.sim;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.diag.MatchLog;
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
    private final TemplateSet templates;
    private final EntityStore entities;
    private final IdentityStore identity = new IdentityStore();
    private final MotionStore motion = new MotionStore();
    private final OrderQueues orders = new OrderQueues();
    private final AiStateStore ai = new AiStateStore();
    private final PathStore paths = new PathStore();
    private final MatchBus bus = new MatchBus();
    private final StatService stats;
    private final SpatialQueries spatial;
    private final ArrayDeque<CommandEnvelope> pendingCommands = new ArrayDeque<>();
    private final List<OutputEvent> outputs = new ArrayList<>();
    private final MatchLog log;
    private BodyFrame.Builder frame = BodyFrame.builder(0);
    private TickInputs input;
    private int nextGroup = FIRST_GROUP;
    private final List<Agent> agents = new ArrayList<>();
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

    /** This tick's inputs, which INGEST reads. */
    public TickInputs input() {
        return input;
    }

    public MatchLog log() {
        return log;
    }
}
