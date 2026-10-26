package dev.strategos.core.sim;

import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.diag.MatchLog;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.build.BuildingLife;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.DamageRanks;
import dev.strategos.core.sim.combat.Damage;
import dev.strategos.core.sim.combat.HealthStore;
import dev.strategos.core.sim.combat.ShotStore;
import dev.strategos.core.sim.econ.Amount;
import dev.strategos.core.sim.econ.CarryStore;
import dev.strategos.core.sim.econ.Costs;
import dev.strategos.core.sim.econ.EconomyChecks;
import dev.strategos.core.sim.econ.EconomyRules;
import dev.strategos.core.sim.econ.EconomyTimes;
import dev.strategos.core.sim.econ.GatherRates;
import dev.strategos.core.sim.econ.Ledger;
import dev.strategos.core.sim.econ.NodeIndex;
import dev.strategos.core.sim.econ.NodeStore;
import dev.strategos.core.sim.econ.Treasury;
import dev.strategos.core.sim.nav.Material;
import dev.strategos.core.sim.nav.MaterialKind;
import dev.strategos.core.sim.nav.MobilityClass;
import dev.strategos.core.sim.nav.PathStore;
import dev.strategos.core.sim.nav.SurfaceGrid;
import dev.strategos.core.sim.nav.TerrainEdits;
import dev.strategos.core.sim.order.OrderQueues;
import dev.strategos.core.sim.prod.Population;
import dev.strategos.core.sim.spatial.SpatialQueries;
import dev.strategos.core.sim.stats.StatService;
import dev.strategos.core.sim.unitai.ActivityTiers;
import dev.strategos.core.sim.unitai.UnitAi;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Everything a snapshot holds plus per-tick scratch, with no Minecraft reference. Public accessors, because the systems
 * live in {@code sim.*} subpackages; not {@code @CoreApi}, so the mod cannot reach it (B2).
 */
public final class MatchState {
    /** The first group id: 0 means an order given to one unit alone. */
    public static final int FIRST_GROUP = 1;

    private long tick;
    private MatchConfig config;
    private final Outcome outcome = new Outcome();
    private final AiSlots aiSlots = new AiSlots();
    private TemplateSet templates;
    private final EntityStore entities;
    private final IdentityStore identity = new IdentityStore();
    private final MotionStore motion = new MotionStore();
    private final OrderQueues orders = new OrderQueues();
    private final AiStateStore ai = new AiStateStore();
    private final PathStore paths = new PathStore();
    private final HealthStore health = new HealthStore();
    private final ShotStore shots = new ShotStore();
    private final Treasury treasury;
    private final Costs costs;
    private final GatherRates gatherRates;
    private final CarryStore carry = new CarryStore();
    private final Ledger ledger;
    private final Population population;
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
    private final Leaders leaders = new Leaders();
    private final ActivityTiers tiers = new ActivityTiers();
    private final List<JobDelivery> deliveries = new ArrayList<>();
    private final MatchTerrain terrain;
    private @Nullable MobilityClass[] mobilityByTemplate = new MobilityClass[0];
    private boolean hasLargeMobility;
    private final NodeStore nodes = new NodeStore();
    private final BuildingStore buildings = new BuildingStore();
    private final Alerts alerts = new Alerts(); // UI-12's live alerts, saved (MATCH-04)
    private final java.util.TreeMap<String, int[]> damageRanks = new java.util.TreeMap<>(); // by blueprint id
    private @Nullable NodeIndex nodeIndex;
    private final EconomyTimes economyTimes = new EconomyTimes();

    MatchState(MatchConfig config, TemplateSet templates, EntityStore entities, MatchLog log) {
        this.config = config;
        this.templates = templates;
        cacheMobility();
        this.entities = entities;
        this.log = log;
        this.stats = new StatService(templates, config.factions() + 1, identity, bus); // the players' and Gaia's
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
        this.gatherRates = new GatherRates(economy);
        gatherRates.swap(stats);
        this.ledger = new Ledger(economy.size());
        this.population = new Population(config.factions(), economy.populationMax());
        this.spatial = new SpatialQueries(config.bounds(), templates);
        this.input = TickInputs.empty(0);
        this.terrain = new MatchTerrain(this);
        ensureCapacity(entities.highWater());
    }

    /** The published terrain: a copy-on-write grid, so a job keeps the one it captured. */
    public SurfaceGrid surfaces() {
        return terrain.surfaces();
    }

    /** The derived grid whose clearance fits this unit, including the large class's page halo. */
    public SurfaceGrid surfacesFor(int index) {
        return terrain.surfaces(mobility(index));
    }

