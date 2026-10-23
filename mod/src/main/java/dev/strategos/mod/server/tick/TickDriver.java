package dev.strategos.mod.server.tick;

import dev.strategos.core.sim.MatchLifecycle;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.match.MatchRuntime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.TickRateManager;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Steps the matches: at the start of each server tick the spawn budget and the block writer's budget refill, and map
 * imports feed the writer (a preparing match's inside the fault barrier); at the start of each level's tick, before its
 * entities and tracker, every RUNNING match of that level steps and every PAUSED one gets its bodies, in id order, each
 * inside the fault barrier, and then the block writer writes that level's share.
 */
public final class TickDriver {
    private TickDriver() {}

    public static void onServerTickPre(ServerTickEvent.Pre event) {
        StrategosServer server = StrategosServer.getOrNull();
        if (server != null) {
            server.betweenTicks().tickStarts();
            server.commanders().tickStarts(server.server()); // before the connections tick (S9)
            server.spawnBudget().reset(server.settings().spawnsPerTick());
            server.writer().startTick();
            for (MatchRuntime rt : server.matches().preparing()) {
                server.faults().run(rt, "import", () -> server.matches().stepPreparing(rt));
            }
            server.mapImports().tick();
        }
    }

    public static void onLevelTickPre(LevelTickEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        StrategosServer server = StrategosServer.getOrNull();
        if (server == null || !shouldStep(level.tickRateManager())) {
            return;
        }
        boolean running = false;
        for (MatchRuntime rt : server.matches().activeIn(level)) {
            running |= rt.lifecycle() == MatchLifecycle.RUNNING;
            Runnable work = rt.lifecycle() == MatchLifecycle.RUNNING
                    ? () -> {
                        rt.tick(level);
                        rt.pages()
                                .tick(
                                        level,
                                        rt.tick(),
                                        server.blockClasses(),
                                        server.materials(),
                                        server.jobs(),
                                        server.writer(),
                                        rt.id().value());
                    }
                    : () -> rt.materialiseOnly(level);
            server.faults().run(rt, "tick", work);
        }
        server.writer().flush(level, level.getServer().getTickCount(), running);
    }

    /** A frozen game (/tick freeze) steps no match; /tick step runs as many steps as it asks for. */
    public static boolean shouldStep(TickRateManager rates) {
        return rates.runsNormally();
    }
}
