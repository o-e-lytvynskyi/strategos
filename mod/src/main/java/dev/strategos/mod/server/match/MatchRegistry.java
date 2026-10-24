package dev.strategos.mod.server.match;

import dev.strategos.core.content.MapManifest;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.diag.MatchLog;
import dev.strategos.core.port.JobExecutor;
import dev.strategos.core.port.MetricsSink;
import dev.strategos.core.shared.MatchId;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.sim.CorePorts;
import dev.strategos.core.sim.MatchBounds;
import dev.strategos.core.sim.MatchConfig;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchLifecycle;
import dev.strategos.core.sim.MatchRules;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.econ.EconomyRules;
import dev.strategos.mod.StrategosConfig;
import dev.strategos.mod.StrategosSettings;
import dev.strategos.mod.server.body.SpawnBudget;
import dev.strategos.mod.server.content.MapPackage;
import dev.strategos.mod.server.content.MapPackageLoader;
import dev.strategos.mod.server.persist.DiskJournal;
import dev.strategos.mod.server.persist.MatchData;
import dev.strategos.mod.server.persist.Quarantine;
import dev.strategos.mod.server.persist.RegistryData;
import dev.strategos.mod.server.world.ArenaPolicy;
import dev.strategos.mod.server.world.ArenaTickets;
import dev.strategos.mod.server.world.ArenaWorld;
import dev.strategos.mod.server.world.BlockClassTable;
import dev.strategos.mod.server.world.BlockWriter;
import dev.strategos.mod.server.world.MaterialTable;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The server's matches, in id order: the only way to make, find, pause, drop, unload or restore one. A saved
 * (persistent) match has a record in the registry blob and a blob of its own; both load on server start and save at
 * autosave and on stop. In an arena world a player's match takes the next slot and holds chunk tickets over its space
 * until it is disposed ({@link ArenaPolicy}). Server thread only.
 */
public final class MatchRegistry {
    public static final int MIN_SIZE = 8;
    public static final int MAX_SIZE = 512;
    public static final int MAX_HEIGHT = 128;

    /** Two matches must keep this many blocks apart. */
    public static final int MARGIN = 2;

    private static final Logger LOG = LoggerFactory.getLogger(MatchRegistry.class);

    private final MinecraftServer server;
    private final StrategosSettings settings;
    private final SpawnBudget spawnBudget;
    private final BlockWriter writer;
    private final Supplier<BlockClassTable> blockClasses;
    private final Supplier<MaterialTable> materials;
    private final JobExecutor jobs;
    private final Supplier<TemplateSet> templates; // the server's current set, for new and restored matches
    private final Supplier<EconomyRules> economy; // the data packs' resources, for new matches (ECO-01)
    private final TreeMap<Integer, MatchRuntime> matches = new TreeMap<>();
    private final Map<UUID, MatchId> seated = new HashMap<>(); // who sits where, across the matches' rosters
    private final Map<ResourceKey<Level>, MatchRegionIndex> regions = new HashMap<>();
    private @Nullable RegistryData persisted;
    private int nextId = 1; // ids while no registry blob is usable; such matches are never saved

    private Consumer<UUID> unseated = player -> {};

    public MatchRegistry(
            MinecraftServer server,
            StrategosSettings settings,
            SpawnBudget spawnBudget,
            BlockWriter writer,
            Supplier<BlockClassTable> blockClasses,
            Supplier<MaterialTable> materials,
            JobExecutor jobs,
            Supplier<TemplateSet> templates,
            Supplier<EconomyRules> economy) {
        this.server = server;
        this.settings = settings;
        this.spawnBudget = spawnBudget;
        this.writer = writer;
        this.blockClasses = blockClasses;
        this.materials = materials;
        this.jobs = jobs;
        this.templates = templates;
        this.economy = economy;
    }

