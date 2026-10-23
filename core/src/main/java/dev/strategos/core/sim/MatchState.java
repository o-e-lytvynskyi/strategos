package dev.strategos.core.sim;

import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.CorePaths;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.diag.MatchLog;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.combat.Damage;
import dev.strategos.core.sim.combat.HealthStore;
import dev.strategos.core.sim.econ.Amount;
import dev.strategos.core.sim.econ.CarryStore;
import dev.strategos.core.sim.econ.Costs;
import dev.strategos.core.sim.econ.EconomyRules;
import dev.strategos.core.sim.econ.Forage;
import dev.strategos.core.sim.econ.GatherRates;
import dev.strategos.core.sim.econ.Ledger;
import dev.strategos.core.sim.econ.NodeDiscovery;
import dev.strategos.core.sim.econ.NodeIndex;
import dev.strategos.core.sim.econ.NodeStore;
import dev.strategos.core.sim.econ.Treasury;
import dev.strategos.core.sim.nav.BlockClass;
import dev.strategos.core.sim.nav.FootprintOverlay;
import dev.strategos.core.sim.nav.Material;
import dev.strategos.core.sim.nav.MaterialKind;
import dev.strategos.core.sim.nav.MobilityClass;
import dev.strategos.core.sim.nav.PageBuilder;
import dev.strategos.core.sim.nav.PageCoord;
import dev.strategos.core.sim.nav.PathStore;
import dev.strategos.core.sim.nav.SliceMaterials;
import dev.strategos.core.sim.nav.SurfaceGrid;
import dev.strategos.core.sim.nav.SurfacePage;
import dev.strategos.core.sim.nav.TerrainEdits;
import dev.strategos.core.sim.nav.VoxelSlice;
import dev.strategos.core.sim.order.OrderQueues;
import dev.strategos.core.sim.spatial.SpatialQueries;
import dev.strategos.core.sim.stats.StatService;
import dev.strategos.core.sim.world.WritePriority;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
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
    private final GatherRates gatherRates;
    private final CarryStore carry = new CarryStore();
    private final Ledger ledger;
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

    private final byte[][] sliceBytes; // each published page's last captured slice, for the core's own rebuilds
    private final TerrainEdits terrainEdits;
    private final FootprintOverlay footprints;
    private final BitSet rebuild;
    private final PageBuilder rebuilder = new PageBuilder(MobilityClass.INFANTRY);
    private final NodeStore nodes = new NodeStore();
    private final BuildingStore buildings = new BuildingStore();
    private @Nullable NodeIndex nodeIndex;

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
        this.gatherRates = new GatherRates(economy);
        gatherRates.swap(stats);
        this.ledger = new Ledger(economy.size());
        this.spatial = new SpatialQueries(config.bounds(), templates);
        this.input = TickInputs.empty(0);
        this.surfaces =
                SurfaceGrid.empty(config.bounds().sizeX(), config.bounds().sizeZ(), MobilityClass.INFANTRY);
        this.pageSlices = new long[surfaces.pagesX() * surfaces.pagesZ()];
        this.published = new BitSet(pageSlices.length);
        this.known = new BitSet(pageSlices.length);
        this.sliceBytes = new byte[pageSlices.length][];
        this.terrainEdits = new TerrainEdits(pageSlices.length);
        this.footprints = new FootprintOverlay(surfaces.pagesX(), surfaces.pagesZ());
        this.rebuild = new BitSet(pageSlices.length);
        ensureCapacity(entities.highWater());
    }

    /** The published terrain: a copy-on-write grid, so a job keeps the one it captured. */
    public SurfaceGrid surfaces() {
        return surfaces;
    }

    /**
     * Publishes a rebuilt page; a page outside the match's grid is refused. A page whose slice differs from the one
     * last known there counts as changed this tick: routes across it are asked for again (PATH-15). The core's own
     * edits and footprints are laid over the slice (M3 spec §1.6 row 5): those up to the capture's sample tick are in
     * it already and are forgotten, the rest are applied by rebuilding the page here.
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
            sliceBytes[index] = page.slice();
            if (page.sampleTick() >= 0) {
                terrainEdits.dropUpTo(index, page.sampleTick());
                losses(index, page.coord());
            }
            if (terrainEdits.count(index) > 0 || footprints.any(index)) {
                built.add(rebuilt(index, page.coord()));
                rebuild.clear(index);
            } else {
                built.add(page.page());
            }
        }
        surfaces = surfaces.publishAll(built);
        if (!nodes.discovered() && published.cardinality() == pageSlices.length) {
            discoverNodes();
        }
    }

    /**
     * ECO-02: the match's nodes, found once every page of its space is published, from the slices' materials (M3 spec
     * §1.6 rows 1–2); restored ones are kept.
     */
    private void discoverNodes() {
        MatchBounds b = config.bounds();
        NodeDiscovery.discover(
                nodes,
                config.rules().economy(),
                surfaces.pagesX(),
                surfaces.pagesZ(),
                b.minY(),
                i -> sliceBytes[i] == null ? null : VoxelSlice.decode(sliceBytes[i]));
        nodeIndex = null;
        for (int id = 1; id <= nodes.count(); id++) {
            ledger.found(nodes.resource(id), nodes.amount(id));
        }
        Forage.showAll(this);
    }

    /**
     * The materials in the box (x0..x1, y0..y1, z0..z1), match-local x and z, absolute y, as the core now sees its
     * terrain: each page's last slice, less every voxel the core has edited since (ECO-03). Positions are packed by
     * {@link NodeStore#pack}; the result is in position order.
     */
    public List<long[]> materialsIn(int x0, int y0, int z0, int x1, int y1, int z1, MaterialKind kind) {
        List<long[]> out = new ArrayList<>(); // packed position, material amount
        MatchBounds b = config.bounds();
        for (int pz = Math.max(0, z0 >> 4); pz <= Math.min(surfaces.pagesZ() - 1, z1 >> 4); pz++) {
            for (int px = Math.max(0, x0 >> 4); px <= Math.min(surfaces.pagesX() - 1, x1 >> 4); px++) {
                int page = pz * surfaces.pagesX() + px;
                if (sliceBytes[page] == null) {
                    continue;
                }
                VoxelSlice slice = VoxelSlice.decode(sliceBytes[page]);
                SliceMaterials m = slice.materials();
                int height = slice.height();
                for (int k = 0; k < m.size(); k++) {
                    if (m.material(k).kind() != kind) {
                        continue;
                    }
                    int column = m.voxel(k) / height;
                    int band = m.voxel(k) % height;
                    int x = px * 16 + column / 16;
                    int z = pz * 16 + column % 16;
                    int y = b.minY() + band;
                    if (x < x0 || x > x1 || z < z0 || z > z1 || y < y0 || y > y1) {
                        continue;
                    }
                    int edited = terrainEdits.classAt(page, TerrainEdits.cell(column / 16, band, column % 16));
                    if (edited >= 0 && BlockClass.is((short) edited, BlockClass.PASSABLE)) {
                        continue; // the core took it away
                    }
                    out.add(new long[] {NodeStore.pack(x, y, z), m.material(k).amount()});
                }
            }
        }
        out.sort((a, c) -> Long.compare(a[0], c[0]));
        return out;
    }

    /**
     * The material at match-local (x, z) and absolute height y as the core now sees its terrain, or null: none, or one
     * the core has taken away.
     */
    public @Nullable Material materialAt(int x, int y, int z) {
        MatchBounds b = config.bounds();
        if (x < 0 || z < 0 || x >= b.sizeX() || z >= b.sizeZ()) {
            return null;
        }
        int page = (z >> 4) * surfaces.pagesX() + (x >> 4);
        if (sliceBytes[page] == null) {
            return null;
        }
        VoxelSlice slice = VoxelSlice.decode(sliceBytes[page]);
        int band = y - b.minY();
        if (band < 0 || band >= slice.height()) {
            return null;
        }
        int edited = terrainEdits.classAt(page, TerrainEdits.cell(x & 15, band, z & 15));
        if (edited >= 0 && BlockClass.is((short) edited, BlockClass.PASSABLE)) {
            return null;
        }
        return slice.materials().at(((x & 15) * 16 + (z & 15)) * slice.height() + band);
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
        int path = stats.path(CorePaths.HEALTH);
        double v =
                path < 0 ? Double.NaN : stats.blockValue(buildings.template(handle), buildings.faction(handle), path);
        double full = Double.isFinite(v) && v > 0 ? v : CorePaths.DEFAULT_HEALTH;
        if (buildings.state(handle) == BuildingStore.COMPLETE) {
            return full;
        }
        return full * Math.max(1, buildings.progress(handle)) / BuildingStore.FULL;
    }

    /**
     * A finished building at once (BLD-01's instant placement, for scenarios and tests): its footprint's columns are
     * blocked for units, and the writer is asked to clear its volume and place its blocks in build order. Its handle.
     */
    public int placeComplete(int template, int faction, Footprint at, int baseY) {
        int handle = buildings.create(template, faction, at, baseY, BuildingStore.COMPLETE, BuildingStore.FULL);
        Blueprint b = blueprintOf(handle);
        for (int cell : at.columns(b)) {
            blockColumn(cell >>> 16, cell & 0xFFFF);
        }
        int site = Handle.index(handle);
        boolean[] filled = new boolean[b.sizeX() * b.sizeY() * b.sizeZ()];
        for (int k = 0; k < b.count(); k++) {
            filled[(b.x(k) * b.sizeY() + b.y(k)) * b.sizeZ() + b.z(k)] = true;
        }
        for (int x = 0; x < b.sizeX(); x++) {
            for (int z = 0; z < b.sizeZ(); z++) {
                if (!b.covers(x, z)) {
                    continue;
                }
                for (int y = 0; y < b.sizeY(); y++) {
                    if (!filled[(x * b.sizeY() + y) * b.sizeZ() + z]) {
                        outputs.add(new WorldEffect(
                                site,
                                WritePriority.CONSTRUCTION,
                                at.worldX(x, z),
                                baseY + y,
                                at.worldZ(x, z),
                                WorldEffect.AIR,
                                0));
                    }
                }
            }
        }
        for (int k = 0; k < b.count(); k++) {
            outputs.add(new WorldEffect(
                    site,
                    WritePriority.CONSTRUCTION,
                    at.worldX(b.x(k), b.z(k)),
                    baseY + b.y(k),
                    at.worldZ(b.x(k), b.z(k)),
                    b.palette().get(b.state(k)),
                    handle));
        }
        return handle;
    }

    /**
     * Building {@code handle} falls (BLD-01: 0 hit points): its blocks above its floor layer go, the floor stays as a
     * foundation; its columns are released, and the core's terrain gets the air the writer will leave.
     */
    public void destroyBuilding(int handle) {
        Blueprint b = blueprintOf(handle);
        Footprint at = footprintOf(handle);
        int baseY = buildings.baseY(handle);
        int site = Handle.index(handle);
        for (int k = b.count() - 1; k >= 0; k--) { // top-down, the reverse of the build order
            if (b.y(k) == 0) {
                continue;
            }
            int x = at.worldX(b.x(k), b.z(k));
            int z = at.worldZ(b.x(k), b.z(k));
            outputs.add(new WorldEffect(site, WritePriority.DAMAGE_STAGE, x, baseY + b.y(k), z, WorldEffect.AIR, 0));
            editVoxel(x, baseY + b.y(k), z, BlockClass.AIR);
        }
        for (int cell : at.columns(b)) {
            releaseColumn(cell >>> 16, cell & 0xFFFF);
        }
        buildings.remove(handle);
    }

    /** Where the nodes are, built on first use after discovery or a restore. */
    public NodeIndex nodeIndex() {
        NodeIndex index = nodeIndex;
        if (index == null) {
            index = new NodeIndex(nodes, config.rules().economy(), surfaces.pagesX(), surfaces.pagesZ());
            nodeIndex = index;
        }
        return index;
    }

    /**
     * A capture after an edit someone else made (04 §11.4): a node block it no longer shows, that the core still counts
     * and did not take itself, went without the core, and the node loses it, crediting nobody.
     */
    private void losses(int page, PageCoord coord) {
        if (!nodes.discovered() || nodes.count() == 0) {
            return;
        }
        VoxelSlice slice = VoxelSlice.decode(sliceBytes[page]);
        int minY = config.bounds().minY();
        for (int id = 1; id <= nodes.count(); id++) {
            long[] blocks = nodes.blocks(id);
            for (int k = nodes.gone(id); k < blocks.length; k++) {
                int x = NodeStore.x(blocks[k]);
                int z = NodeStore.z(blocks[k]);
                int band = NodeStore.y(blocks[k]) - minY;
                if (x >> 4 != coord.x() || z >> 4 != coord.z() || band < 0 || band >= slice.height()) {
                    continue;
                }
                int column = (x & 15) * 16 + (z & 15);
                boolean shown = slice.materials().at(column * slice.height() + band) != null;
                boolean ours = terrainEdits.classAt(page, TerrainEdits.cell(x & 15, band, z & 15)) >= 0;
                if (!shown && !ours) {
                    long lost = nodes.lose(id, blocks[k]);
                    if (lost > 0) {
                        ledger.lost(nodes.resource(id), lost);
                    }
                }
            }
        }
    }

    /** Page {@code index}'s surfaces from its last slice, the core's edits and its footprints. */
    private SurfacePage rebuilt(int index, PageCoord coord) {
        VoxelSlice slice = terrainEdits.applyTo(index, VoxelSlice.decode(sliceBytes[index]));
        return rebuilder.build(slice, footprints.of(coord), coord);
    }

    /**
     * The core sets a voxel itself, at match-local column (x, z) and absolute height y: a log it removed, a footprint
     * it cleared. Its page is rebuilt in this tick's WORLD phase; the writer makes the block follow.
     */
    public void editVoxel(int x, int y, int z, short cls) {
        MatchBounds b = config.bounds();
        int band = y - b.minY();
        if (x < 0 || z < 0 || x >= b.sizeX() || z >= b.sizeZ() || band < 0 || band >= VoxelSlice.MAX_HEIGHT) {
            throw new IllegalArgumentException("voxel (" + x + ", " + y + ", " + z + ") outside the match space");
        }
        int index = pageIndex(PageCoord.of(x, z));
        terrainEdits.add(index, TerrainEdits.cell(x & 15, band, z & 15), cls, tick);
        rebuild.set(index);
    }

    /** A snapshot's buildings, their footprints blocked again (the writer's blocks are the world's already). */
    void restoreBuildings(List<BuildingStore.Saved> saved) {
        buildings.restore(saved);
        for (int handle : buildings.handles()) {
            if (buildings.state(handle) != BuildingStore.FOUNDATION) {
                for (int cell : footprintOf(handle).columns(blueprintOf(handle))) {
                    footprints.block(cell >>> 16, cell & 0xFFFF);
                }
            }
        }
    }

    /** Whether a building's footprint blocks match-local column (x, z). */
    public boolean footprintAt(int x, int z) {
        return footprints.blocked(x, z);
    }

    /** Blocks a footprint column (BLD-01); its page is rebuilt in this tick's WORLD phase. */
    public void blockColumn(int x, int z) {
        footprints.block(x, z);
        rebuild.set(footprints.pageOf(x, z));
    }

    /** Releases a footprint column; its page is rebuilt in this tick's WORLD phase. */
    public void releaseColumn(int x, int z) {
        footprints.release(x, z);
        rebuild.set(footprints.pageOf(x, z));
    }

    /**
     * WORLD: rebuilds every page the core edited this tick, from its last slice, and publishes them as changed. A page
     * not captured yet gets its edits when it is first published.
     */
    public void rebuildEdited() {
        if (rebuild.isEmpty()) {
            return;
        }
        List<SurfacePage> built = new ArrayList<>();
        for (int i = rebuild.nextSetBit(0); i >= 0; i = rebuild.nextSetBit(i + 1)) {
            if (sliceBytes[i] == null) {
                continue;
            }
            built.add(rebuilt(i, new PageCoord(i % surfaces.pagesX(), i / surfaces.pagesX())));
            if (changedCount == changed.length) {
                changed = Arrays.copyOf(changed, changedCount * 2);
            }
            changed[changedCount++] = i;
            paths.pageChanged(tick);
        }
        rebuild.clear();
        if (!built.isEmpty()) {
            surfaces = surfaces.publishAll(built);
        }
    }

    /** The core's own terrain edits (M3 spec §1.6 row 5). */
    public TerrainEdits terrainEdits() {
        return terrainEdits;
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
        carry.ensureCapacity(size);
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
        int i = Handle.index(handle);
        health.reset(i);
        releaseClaim(i, handle);
        dropLoad(i);
        entities.destroy(handle);
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
        for (int handle : buildings.handles()) {
            for (int slot = 0; slot < buildings.slotCount(handle); slot++) {
                int unit = buildings.claim(handle, slot);
                if (unit != 0 && entities.isAlive(unit)) {
                    carry.setSite(Handle.index(unit), handle);
                }
            }
        }
        for (int id = 1; id <= nodes.count(); id++) {
            for (int slot = 0; slot < nodes.slotCount(id); slot++) {
                int handle = nodes.claim(id, slot);
                if (handle != 0 && entities.isAlive(handle)) {
                    carry.setClaimed(Handle.index(handle), id);
                }
            }
        }
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
        if (ledger.empty() && carry.empty()) {
            return null;
        }
        for (int r = 0; r < ledger.resources(); r++) {
            long gathered = 0;
            for (int f = 0; f < treasury.factions(); f++) {
                gathered += treasury.gathered(f, r);
            }
            String broken = ledger.broken(r, nodes.remaining(r), carry.total(r, entities.highWater()), gathered);
            if (broken != null) {
                return broken;
            }
        }
        return null;
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
