package dev.strategos.gametest;

import dev.strategos.gametest.suites.AbilityTests;
import dev.strategos.gametest.suites.AiSoak;
import dev.strategos.gametest.suites.AiTests;
import dev.strategos.gametest.suites.ArenaTests;
import dev.strategos.gametest.suites.AvoidTests;
import dev.strategos.gametest.suites.BodyTests;
import dev.strategos.gametest.suites.BuildTests;
import dev.strategos.gametest.suites.BuildingTests;
import dev.strategos.gametest.suites.CameraTests;
import dev.strategos.gametest.suites.CaptureTests;
import dev.strategos.gametest.suites.CivTests;
import dev.strategos.gametest.suites.CombatTests;
import dev.strategos.gametest.suites.CommandTests;
import dev.strategos.gametest.suites.ConstructionTests;
import dev.strategos.gametest.suites.ContentTests;
import dev.strategos.gametest.suites.DebugTests;
import dev.strategos.gametest.suites.EcoTests;
import dev.strategos.gametest.suites.FaultTests;
import dev.strategos.gametest.suites.FidelityTests;
import dev.strategos.gametest.suites.FirstPersonTests;
import dev.strategos.gametest.suites.FogTests;
import dev.strategos.gametest.suites.FormationTests;
import dev.strategos.gametest.suites.GarrisonTests;
import dev.strategos.gametest.suites.HealTests;
import dev.strategos.gametest.suites.JournalTests;
import dev.strategos.gametest.suites.LootTests;
import dev.strategos.gametest.suites.MapTests;
import dev.strategos.gametest.suites.MatchTests;
import dev.strategos.gametest.suites.NavTests;
import dev.strategos.gametest.suites.NetTests;
import dev.strategos.gametest.suites.OrderTests;
import dev.strategos.gametest.suites.PacingTests;
import dev.strategos.gametest.suites.PathTests;
import dev.strategos.gametest.suites.PerfTests;
import dev.strategos.gametest.suites.PersistenceTests;
import dev.strategos.gametest.suites.PhaseTests;
import dev.strategos.gametest.suites.ProdTests;
import dev.strategos.gametest.suites.ProtectionTests;
import dev.strategos.gametest.suites.ReplicationTests;
import dev.strategos.gametest.suites.RetryNoReasonSelfTest;
import dev.strategos.gametest.suites.SelfTests;
import dev.strategos.gametest.suites.SharedBatchSelfTest;
import dev.strategos.gametest.suites.SkirmishTests;
import dev.strategos.gametest.suites.TechTests;
import dev.strategos.gametest.suites.TerritoryTests;
import dev.strategos.gametest.suites.TicketLeakSelfTest;
import dev.strategos.gametest.suites.TowerTests;
import dev.strategos.gametest.suites.WallTests;
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
                        AbilityTests.class,
                        AiSoak.class,
                        AiTests.class,
                        ArenaTests.class,
                        AvoidTests.class,
                        BuildTests.class,
                        BuildingTests.class,
                        BodyTests.class,
                        CameraTests.class,
                        CaptureTests.class,
                        ReplicationTests.class,
                        CivTests.class,
                        CombatTests.class,
                        FirstPersonTests.class,
                        FormationTests.class,
                        GarrisonTests.class,
                        HealTests.class,
                        LootTests.class,
                        MatchTests.class,
                        CommandTests.class,
                        ContentTests.class,
                        ConstructionTests.class,
                        TerritoryTests.class,
                        TowerTests.class,
                        ProdTests.class,
                        DebugTests.class,
                        EcoTests.class,
                        PersistenceTests.class,
                        FaultTests.class,
                        FogTests.class,
                        FidelityTests.class,
                        JournalTests.class,
                        MapTests.class,
                        NavTests.class,
                        NetTests.class,
                        OrderTests.class,
                        PathTests.class,
                        PhaseTests.class,
                        PacingTests.class,
                        ProtectionTests.class,
                        SkirmishTests.class,
                        TechTests.class,
                        WallTests.class,
                        WriterTests.class);
        };
    }
}