    /**
     * A RUNNING match in {@code space}, with the next id; with a map, a PREPARING one that starts once the map is
     * imported and its terrain hash checked ({@link #stepPreparing}). Throws {@link MatchRefusedException} for a size
     * outside 8..512, a Y band taller than 128 or outside the level's build height, a floor outside the band, another
     * match within 2 blocks, a map that is missing or does not fit the space, or a saved match while the registry blob
     * is not usable.
     */
    public MatchRuntime create(ServerLevel level, MatchSpace space, MatchOptions options) {
        if (!space.level().equals(level.dimension())) {
            throw new IllegalArgumentException("space in " + space.level().location() + ", level "
                    + level.dimension().location());
        }
        check(level, space);
        ResourceLocation mapId = options.map();
        MapPackage map = mapId == null ? null : loadMap(mapId, space);
        MatchBounds bounds;
        try {
            bounds = space.toBounds(options.flatFloorY());
        } catch (IllegalArgumentException e) {
            throw new MatchRefusedException(String.valueOf(e.getMessage()));
        }
        RegistryData saved = options.persistent() ? usableRegistry() : null;
        MatchId id = allocateId();
        Long seed = options.seed();
        MatchConfig config = new MatchConfig(
                id,
                seed != null ? seed : level.getRandom().nextLong(),
                bounds,
                options.factions(),
                MatchRules.DEFAULTS.withEconomy(economy.get()));
        MatchCore core = MatchCore.create(config, templates.get(), ports(options.metrics(), id));
        FaultPolicy policy = options.faultPolicy();
        if (policy == null) {
            policy = defaultPolicy();
        }
        MatchData data = null;
        if (saved != null) {
            data = MatchData.live(id, core::snapshot, saved::epoch);
            level.getDataStorage().set(MatchData.name(id), data);
            data.setDirty();
            saved.put(new MatchRecord(
                    id,
                    space,
                    config.seed(),
                    MatchLifecycle.CREATED,
                    options.source(),
                    saved.epoch(),
                    Optional.empty(),
                    Optional.ofNullable(mapId).map(ResourceLocation::toString)));
        }
        MatchRuntime rt =
                new MatchRuntime(id, space, options.source(), policy, saved != null, core, data, spawnBudget, mapId);
        rt.onLifecycleChange(() -> recordChanged(rt));
        rt.transition(MatchLifecycle.PREPARING);
        if (holdsTickets(rt)) {
            ArenaTickets.force(level, id, space);
        }
        register(rt);
        if (map != null) {
            rt.importer(new MapImporter(level, map, space.origin(), id.value(), writer));
            return rt;
        }
        prewarm(level, rt);
        rt.transition(MatchLifecycle.READY);
        rt.transition(MatchLifecycle.RUNNING);
        return rt;
    }

    /** Every page of the match captured now, built and published on its next tick (PATH-15: prewarm at prepare). */
    private void prewarm(ServerLevel level, MatchRuntime rt) {
        rt.pages().prewarm(level, rt.tick() + 1, blockClasses.get(), materials.get(), jobs);
    }

    /** {@code /strategos nav rebuild}: every page captured again now and published on the match's next tick. */
    public void rebuildPages(MatchRuntime rt) {
        ServerLevel level = server.getLevel(rt.space().level());
        if (level == null) {
            throw new MatchRefusedException(rt.id() + "'s level is not loaded");
        }
        prewarm(level, rt);
    }

    /** The matches still importing their map. */
    public List<MatchRuntime> preparing() {
        List<MatchRuntime> list = new ArrayList<>();
        for (MatchRuntime rt : matches.values()) {
            if (rt.lifecycle() == MatchLifecycle.PREPARING && rt.importer() != null) {
                list.add(rt);
            }
        }
        return list;
    }

