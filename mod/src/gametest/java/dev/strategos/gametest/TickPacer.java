package dev.strategos.gametest;

import dev.strategos.mod.Strategos;
import java.util.concurrent.locks.LockSupport;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Paces the GameTest server at 20 ticks a second, or a game speed's multiple of it, while a perf test asks for it. The GameTest server never sleeps
 * ({@code GameTestServer#waitUntilNextTick} only runs the task queue), so path workers would get no time between ticks
 * and the main thread would join their jobs unfinished, as spike S7's first run found. After each tick, once its time is
 * stored (MinecraftServer.java:954, before {@code ServerTickEvent.Post} at 958), the pacer waits until 50 ms after the
 * tick began, outside every measured span.
 */
@EventBusSubscriber(modid = Strategos.MOD_ID)
public final class TickPacer {
    private static final long TICK_NANOS = 50_000_000;

    // Gametest code: one GameTest server per JVM, server thread only.
    private static boolean pacing;
    private static long tickStart;
    private static long tickNanos = TICK_NANOS;

    private TickPacer() {}

    /** Turns pacing on or off from the next tick, at 20 ticks a second. */
    public static void pace(boolean on) {
        pace(on, 1);
    }

    /** As {@link #pace(boolean)}, at {@code speed} times 20 ticks a second (MATCH-05's 0.5, 1 and 1.5). */
    public static void pace(boolean on, double speed) {
        pacing = on;
        tickNanos = Math.round(TICK_NANOS / speed);
    }

    @SubscribeEvent
    static void onTickPre(ServerTickEvent.Pre event) {
        tickStart = System.nanoTime();
    }

    @SubscribeEvent
    static void onTickPost(ServerTickEvent.Post event) {
        if (!pacing) {
            return;
        }
        long left;
        while ((left = tickStart + tickNanos - System.nanoTime()) > 0) {
            LockSupport.parkNanos(left);
        }
    }
}
