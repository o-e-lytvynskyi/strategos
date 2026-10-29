package dev.strategos.gametest;

import dev.strategos.gametest.suites.ForeignLoadSampler;
import dev.strategos.mod.Strategos;
import java.nio.file.Path;
import net.minecraft.gametest.framework.GlobalTestReporter;
import net.minecraft.gametest.framework.LogTestReporter;
import net.minecraft.gametest.framework.StructureUtils;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;

/** Registers the GameTests and, on the GameTest server, the JUnit XML report. */
@EventBusSubscriber(modid = Strategos.MOD_ID) // FML 4.0.45 infers the mod bus for RegisterGameTestsEvent
public final class GameTestBootstrap {
    private GameTestBootstrap() {}

    @SubscribeEvent
    static void onRegisterGameTests(RegisterGameTestsEvent event) {
        String dir = System.getProperty("strategos.gametest.structures");
        if (dir != null) {
            StructureUtils.testStructuresDir = dir; // SNBT arenas straight from git
        }
        String report = System.getProperty("strategos.gametest.report");
        if (report != null && GameTestHooks.isGametestServer()) {
            GlobalTestReporter.replaceWith(
                    new TeeReporter(new LogTestReporter(), new JUnitXmlReporter(Path.of(report))));
        }
        if (System.getProperty("strategos.gametest.filter", "").startsWith("^perftests")) {
            // readings exist from the start of the batch, and the first window waits for a preflight of them: the
            // main thread, before the server thread exists (Main posts this event first; no watchdog on this path)
            ForeignLoadSampler.start();
            ForeignLoadSampler.preflight(Long.getLong("strategos.perf.preflightSeconds", 60));
        }
        event.register(StrategosGameTests.class); // the generator and the @AfterBatch hooks
    }

    /** Mod bus, inferred like the above: map-scale tests force chunks only through this controller. */
    @SubscribeEvent
    static void onRegisterTickets(RegisterTicketControllersEvent event) {
        if (GameTestHooks.isGametestServer()) {
            event.register(GameTestTickets.CONTROLLER);
        }
    }
}
