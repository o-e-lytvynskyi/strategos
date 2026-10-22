package dev.strategos.core.testkit;

import dev.strategos.core.sim.WorldEffect;
import dev.strategos.core.sim.world.WriteScheduler;
import dev.strategos.core.sim.world.WriteSite;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Voxels that take {@link WorldEffect}s the way the mod's {@code BlockWriter} does (spec §5.13, BLD-15.3): through a
 * {@link WriteScheduler}, with the same budget, bursts and order, so L1 sees which writes the game would make on which
 * tick. Each effect's block state becomes a class through the lookup the test gives; {@code y} counts from {@code
 * baseY}. Every applied write is logged.
 */
public final class FakeWorld {
    private final VoxelWorld voxels;
    private final int baseY;
    private final ToIntFunction<String> classes;
    private final WriteScheduler<WorldEffect> scheduler = new WriteScheduler<>();
    private final List<Applied> log = new ArrayList<>();
    private long tick;

    /** One write as applied: on which tick, from which site. */
    public record Applied(long tick, WriteSite site, WorldEffect effect) {}

    public FakeWorld(VoxelWorld voxels, int baseY, ToIntFunction<String> classes) {
        this.voxels = voxels;
        this.baseY = baseY;
        this.classes = classes;
    }

    /** A match's effect, scheduled at the site it names. */
    public void submit(int match, WorldEffect effect) {
        scheduler.enqueue(new WriteSite(match, effect.site()), effect.priority(), effect);
    }

    /** One tick of writes under {@code budget}; returns the sites that finished. */
    public List<WriteSite> tick(int budget) {
        long now = tick++;
        return scheduler.drain(now, budget, (site, effect) -> {
            voxels.set(effect.x(), effect.y() - baseY, effect.z(), (short) classes.applyAsInt(effect.state()));
            log.add(new Applied(now, site, effect));
        });
    }

    public int pending() {
        return scheduler.pending();
    }

    public List<Applied> log() {
        return List.copyOf(log);
    }

    public VoxelWorld voxels() {
        return voxels;
    }
}
