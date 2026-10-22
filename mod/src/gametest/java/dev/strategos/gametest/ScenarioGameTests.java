package dev.strategos.gametest;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.Hex64;
import dev.strategos.core.sim.MatchConfig;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.journal.JournalExport;
import dev.strategos.core.testkit.JournalFiles;
import dev.strategos.core.testkit.Scenario;
import dev.strategos.core.testkit.ScenarioReport;
import dev.strategos.core.testkit.Scenarios;
import dev.strategos.core.testkit.Seeds;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.mod.server.match.MatchRuntime;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.level.block.Rotation;

/**
 * The shared scenarios as GameTests (M0 spec 7.4): each runs in its own arena, and on its first done tick it verifies,
 * checks that its L1 twin (a {@code SimHarness} on the live match's config) stops at the same tick with the same hash,
 * and exports its journal for {@code :core:replayJournals}.
 */
public final class ScenarioGameTests {
    /** {@code isolation.match_00} to {@code _49}: 50 patrol matches side by side in one batch (CORE-01.1). */
    static final int ISOLATION_UNITS = 10;

    static final int ISOLATION_TICKS = 1_000;

    private ScenarioGameTests() {}

    /** One scenario GameTest: its name, batch and timeout, and a maker of fresh scenarios (one per run). */
    record ScenarioTest(String name, String batch, int timeoutTicks, Supplier<Scenario> scenario) {}

    /** Scenarios.all() in batch default; the 20 skeleton seeds in batch skeleton; the 50 isolation seeds. */
    static List<ScenarioTest> tests() {
        List<ScenarioTest> tests = new ArrayList<>();
        for (Scenario scenario : Scenarios.all()) {
            String id = scenario.id();
            tests.add(new ScenarioTest("scenarios." + id, "default", scenario.timeoutTicks() + 20, () -> fresh(id)));
        }
        long[] skeleton = Seeds.set("skeleton-20").toArray();
        for (int i = 0; i < skeleton.length; i++) {
            long seed = skeleton[i];
            tests.add(new ScenarioTest(
                    String.format(Locale.ROOT, "scenarios.walking_skeleton_seed_%02d", i),
                    "skeleton",
                    220,
                    () -> Scenarios.walkingSkeleton(seed)));
        }
        long[] isolation = Seeds.set("isolation-50").toArray();
        for (int i = 0; i < isolation.length; i++) {
            long seed = isolation[i];
            tests.add(new ScenarioTest(
                    String.format(Locale.ROOT, "isolation.match_%02d", i),
                    "isolation",
                    ISOLATION_TICKS + 100,
                    () -> Scenarios.patrol(seed, ISOLATION_UNITS, 32, 32, ISOLATION_TICKS)));
        }
        return tests;
    }

    static TestFunction function(ScenarioTest test) {
        return new TestFunction(
                test.batch(),
                test.name(),
                "strategos:arena_flat_32",
                Rotation.NONE,
                test.timeoutTicks(),
                0,
                true,
                false,
                1,
                1,
                false,
                helper -> run(helper, test.name(), test.scenario()));
    }

    private static Scenario fresh(String id) {
        for (Scenario scenario : Scenarios.all()) {
            if (scenario.id().equals(id)) {
                return scenario;
            }
        }
        throw new IllegalStateException("no scenario " + id);
    }

    /** Runs the scenario on a match over the whole arena; on the first tick it is done, verifies and checks the twin. */
    static void run(GameTestHelper h, String name, Supplier<Scenario> make) {
        Scenario scenario = make.get();
        MatchRuntime rt = MatchFixture.create(h, MatchFixture.options(scenario.seed(), h));
        if (rt.space().sizeX() != scenario.sizeX() || rt.space().sizeZ() != scenario.sizeZ()) {
            throw new GameTestAssertException(name + ": the arena is "
                    + rt.space().sizeX() + " x " + rt.space().sizeZ() + ", the scenario needs " + scenario.sizeX()
                    + " x " + scenario.sizeZ());
        }
        RuntimeScenarioContext ctx = new RuntimeScenarioContext(rt);
        scenario.setup(ctx);
        boolean[] done = {false};
        h.onEachTick(() -> {
            if (done[0]) {
                return;
            }
            try {
                scenario.onTick(ctx);
                if (!scenario.isDone(ctx)) {
                    return;
                }
                done[0] = true;
                scenario.verify(ctx);
            } catch (AssertionError e) { // ScenarioFailure: an Error, which the GameTest runner would not catch
                throw new GameTestAssertException(name + " at tick " + ctx.tick() + ": " + e.getMessage());
            }
            MatchCore core = rt.core();
            if (core == null) {
                throw new GameTestAssertException(name + ": the match lost its core");
            }
            checkTwin(name, core, make.get(), ctx.tick(), ctx.stateHash());
            exportJournal(name, core, ctx.tick(), ctx.stateHash());
            h.succeed();
        });
    }

    /** The L1 twin: a harness on the live config (the match id aside, which the hash leaves out), played alone. */
    private static void checkTwin(String name, MatchCore core, Scenario twin, long tick, long hash) {
        MatchConfig live = core.queries().config();
        ScenarioReport report = SimHarness.builder()
                .bounds(live.bounds())
                .seed(live.seed())
                .factions(live.factions())
                .rules(live.rules())
                .templates(TemplateSet.builtin())
                .build()
                .play(twin);
        if (!report.passed() || report.ticks() != tick || report.finalHash() != hash) {
            throw new GameTestAssertException(name + ": the L1 twin stopped at tick " + report.ticks() + " with hash "
                    + Hex64.format(report.finalHash()) + (report.passed() ? "" : " (" + report.failure() + ")")
                    + ", the world at tick " + tick + " with " + Hex64.format(hash));
        }
    }

    /** {@code <journals>/<test name>.strj}, for {@code :core:replayJournals}; runs without the directory skip it. */
    private static void exportJournal(String name, MatchCore core, long tick, long hash) {
        String dir = System.getProperty("strategos.gametest.journals");
        if (dir != null && !dir.isBlank()) {
            JournalFiles.write(
                    Path.of(dir, name + JournalFiles.EXTENSION), new JournalExport(core.journal(), tick, hash));
        }
    }
}