    /**
     * One tick of a preparing match's import. When it is done, the terrain hash of what stands in the level must be the
     * manifest's: the match then starts RUNNING; otherwise it faults with both hashes.
     */
    public void stepPreparing(MatchRuntime rt) {
        MapImporter importer = rt.importer();
        if (rt.lifecycle() != MatchLifecycle.PREPARING || importer == null || !importer.step()) {
            return;
        }
        rt.imported(importer.hash(), importer.millis());
        rt.importer(null);
        String expected = importer.map().manifest().terrainHash();
        if (!expected.equals(importer.hash())) {
            rt.fault(
                    new IllegalStateException("the imported terrain hashes to " + importer.hash() + ", the manifest of "
                            + rt.map() + " says " + expected),
                    "import");
            return;
        }
        rt.log().info(rt.tick(), "map {} imported in {} ms", String.valueOf(rt.map()), importer.millis());
        ServerLevel level = server.getLevel(rt.space().level());
        if (level != null) {
            prewarm(level, rt);
        }
        rt.transition(MatchLifecycle.READY);
        rt.transition(MatchLifecycle.RUNNING);
    }

    /** An arena match, or one on a map, holds tickets over its whole space until it is disposed. */
    private boolean holdsTickets(MatchRuntime rt) {
        return rt.map() != null || ArenaPolicy.governs(server, rt.space(), rt.source());
    }

    /** The package, checked against the space: the same size, and a band as high as the map. */
    private MapPackage loadMap(ResourceLocation id, MatchSpace space) {
        MapPackage map;
        try {
            map = MapPackageLoader.load(server, id);
        } catch (IllegalArgumentException e) {
            throw new MatchRefusedException(String.valueOf(e.getMessage()));
        }
        MapManifest m = map.manifest();
        int height = space.maxY() - space.minY() + 1;
        if (m.sizeX() != space.sizeX() || m.sizeZ() != space.sizeZ() || m.height() != height) {
            throw new MatchRefusedException("map " + id + " is " + m.sizeX() + " x " + m.sizeZ() + " x " + m.height()
                    + ", the space " + space.sizeX() + " x " + space.sizeZ() + " x " + height);
        }
        return map;
    }

    /**
     * A RUNNING match of {@code sizeX} x {@code sizeZ} in the arena's next slot, saved with the world. Throws {@link
     * MatchRefusedException} outside an arena world and when the registry blob cannot count the slot.
     */
    public MatchRuntime createInArena(int sizeX, int sizeZ, MatchOptions options) {
        if (!ArenaWorld.isArena(server)) {
            throw new MatchRefusedException("this world is not a Strategos arena");
        }
        int slot = usableRegistry().allocateArenaSlot();
        return create(server.overworld(), ArenaPolicy.slotSpace(slot, sizeX, sizeZ), options);
    }

    /**
     * A match on map package {@code map} in the arena's next slot: the map's size, its band from its {@code minY}, its
     * units standing at its first start slot until movement follows the terrain (T9).
     */
    public MatchRuntime createInArena(ResourceLocation map, MatchOptions options) {
        if (!ArenaWorld.isArena(server)) {
            throw new MatchRefusedException("this world is not a Strategos arena");
        }
        MapManifest m;
        try {
            m = MapPackageLoader.load(server, map).manifest();
        } catch (IllegalArgumentException e) {
            throw new MatchRefusedException(String.valueOf(e.getMessage()));
        }
        int slot = usableRegistry().allocateArenaSlot();
        MatchSpace space = ArenaPolicy.slotSpace(slot, m.sizeX(), m.sizeZ(), m.minY(), m.minY() + m.height() - 1);
        double floor =
                m.minY() + (m.startSlots().isEmpty() ? 0 : m.startSlots().get(0).y());
        return create(server.overworld(), space, options.withMap(map, floor));
    }

    /** The registry blob as loaded on server start, quarantined or not; null before. */
    public @Nullable RegistryData persisted() {
        return persisted;
    }

