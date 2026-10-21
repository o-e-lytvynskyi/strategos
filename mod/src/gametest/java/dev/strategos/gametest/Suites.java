package dev.strategos.gametest;

import dev.strategos.gametest.suites.BodyTests;
import dev.strategos.gametest.suites.BuildTests;
import dev.strategos.gametest.suites.CommandTests;
import dev.strategos.gametest.suites.MatchTests;
import dev.strategos.gametest.suites.RetryNoReasonSelfTest;
import dev.strategos.gametest.suites.SelfTests;
import dev.strategos.gametest.suites.SharedBatchSelfTest;
import dev.strategos.gametest.suites.TicketLeakSelfTest;
import java.util.List;

/** The suites one GameTest run holds; gametest code, so static lists are fine here. */
public final class Suites {
    private Suites() {}

    static List<Class<?>> forRun(boolean perf, String selftest) {
        if (perf) {
            throw new IllegalStateException("no perf suite yet: PerfTests arrives with M0 T12");
        }
        return switch (selftest) {
            case "retry" -> List.of(SelfTests.class);
            case "retryNoReason" -> List.of(RetryNoReasonSelfTest.class);
            case "sharedBatch" -> List.of(SharedBatchSelfTest.class);
            case "ticketLeak" -> List.of(TicketLeakSelfTest.class);
            // PersistenceTests join in T9, FaultTests in T10.
            default -> List.of(BuildTests.class, BodyTests.class, MatchTests.class, CommandTests.class);
        };
    }
}
