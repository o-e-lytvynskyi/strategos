package dev.strategos.gametest;

import dev.strategos.mod.Strategos;
import java.lang.reflect.Field;
import java.util.concurrent.locks.LockSupport;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Real server pacing for measurements. GameTestServer omits vanilla's normal wait, so its internal deadlines can
 * drift far ahead during setup. Align both vanilla work budgets and the engine rate with the requested pace;
 * otherwise a 30 TPS test can spend a 20 TPS budget on optional chunk work. Waiting in Post stays outside MSPT.
 */
@EventBusSubscriber(modid = Strategos.MOD_ID)
public final class TickPacer {
    private static final long TICK_NANOS = 50_000_000;
    private static final Field NEXT_TICK = field("nextTickTimeNanos");
    private static final Field DELAYED_TASKS = field("delayedTasksMaxNextTickTimeNanos");
    private static boolean pacing;
    private static boolean rateApplied;
    private static float previousRate;
    private static float targetRate = 20;
    private static long tickStart;
    private static long tickNanos = TICK_NANOS;

    private TickPacer() {}

    /** Turns pacing on or off from the next tick, at 20 ticks a second. */
    public static void pace(boolean on) {
        pace(on, 1);
    }

    /** As {@link #pace(boolean)}, at the requested game speed. */
    public static void pace(boolean on, double speed) {
        if (!Double.isFinite(speed) || speed <= 0) {
            throw new IllegalArgumentException("a finite positive game speed is required");
        }
        pacing = on;
        targetRate = (float) (20 * speed);
        tickNanos = Math.round(TICK_NANOS / speed);
    }

    @SubscribeEvent
    static void onTickPre(ServerTickEvent.Pre event) {
        tickStart = Util.getNanos();
        MinecraftServer server = event.getServer();
        if (!pacing) {
            if (rateApplied) {
                server.tickRateManager().setTickRate(previousRate);
                rateApplied = false;
            }
            return;
        }
        if (!rateApplied) {
            previousRate = server.tickRateManager().tickrate();
            rateApplied = true;
        }
        if (server.tickRateManager().tickrate() != targetRate) {
            server.tickRateManager().setTickRate(targetRate);
        }
        try {
            NEXT_TICK.setLong(server, tickStart + tickNanos);
            DELAYED_TASKS.setLong(server, tickStart + tickNanos);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot align the GameTest server clock", e);
        }
    }

    @SubscribeEvent
    static void onTickPost(ServerTickEvent.Post event) {
        if (!pacing) {
            return;
        }
        long left;
        while ((left = tickStart + tickNanos - Util.getNanos()) > 0) {
            LockSupport.parkNanos(left);
        }
    }

    /** Dev-only reflection, like RerunTracking; fail loudly if the verified 1.21.1 fields change. */
    private static Field field(String name) {
        try {
            Field field = MinecraftServer.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalStateException("Minecraft server clock field is unavailable: " + name, e);
        }
    }
}