    private void check(ServerLevel level, MatchSpace space) {
        if (space.sizeX() < MIN_SIZE
                || space.sizeX() > MAX_SIZE
                || space.sizeZ() < MIN_SIZE
                || space.sizeZ() > MAX_SIZE) {
            throw new MatchRefusedException(
                    "match size " + space.sizeX() + " x " + space.sizeZ() + " outside " + MIN_SIZE + ".." + MAX_SIZE);
        }
        int height = space.maxY() - space.minY() + 1;
        if (height > MAX_HEIGHT) {
            throw new MatchRefusedException("match Y band " + space.minY() + ".." + space.maxY() + " is " + height
                    + " blocks, more than " + MAX_HEIGHT);
        }
        if (space.minY() < level.getMinBuildHeight() || space.maxY() >= level.getMaxBuildHeight()) {
            throw new MatchRefusedException(
                    "match Y band " + space.minY() + ".." + space.maxY() + " outside the build height "
                            + level.getMinBuildHeight() + ".." + (level.getMaxBuildHeight() - 1));
        }
        MatchId other = regionsOf(space.level()).overlapping(space, MARGIN);
        if (other != null) {
            throw new MatchRefusedException("match space overlaps match " + other + " within " + MARGIN + " blocks");
        }
    }

    public @Nullable MatchRuntime get(MatchId id) {
        return matches.get(id.value());
    }

    /** A saved match's record, or null. */
    public @Nullable MatchRecord record(MatchId id) {
        RegistryData saved = persisted;
        return saved == null ? null : saved.record(id);
    }

    /** Every match, in id order. */
    public List<MatchRuntime> all() {
        return new ArrayList<>(matches.values());
    }

    /** The RUNNING and PAUSED matches of {@code level}, in id order: what the tick driver steps. */
    public List<MatchRuntime> activeIn(ServerLevel level) {
        List<MatchRuntime> active = new ArrayList<>();
        for (MatchRuntime rt : matches.values()) {
            MatchLifecycle lifecycle = rt.lifecycle();
            if ((lifecycle == MatchLifecycle.RUNNING || lifecycle == MatchLifecycle.PAUSED)
                    && rt.space().level().equals(level.dimension())) {
                active.add(rt);
            }
        }
        return active;
    }

    public @Nullable MatchRuntime at(ServerLevel level, BlockPos pos) {
        MatchRegionIndex index = regions.get(level.dimension());
        MatchId id = index == null ? null : index.at(pos);
        return id == null ? null : matches.get(id.value());
    }

    public void pause(MatchId id) {
        require(id).transition(MatchLifecycle.PAUSED);
    }

    public void resume(MatchId id) {
        require(id).transition(MatchLifecycle.RUNNING);
    }

    /** The match is over (MATCH-01): it steps no more and takes no commands; its world stays until it is disposed. */
    public void end(MatchId id) {
        MatchRuntime rt = require(id);
        if (rt.lifecycle().canTransitionTo(MatchLifecycle.ENDED)) {
            rt.transition(MatchLifecycle.ENDED);
        }
    }

    /**
     * Ends the match for good: its bodies are discarded, its region freed, its waiting block writes dropped and its
     * blocks unprotected, its chunk tickets released, its record and blob file removed.
     */
    public void dispose(MatchId id) {
        MatchRuntime rt = require(id);
        writeJournal(rt);
        if (rt.lifecycle().canTransitionTo(MatchLifecycle.ENDED)) {
            rt.transition(MatchLifecycle.ENDED);
        } else if (rt.lifecycle() == MatchLifecycle.PREPARING || rt.lifecycle() == MatchLifecycle.READY) {
            rt.fault(new IllegalStateException("disposed before it started"), "dispose"); // no direct way to DISPOSED
        }
        rt.importer(null);
        rt.transition(MatchLifecycle.DISPOSED);
        drop(rt);
        if (rt.persistent()) {
            forget(rt);
        }
        ServerLevel level = server.getLevel(rt.space().level());
        if (level != null) {
            writer.forget(level, id.value());
            if (holdsTickets(rt) && !settings.leakTickets()) {
                ArenaTickets.release(level, id, rt.space());
            }
        }
    }

    /** Disposes every match of {@code level} made by {@code source}: a GameTest batch's cleanup. */
    public void disposeAll(ServerLevel level, MatchSource source) {
        for (MatchRuntime rt : all()) {
            if (rt.source() == source && rt.space().level().equals(level.dimension())) {
                dispose(rt.id());
            }
        }
    }