    public MobilityClass mobility(int index) {
        int template = identity.templateIndex(index);
        if (template >= 0 && template < mobilityByTemplate.length) {
            MobilityClass cached = mobilityByTemplate[template];
            if (cached != null) return cached;
        }
        return MobilityClass.of(templates.byIndex(template).mobility());
    }

    public boolean hasLargeMobility() {
        return hasLargeMobility;
    }

    private void cacheMobility() {
        mobilityByTemplate = new MobilityClass[templates.indexCount()];
        hasLargeMobility = false;
        for (UnitTemplate template : templates.all()) {
            MobilityClass mobility = switch (template.mobility()) {
                case "infantry" -> MobilityClass.INFANTRY;
                case "large" -> MobilityClass.LARGE;
                default -> null; // Preserve the original error when an unsupported mobility is queried.
            };
            mobilityByTemplate[template.netIndex()] = mobility;
            hasLargeMobility |= mobility == MobilityClass.LARGE;
        }
    }

    public boolean hasFootprints() {
        return terrain.hasFootprints();
    }

    public SurfaceGrid surfaces(MobilityClass mobility) {
        return terrain.surfaces(mobility);
    }

    public long navigationBytes() {
        return terrain.navigationBytes();
    }

    public int changedPageCount(MobilityClass mobility) {
        return terrain.changedPageCount(mobility);
    }

    public int changedPage(int k, MobilityClass mobility) {
        return terrain.changedPage(k, mobility);
    }

    /**
     * Publishes a rebuilt page; a page outside the match's grid is refused. A page whose slice differs from the one
     * last known there counts as changed this tick: routes across it are asked for again (PATH-15). The core's own
     * edits and footprints are laid over the slice (M3 spec §1.6 row 5): those up to the capture's sample tick are in
     * it already and are forgotten, the rest are applied by rebuilding the page here.
     */
    public void publish(TerrainPage page) {
        terrain.publish(page);
    }

    /** Publishes a tick's rebuilt pages at once, in their order, so the grid's components are joined once. */
    public void publishAll(List<TerrainPage> pages) {
        terrain.publishAll(pages);
    }

    /** Discover the resource model once all terrain slices have arrived. */
    void discoverNodes(byte[][] sliceBytes) {
        nodeIndex = null;
        MatchResourceDiscovery.discover(this, sliceBytes);
    }

    /**
     * The materials in the box (x0..x1, y0..y1, z0..z1), match-local x and z, absolute y, as the core now sees its
     * terrain: each page's last slice, less every voxel the core has edited since (ECO-03). Positions are packed by
     * {@link NodeStore#pack}; the result is in position order.
     */
    public List<long[]> materialsIn(int x0, int y0, int z0, int x1, int y1, int z1, MaterialKind kind) {
        return terrain.materials().in(x0, y0, z0, x1, y1, z1, kind);
    }

    /**
     * The material at match-local (x, z) and absolute height y as the core now sees its terrain, or null: none, or one
     * the core has taken away.
     */
    public @Nullable Material materialAt(int x, int y, int z) {
        return terrain.materials().at(x, y, z);
    }

    /** The match's resource nodes (ECO-02). */
    public NodeStore nodes() {
        return nodes;
    }

    /** The match's buildings (BLD-01). */
    public BuildingStore buildings() {
        return buildings;
    }

    /** Building {@code handle}'s blueprint. */
    public Blueprint blueprintOf(int handle) {
        return Objects.requireNonNull(
                templates.blueprint(buildings.template(handle)), "a building template without a blueprint");
    }

    /** The live under-attack alerts (UI-12). */
    Alerts alerts() {
        return alerts;
    }

    /** Blueprint {@code b}'s damage ranks (BLD-05), worked out once a match: derived, neither hashed nor saved. */
    public int[] damageRanks(Blueprint b) {
        return damageRanks.computeIfAbsent(b.id(), id -> DamageRanks.of(b));
    }

    /** Where building {@code handle} stands. */
    public Footprint footprintOf(int handle) {
        Blueprint b = blueprintOf(handle);
        return buildings.footprint(handle, b.sizeX(), b.sizeZ());
    }

    /**
     * Building {@code handle}'s most hit points: {@code Health/max} through its faction's modifiers, rising with its
     * construction from a thousandth of it at the foundation (BLD-03).
     */
    public double buildingMaxHp(int handle) {
        return BuildingLife.maxHp(this, handle);
    }

    /**
     * A finished building at once (BLD-01's instant placement, for scenarios and tests): its footprint's columns are
     * blocked for units, and the writer is asked to clear its volume and place its blocks in build order. Its handle.
     */
    public int placeComplete(int template, int faction, Footprint at, int baseY) {
        return BuildingLife.placeComplete(this, template, faction, at, baseY);
    }

