package dev.strategos.core.sim;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.diag.MatchLog;
import dev.strategos.core.sim.nav.MobilityClass;
import dev.strategos.core.sim.nav.PageCoord;
import dev.strategos.core.sim.nav.SurfaceGrid;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Everything a snapshot holds plus per-tick scratch, with no Minecraft reference. Public accessors, because the systems
 * live in {@code sim.*} subpackages; not {@code @CoreApi}, so the mod cannot reach it (B2).
 */
public final class MatchState {
    private long tick;
    private final MatchConfig config;
    private final TemplateSet templates;
    private final EntityStore entities;
    private final IdentityStore identity = new IdentityStore();
    private final MotionStore motion = new MotionStore();
    private final OrderStore orders = new OrderStore();
    private final AiStateStore ai = new AiStateStore();
    private final MatchBus bus = new MatchBus();
    private final ArrayDeque<CommandEnvelope> pendingCommands = new ArrayDeque<>();
    private final List<OutputEvent> outputs = new ArrayList<>();
    private final MatchLog log;
    private BodyFrame.Builder frame = BodyFrame.builder(0);
    private TickInputs input;
    private SurfaceGrid surfaces;
    private final long[] pageSlices;
    private final BitSet published;

    MatchState(MatchConfig config, TemplateSet templates, EntityStore entities, MatchLog log) {
        this.config = config;
        this.templates = templates;
        this.entities = entities;
        this.log = log;
        this.input = TickInputs.empty(0);
        this.surfaces =
                SurfaceGrid.empty(config.bounds().sizeX(), config.bounds().sizeZ(), MobilityClass.INFANTRY);
        this.pageSlices = new long[surfaces.pagesX() * surfaces.pagesZ()];
        this.published = new BitSet(pageSlices.length);
        ensureCapacity(entities.highWater());
    }

    /** The published terrain: a copy-on-write grid, so a job keeps the one it captured. */
    public SurfaceGrid surfaces() {
        return surfaces;
    }

    /** Publishes a rebuilt page; a page outside the match's grid is refused. */
    public void publish(TerrainPage page) {
        PageCoord c = page.coord();
        if (c.x() >= surfaces.pagesX() || c.z() >= surfaces.pagesZ()) {
            throw new IllegalArgumentException(
                    "page " + c + " outside the match's " + surfaces.pagesX() + " x " + surfaces.pagesZ() + " pages");
        }
        surfaces = surfaces.publish(page.page());
        int index = c.z() * surfaces.pagesX() + c.x();
        pageSlices[index] = page.sliceHash();
        published.set(index);
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
        frame = BodyFrame.builder(entities.aliveCount());
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

    public OrderStore orders() {
        return orders;
    }

    public AiStateStore ai() {
        return ai;
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

    /** This tick's inputs, which INGEST reads. */
    public TickInputs input() {
        return input;
    }

    public MatchLog log() {
        return log;
    }
}