    /** A test hook: drops the runtime and its bodies as a server stop does, keeping its record and blob. */
    public void unload(MatchId id) {
        drop(require(id));
    }

    /**
     * A saved match back from its record and blob: PAUSED with every live unit wanted for a body, its journal starting
     * at the restored snapshot; FAULTED with the reason when the blob is quarantined, the record says FAULTED or the
     * snapshot does not restore. A blob whose epoch differs from the record's still loads (the blob wins).
     */
    public MatchRuntime restore(ServerLevel level, MatchRecord record, MatchData data) {
        if (matches.containsKey(record.id().value())) {
            throw new MatchRefusedException(record.id() + " is already loaded");
        }
        level.getDataStorage().set(MatchData.name(record.id()), data);
        MatchLog log = new MatchLog(LOG, record.id());
        MatchCore core = null;
        String reason = null;
        MatchSnapshot snapshot = data.loadedSnapshot();
        if (data.state() == MatchData.State.QUARANTINED) {
            reason = "its saved blob is quarantined: " + data.quarantineReason();
        } else if (record.lifecycle() == MatchLifecycle.FAULTED) {
            reason = record.faultMessage().orElse("it had faulted before the save");
        } else if (record.lifecycle() == MatchLifecycle.PREPARING) {
            reason = "the server stopped while it was importing its map";
        } else if (data.state() != MatchData.State.LOADED || snapshot == null) {
            reason = "its blob is " + data.state();
        } else {
            if (data.loadedEpoch() != record.lastEpoch()) {
                log.warn(
                        snapshot.tick(),
                        "blob epoch {}, record epoch {}: the blob wins",
                        data.loadedEpoch(),
                        record.lastEpoch());
            }
            try {
                core = MatchCore.restore(snapshot, templates.get(), ports(MetricsSink.none(), record.id()));
            } catch (RuntimeException e) {
                reason = "its snapshot does not restore: " + e.getMessage();
            }
        }
        MatchRuntime rt = new MatchRuntime(
                record.id(),
                record.space(),
                record.source(),
                defaultPolicy(),
                true,
                core,
                data,
                spawnBudget,
                record.map().map(ResourceLocation::parse).orElse(null));
        rt.onLifecycleChange(() -> recordChanged(rt));
        if (core == null) {
            rt.restoredFaulted(String.valueOf(reason));
            recordChanged(rt);
            log.error(-1, "restored FAULTED: " + reason, new IllegalStateException(reason));
        } else {
            rt.transition(MatchLifecycle.PREPARING);
            prewarm(level, rt);
            rt.transition(MatchLifecycle.READY);
            rt.transition(MatchLifecycle.RUNNING);
            rt.transition(MatchLifecycle.PAUSED);
            for (int handle : core.queries().unitHandles()) {
                rt.bodies().want(handle);
            }
            RegistryData saved = persisted;
            long lastEpoch = record.lastEpoch();
            data.attach(core::snapshot, saved != null ? saved::epoch : () -> lastEpoch);
        }
        register(rt);
        return rt;
    }

    /** On server start: the registry blob, then every saved match from its own blob, or FAULTED with the reason. */
    public void loadPersisted() {
        RegistryData saved = Quarantine.loadRegistry(server.overworld(), System.currentTimeMillis());
        persisted = saved;
        if (saved.quarantineReason() != null) {
            LOG.error("The match registry is quarantined, so no saved match loads: {}", saved.quarantineReason());
            return;
        }
        for (MatchRecord record : saved.records()) {
            loadSaved(record, saved.epoch());
        }
    }