    /**
     * Building {@code handle} falls (BLD-01: 0 hit points): its blocks above its floor layer go, the floor stays as a
     * foundation; its columns are released, and the core's terrain gets the air the writer will leave.
     */
    public void destroyBuilding(int handle) {
        BuildingLife.destroy(this, handle);
    }

    /** A node added after discovery, a field's (ECO-09): its id; the index is built again on its next use. */
    public int addNode(
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
        int id = nodes.add(kind, resource, subtype, perBlock, amount, anchorX, anchorZ, baseY, blocks, slots);
        nodeIndex = null;
        return id;
    }

    /** PERF-07.4's times of the node index and the dropsite queries; measured, never saved or hashed. */
    public EconomyTimes economyTimes() {
        return economyTimes;
    }

    /** Where the nodes are, built on first use after discovery or a restore. */
    public NodeIndex nodeIndex() {
        NodeIndex index = nodeIndex;
        if (index == null) {
            long start = economyTimes.start();
            index = new NodeIndex(
                    nodes,
                    config.rules().economy(),
                    surfaces().pagesX(),
                    surfaces().pagesZ());
            nodeIndex = index;
            economyTimes.built(start);
        }
        return index;
    }

    /**
     * The core sets a voxel itself, at match-local column (x, z) and absolute height y: a log it removed, a footprint
     * it cleared. Its page is rebuilt in this tick's WORLD phase; the writer makes the block follow.
     */
    public void editVoxel(int x, int y, int z, short cls) {
        terrain.editVoxel(x, y, z, cls);
    }

    /** A snapshot's buildings, their footprints blocked again (the writer's blocks are the world's already). */
    void restoreBuildings(List<BuildingStore.Saved> saved) {
        buildings.restore(saved);
        for (int handle : buildings.handles()) {
            if (buildings.state(handle) != BuildingStore.FOUNDATION
                    && dev.strategos.core.sim.build.Placing.obstructs(
                            this, buildings.template(handle), buildings.faction(handle))) { // a field's are walked on
                for (int cell : footprintOf(handle).columns(blueprintOf(handle))) {
                    restoreColumn(cell >>> 16, cell & 0xFFFF);
                }
            }
        }
    }

    /** Whether a building's footprint blocks match-local column (x, z). */
    public boolean footprintAt(int x, int z) {
        return terrain.footprintAt(x, z);
    }

    /** Blocks a footprint column (BLD-01); its page is rebuilt in this tick's WORLD phase. */
    public void blockColumn(int x, int z) {
        terrain.blockColumn(x, z);
    }

    /** Blocks a restored footprint's column, before the pages are published: no rebuild follows. */
    public void restoreColumn(int x, int z) {
        terrain.restoreColumn(x, z);
    }

    /** Releases a footprint column; its page is rebuilt in this tick's WORLD phase. */
    public void releaseColumn(int x, int z) {
        terrain.releaseColumn(x, z);
    }

    /**
     * WORLD: rebuilds every page the core edited this tick, from its last slice, and publishes them as changed. A page
     * not captured yet gets its edits when it is first published.
     */
    public void rebuildEdited() {
        terrain.rebuildEdited();
    }

    /** The core's own terrain edits (M3 spec §1.6 row 5). */
    public TerrainEdits terrainEdits() {
        return terrain.terrainEdits();
    }

    /**
     * A restored page whose slice is missing: it stays unpublished, but its slice hash is known, so the same slice
     * published again (a restart's prewarm) does not count as a change.
     */
    void expect(PageRef ref) {
        terrain.expect(ref);
    }

    /** How many pages changed this tick. */
    public int changedPageCount() {
        return terrain.changedPageCount();
    }

    /** This tick's {@code k}th changed page, by index {@code z * pagesX + x}, in publication order. */
    public int changedPage(int k) {
        return terrain.changedPage(k);
    }

    /** The published pages, in page order, by the hash of the slice each was built from. */
    public List<PageRef> pageRefs() {
        return terrain.pageRefs();
    }

    /** Starts a tick: its number and inputs, a cleared output list and a new body frame. */
    void beginTick(TickInputs in) {
        tick = in.tick();
        input = in;
        outputs.clear();
        terrain.beginTick();
        frame = BodyFrame.builder(entities.aliveCount());
        agents.clear();
        leaders.clear();
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
        carry.ensureCapacity(size);
    }

    /** The damage each unit has taken (CMB-01). */
    public HealthStore health() {
        return health;
    }

