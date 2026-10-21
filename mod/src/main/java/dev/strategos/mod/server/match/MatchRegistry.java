package dev.strategos.mod.server.match;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.MatchId;
import dev.strategos.core.sim.CorePorts;
import dev.strategos.core.sim.MatchBounds;
import dev.strategos.core.sim.MatchConfig;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchLifecycle;
import dev.strategos.core.sim.MatchRules;
import dev.strategos.mod.StrategosSettings;
import dev.strategos.mod.server.body.SpawnBudget;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** The server's matches, in id order: the only way to make, find, pause, drop or unload one. Server thread only. */
public final class MatchRegistry {
    public static final int MIN_SIZE = 8;
    public static final int MAX_SIZE = 512;
    public static final int MAX_HEIGHT = 128;

    /** Two matches must keep this many blocks apart. */
    public static final int MARGIN = 2;

    private final StrategosSettings settings;
    private final SpawnBudget spawnBudget;
    private final TreeMap<Integer, MatchRuntime> matches = new TreeMap<>();
    private final Map<ResourceKey<Level>, MatchRegionIndex> regions = new HashMap<>();
    private int nextId = 1;

    public MatchRegistry(StrategosSettings settings, SpawnBudget spawnBudget) {
        this.settings = settings;
        this.spawnBudget = spawnBudget;
    }

    /**
     * A RUNNING match in {@code space}, with the next id. Throws {@link MatchRefusedException} for a size outside 8..512, a
     * Y band taller than 128 or outside the level's build height, a floor outside the band, or another match within
     * 2 blocks.
     */
    public MatchRuntime create(ServerLevel level, MatchSpace space, MatchOptions options) {
        if (options.persistent()) {
            throw new UnsupportedOperationException("persistent matches arrive with M0 T9");
        }
        if (!space.level().equals(level.dimension())) {
            throw new IllegalArgumentException("space in " + space.level().location() + ", level "
                    + level.dimension().location());
        }
        check(level, space);
        MatchBounds bounds;
        try {
            bounds = space.toBounds(options.flatFloorY());
        } catch (IllegalArgumentException e) {
            throw new MatchRefusedException(String.valueOf(e.getMessage()));
        }
        MatchId id = new MatchId(nextId++);
        Long seed = options.seed();
        MatchConfig config = new MatchConfig(
                id,
                seed != null ? seed : level.getRandom().nextLong(),
                bounds,
                options.factions(),
                MatchRules.DEFAULTS);
        MatchCore core =
                MatchCore.create(config, TemplateSet.builtin(), new CorePorts(options.metrics(), System::nanoTime));
        FaultPolicy policy = options.faultPolicy();
        if (policy == null) {
            policy = settings.faultsRethrow() ? FaultPolicy.RETHROW : FaultPolicy.CONTAIN;
        }
        MatchRuntime rt = new MatchRuntime(id, space, options.source(), policy, false, core, spawnBudget);
        rt.transition(MatchLifecycle.PREPARING);
        rt.transition(MatchLifecycle.READY);
        rt.transition(MatchLifecycle.RUNNING);
        matches.put(id.value(), rt);
        regionsOf(space.level()).add(id, space);
        return rt;
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

    /** Ends the match for good: its bodies are discarded and its region freed. */
    public void dispose(MatchId id) {
        MatchRuntime rt = require(id);
        if (rt.lifecycle().canTransitionTo(MatchLifecycle.ENDED)) {
            rt.transition(MatchLifecycle.ENDED);
        }
        rt.transition(MatchLifecycle.DISPOSED);
        drop(rt);
    }

    /** Disposes every match of {@code level} made by {@code source}: a GameTest batch's cleanup. */
    public void disposeAll(ServerLevel level, MatchSource source) {
        for (MatchRuntime rt : all()) {
            if (rt.source() == source && rt.space().level().equals(level.dimension())) {
                dispose(rt.id());
            }
        }
    }

    /** A test hook: drops the runtime and its bodies as a server stop does; T9 keeps its record and blob. */
    public void unload(MatchId id) {
        drop(require(id));
    }

    /** At server stop, after the levels unloaded every body: forgets every match without touching the world. */
    public void shutdown() {
        matches.clear();
        regions.clear();
    }

    private void drop(MatchRuntime rt) {
        rt.bodies().discardAll();
        matches.remove(rt.id().value());
        regionsOf(rt.space().level()).remove(rt.id());
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