    /**
     * One saved match from its record and its blob file: restored, or FAULTED with the reason when its level is gone,
     * its file is missing or unreadable (copied to {@code <name>.dat.corrupt-<epoch>}), or its blob is quarantined.
     */
    public MatchRuntime loadSaved(MatchRecord record, long epoch) {
        ServerLevel level = server.getLevel(record.space().level());
        if (level == null) {
            return registerFaulted(record, "its level " + record.space().level().location() + " is gone");
        }
        return switch (Quarantine.loadMatch(level, record.id(), epoch)) {
            case Quarantine.Missing missing ->
                registerFaulted(record, "its blob " + MatchData.name(record.id()) + " is missing");
            case Quarantine.CorruptFile corrupt ->
                registerFaulted(
                        record, corrupt.reason() + "; kept as " + corrupt.copy().getFileName());
            case Quarantine.Loaded loaded -> restore(level, record, loaded.data());
        };
    }

    /** On server stop, before the levels save: a new save epoch, and every live blob and the registry dirty. */
    public void prepareSave() {
        for (MatchRuntime rt : matches.values()) {
            writeJournal(rt);
        }
        RegistryData saved = persisted;
        if (saved == null || saved.quarantineReason() != null) {
            return;
        }
        long epoch = saved.nextEpoch();
        for (MatchRuntime rt : matches.values()) {
            MatchRecord record = saved.record(rt.id());
            MatchData data = rt.data();
            if (!rt.persistent() || record == null) {
                continue;
            }
            boolean live = data != null && data.state() == MatchData.State.LIVE;
            saved.put((live ? record.withLastEpoch(epoch) : record)
                    .withLifecycle(rt.lifecycle(), Optional.ofNullable(rt.faultMessage())));
            if (data != null && live) {
                data.setDirty();
            }
        }
    }

    /** At server stop, after the levels unloaded every body: forgets every match without touching the world. */
    public void shutdown() {
        matches.clear();
        regions.clear();
        persisted = null;
    }

    private MatchRuntime registerFaulted(MatchRecord record, String reason) {
        MatchRuntime rt = new MatchRuntime(
                record.id(),
                record.space(),
                record.source(),
                defaultPolicy(),
                true,
                null,
                null,
                spawnBudget,
                record.map().map(ResourceLocation::parse).orElse(null));
        rt.restoredFaulted(reason);
        rt.onLifecycleChange(() -> recordChanged(rt));
        recordChanged(rt);
        new MatchLog(LOG, record.id()).error(-1, "restored FAULTED: " + reason, new IllegalStateException(reason));
        register(rt);
        return rt;
    }

    /** A saved match's record follows its lifecycle; dispose removes the record itself. */
    private void recordChanged(MatchRuntime rt) {
        RegistryData saved = persisted;
        if (!rt.persistent()
                || saved == null
                || saved.quarantineReason() != null
                || rt.lifecycle() == MatchLifecycle.DISPOSED) {
            return;
        }
        MatchRecord record = saved.record(rt.id());
        if (record != null) {
            saved.put(record.withLifecycle(rt.lifecycle(), Optional.ofNullable(rt.faultMessage())));
        }
    }

    /** A disposed saved match: its blob never saves again, its file goes, its record goes. */
    private void forget(MatchRuntime rt) {
        MatchData data = rt.data();
        if (data != null) {
            data.dispose();
        }
        ServerLevel level = server.getLevel(rt.space().level());
        if (level != null) {
            try {
                Files.deleteIfExists(Quarantine.dataFile(level, MatchData.name(rt.id())));
            } catch (IOException e) {
                new MatchLog(LOG, rt.id()).error(rt.tick(), "cannot delete the blob of the disposed match", e);
            }
        }
        RegistryData saved = persisted;
        if (saved != null && saved.quarantineReason() == null) {
            saved.remove(rt.id());
        }
    }

    /** The core's ports: the server's clock and path jobs, and the journal setting's sink. */
    private CorePorts ports(MetricsSink metrics, MatchId id) {
        return new CorePorts(metrics, System::nanoTime, jobs, DiskJournal.sink(settings.journal(), server, id));
    }