    public ShotStore shots() {
        return shots;
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
        int i = Handle.index(handle);
        if (identity.faction(i) < config.factions()) { // Gaia has no population
            population.remove(identity.faction(i), unitPopulation(i));
        }
        health.reset(i);
        releaseClaim(i, handle);
        dropLoad(i);
        entities.destroy(handle);
    }

    /**
     * A new unit of template {@code template} for {@code faction} at (x, y, z), facing {@code yaw}, idle, with every
     * store reset; it takes its population (ECO-06). Its handle; {@link UnitCreated} tells the mod.
     */
    public int spawnUnit(int template, int faction, double x, double y, double z, float yaw) {
        int handle = entities.create();
        int index = Handle.index(handle);
        ensureCapacity(index + 1);
        identity.set(index, template, faction);
        motion.set(index, x, y, z, yaw);
        orders.clear(index);
        paths.reset(index);
        motion.setExtra(index, MotionStore.Extra.NONE);
        stats.clearPersonal(index);
        health.reset(index);
        ai.reset(index, UnitAi.IDLE);
        if (faction < config.factions()) {
            population.add(faction, unitPopulation(index));
        }
        outputs.add(new UnitCreated(handle, template, new FactionSlot(faction), x, y, z, yaw));
        return handle;
    }

    /**
     * Gaia's slot (ECO-16): the one after the players', seated by nobody, whose units are the match's animals. It has
     * stats but no treasury, population, computer player or victory.
     */
    public int gaia() {
        return config.factions();
    }

    /** What unit {@code i} takes of its faction's population: its {@code Cost/population}, 0 for none (ECO-06). */
    public int unitPopulation(int i) {
        int path = stats.path(Population.COST);
        double v = path < 0 ? Double.NaN : stats.read(i, path);
        return Double.isFinite(v) && v > 0 ? (int) Math.round(v) : 0;
    }

    /** What a unit of {@code template} for {@code faction} takes of the population. */
    public int templatePopulation(int template, int faction) {
        int path = stats.path(Population.COST);
        double v = path < 0 ? Double.NaN : stats.blockValue(template, faction, path);
        return Double.isFinite(v) && v > 0 ? (int) Math.round(v) : 0;
    }

    /** The factions' population counters (ECO-06). */
    public Population population() {
        return population;
    }

    /** Unit {@code i} ({@code handle}) gives up the work slot it holds, if any (ECO-05). */
    public void releaseClaim(int i, int handle) {
        int node = carry.claimed(i);
        if (node != 0) {
            nodes.release(node, handle);
            carry.setClaimed(i, 0);
        }
        int site = carry.site(i);
        if (site != 0) {
            buildings.release(site, handle);
            carry.setSite(i, 0);
        }
    }

    /** Unit {@code i} drops what it carries: lost to everyone, as the ledger records. */
    public void dropLoad(int i) {
        int resource = carry.resource(i);
        long dropped = carry.clear(i);
        if (dropped > 0) {
            ledger.dropped(resource, dropped);
        }
    }

    /** The claims a restored match's nodes and buildings hold, put back on their units (ECO-05, BLD-03). */
    void restoreClaims() {
        EconomyChecks.restoreClaims(this);
    }

    /** What each unit carries and the slot it holds (ECO-05). */
    public CarryStore carry() {
        return carry;
    }

    /** Where gathered resources come from and go (ECO-05). */
    public Ledger ledger() {
        return ledger;
    }

    /** Gathering rates and carry capacities through the modifiers (ECO-05). */
    public GatherRates gatherRates() {
        return gatherRates;
    }

    /**
     * The first resource balance that does not hold (ECO-05, {@link Ledger}), or null: checked in FINAL each step, the
     * treasury's beside it.
     */
    public @Nullable String economyBroken() {
        return EconomyChecks.broken(this);
    }

    public long tick() {
        return tick;
    }

    public MatchConfig config() {
        return config;
    }

    /** A journaled rule change's rules (MATCH-01): the config carries them, and the population takes its maximum. */
    public void setRules(MatchRules rules) {
        config = new MatchConfig(config.id(), config.seed(), config.bounds(), config.factions(), rules);
        population.setMax(rules.economy().populationMax());
    }

    /** The factions the computer plays (AI-01). */
    public AiSlots aiSlots() {
        return aiSlots;
    }

    /** Who has lost and whether the match is over (MATCH-01). */
    public Outcome outcome() {
        return outcome;
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

    /** When each unit was last ordered, for its activity tier (UNIT-05): a statistic, not state. */
    public ActivityTiers tiers() {
        return tiers;
    }

    /** This tick's commanders in first person (CAM-07). */
    public Leaders leaders() {
        return leaders;
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
        cacheMobility();
        stats.swap(set);
        costs.swap(stats);
        gatherRates.swap(stats);
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
