package dev.strategos.gametest;

import dev.strategos.gametest.suites.ArenaTests;
import dev.strategos.gametest.suites.AvoidTests;
import dev.strategos.gametest.suites.BodyTests;
import dev.strategos.gametest.suites.BuildTests;
import dev.strategos.gametest.suites.CommandTests;
import dev.strategos.gametest.suites.DebugTests;
import dev.strategos.gametest.suites.FaultTests;
import dev.strategos.gametest.suites.FidelityTests;
import dev.strategos.gametest.suites.JournalTests;
import dev.strategos.gametest.suites.MapTests;
import dev.strategos.gametest.suites.MatchTests;
import dev.strategos.gametest.suites.NavTests;
import dev.strategos.gametest.suites.NetTests;
import dev.strategos.gametest.suites.OrderTests;
import dev.strategos.gametest.suites.PathTests;
import dev.strategos.gametest.suites.PerfTests;
import dev.strategos.gametest.suites.PersistenceTests;
import dev.strategos.gametest.suites.RetryNoReasonSelfTest;
import dev.strategos.gametest.suites.SelfTests;
import dev.strategos.gametest.suites.SharedBatchSelfTest;
import dev.strategos.gametest.suites.TicketLeakSelfTest;
import dev.strategos.gametest.suites.WriterTests;
import java.util.List;

/** The suites one GameTest run holds; gametest code, so static lists are fine here. */
public final class Suites {
    private Suites() {}

    static List<Class<?>> forRun(boolean perf, String selftest) {
        if (perf) {
            return List.of(PerfTests.class);
        }
        return switch (selftest) {
            case "retry" -> List.of(SelfTests.class);
            case "retryNoReason" -> List.of(RetryNoReasonSelfTest.class);
            case "sharedBatch" -> List.of(SharedBatchSelfTest.class);
            case "ticketLeak" -> List.of(TicketLeakSelfTest.class);
            default ->
                List.of(
                        ArenaTests.class,
                        AvoidTests.class,
                        BuildTests.class,
                        BodyTests.class,
                        MatchTests.class,
                        CommandTests.class,
                        DebugTests.class,
                        PersistenceTests.class,
                        FaultTests.class,
                        FidelityTests.class,
                        JournalTests.class,
                        MapTests.class,
                        NavTests.class,
                        NetTests.class,
                        OrderTests.class,
                        PathTests.class,
                        WriterTests.class);
        };
    }
}