    /** With {@code journal = disk}, the match's journal is written once more when it is disposed or saved. */
    private void writeJournal(MatchRuntime rt) {
        MatchCore core = rt.core();
        if (settings.journal() == StrategosConfig.Journal.DISK && core != null) {
            DiskJournal.writeNow(server, rt.id(), core);
        }
    }

    private RegistryData usableRegistry() {
        RegistryData saved = persisted;
        if (saved == null) {
            throw new MatchRefusedException("saved matches are not loaded yet");
        }
        if (saved.quarantineReason() != null) {
            throw new MatchRefusedException("saved matches are off: " + saved.quarantineReason());
        }
        return saved;
    }

    private MatchId allocateId() {
        RegistryData saved = persisted;
        if (saved != null && saved.quarantineReason() == null) {
            return saved.allocateId();
        }
        return new MatchId(nextId++);
    }

    private FaultPolicy defaultPolicy() {
        return settings.faultsRethrow() ? FaultPolicy.RETHROW : FaultPolicy.CONTAIN;
    }

    private void register(MatchRuntime rt) {
        matches.put(rt.id().value(), rt);
        regionsOf(rt.space().level()).add(rt.id(), rt.space());
        for (UUID player : rt.roster().members()) {
            seated.put(player, rt.id());
        }
    }

    private void drop(MatchRuntime rt) {
        rt.bodies().discardAll();
        matches.remove(rt.id().value());
        regionsOf(rt.space().level()).remove(rt.id());
        for (UUID player : rt.roster().members()) {
            if (seated.remove(player, rt.id())) {
                unseated.accept(player);
            }
        }
    }

    /** Called with each player a match's end unseats, for its commander's state to come back (CAM-03). */
    public void onUnseated(Consumer<UUID> hook) {
        this.unseated = hook;
    }

    /** Where a player sits: its match and slot. */
    public record Seat(MatchRuntime match, PlayerSlot slot) {}

    /**
     * Seats {@code player} in {@code rt} at {@code slot}, out of any match it sat in; a saved match's blob is marked to
     * save it. Refused for a slot the match has no faction for, or one someone else holds.
     */
    public void join(MatchRuntime rt, UUID player, PlayerSlot slot) {
        MatchCore core = rt.core();
        if (core == null) {
            throw new MatchRefusedException("match " + rt.id() + " is " + rt.lifecycle() + " and seats no one");
        }
        int factions = core.queries().config().factions();
        if (slot.value() >= factions) {
            throw new MatchRefusedException(
                    "match " + rt.id() + " has " + factions + " factions, so no slot " + slot.value());
        }
        rt.roster().seat(player, slot); // refused before anything changes if the slot is taken
        MatchId before = seated.put(player, rt.id());
        if (before != null && !before.equals(rt.id())) {
            MatchRuntime old = matches.get(before.value());
            if (old != null) {
                old.roster().unseat(player);
                markRoster(old);
            }
        }
        markRoster(rt);
    }

    public @Nullable Seat seatOf(UUID player) {
        MatchId id = seated.get(player);
        MatchRuntime rt = id == null ? null : matches.get(id.value());
        PlayerSlot slot = rt == null ? null : rt.roster().slotOf(player);
        return rt == null || slot == null ? null : new Seat(rt, slot);
    }

    /** The id of the match {@code player} sits in, or 0: what the fog gate asks for every pairing. */
    public int matchOf(UUID player) {
        MatchId id = seated.get(player);
        return id == null ? 0 : id.value();
    }

    private static void markRoster(MatchRuntime rt) {
        MatchData data = rt.data();
        if (data != null && data.state() == MatchData.State.LIVE) {
            data.setDirty();
        }
    }

    private MatchRuntime require(MatchId id) {
        MatchRuntime rt = matches.get(id.value());
        if (rt == null) {
            throw new MatchRefusedException("no match " + id);
        }
        return rt;
    }

    private MatchRegionIndex regionsOf(ResourceKey<Level> level) {
        return regions.computeIfAbsent(level, k -> new MatchRegionIndex());
    }
}
