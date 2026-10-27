package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;

import dev.strategos.gametest.StrategosTest;
import dev.strategos.gametest.TickPacer;
import net.minecraft.Util;
import net.minecraft.gametest.framework.GameTestHelper;

/** The perf pacer must give vanilla the same tick budget as the real requested server rate. */
public final class PacingTests {
    private PacingTests() {}

    @StrategosTest(batch = "pacing", arena = "arena_flat_48", timeoutTicks = 80)
    public static void engineClockMatchesPacingAndRestores(GameTestHelper h) {
        float original = h.getLevel().getServer().tickRateManager().tickrate();
        h.startSequence()
                .thenExecute(() -> TickPacer.pace(true, 1.5))
                .thenIdle(4)
                .thenExecute(() -> {
                    var server = h.getLevel().getServer();
                    check(server.tickRateManager().tickrate() == 30, "the engine still uses its old tick rate");
                    long ahead = server.getNextTickTime() - Util.getNanos();
                    check(ahead <= 34_000_000, "vanilla's deadline is " + ahead + " ns ahead, beyond one 30 TPS tick");
                    TickPacer.pace(false);
                })
                .thenIdle(2)
                .thenExecute(() -> check(
                        h.getLevel().getServer().tickRateManager().tickrate() == original,
                        "the original engine tick rate was not restored"))
                .thenSucceed();
    }
}
