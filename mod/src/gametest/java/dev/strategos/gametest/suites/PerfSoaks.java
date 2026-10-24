package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.PerfVerdicts.judgeSoak;

import dev.strategos.core.protocol.MatchControl;
import dev.strategos.core.testkit.EconomyScene;
import dev.strategos.core.testkit.FirstLoopScene;
import dev.strategos.gametest.suites.PerfVerdicts.Window;
import net.minecraft.gametest.framework.GameTestHelper;

/** Long-running performance scenes, behind PerfTests' stable annotated entry points. */
final class PerfSoaks {
    private static final int SOAK_TICKS = Integer.getInteger("strategos.soak.ticks", 12_000);
    private static final int STRESS_TICKS = 2_400;
    private static final int SOAK_LANE = 1;
    private static final int STRESS_LANE = 2;
    private static final int MELEE_LANE = 3;
    private static final int MELEE_TICKS = 6_000;
    private static final int FIRST_LOOP_TICKS = Integer.getInteger("strategos.firstLoop.ticks", 18_000);
    private static final int FIRST_LOOP_SHORT_TICKS = 6_000;
    private static final int FIRST_LOOP_LANE = 4;
    private static final int FIRST_LOOP_STRESS_LANE = 5;
    private static final int FIRST_LOOP_FAST_LANE = 6;
    private static final int ECONOMY_TICKS = Integer.getInteger("strategos.economy.ticks", 18_000);
    private static final int ECONOMY_LANE = 7;
    private static final int ECONOMY_STRESS_LANE = 8;

    private PerfSoaks() {}

    static void crowdSoak600(GameTestHelper h) {
        Window window = new Window();
        CrowdSoak.start(
                h,
                new CrowdSoak.Config("crowd_soak_600", 0, 300, SOAK_TICKS, true, 2, SOAK_LANE),
                r -> judgeSoak(h, r, window, false),
                window::start,
                window::sampleForeignLoad);
    }

    static void stress1200(GameTestHelper h) {
        Window window = new Window();
        CrowdSoak.start(
                h,
                new CrowdSoak.Config("stress_1200", 1, 600, STRESS_TICKS, true, 0, STRESS_LANE),
                r -> judgeSoak(h, r, window, true),
                window::start,
                window::sampleForeignLoad);
    }

    static void meleeSoak200(GameTestHelper h) {
        Window window = new Window();
        CrowdSoak.start(
                h,
                new CrowdSoak.Config("melee_soak_200", 0, 100, MELEE_TICKS, true, 2, MELEE_LANE, true),
                r -> judgeSoak(h, r, window, false),
                window::start,
                window::sampleForeignLoad);
    }

    static void firstLoopSoak(GameTestHelper h) {
        Window window = new Window();
        CrowdSoak.start(
                h,
                CrowdSoak.Config.firstLoop(
                        "first_loop_soak", FirstLoopScene.Config.SOAK, FIRST_LOOP_TICKS, FIRST_LOOP_LANE, 1),
                r -> judgeSoak(h, r, window, false),
                window::start,
                window::sampleForeignLoad);
    }

    static void firstLoopStress(GameTestHelper h) {
        Window window = new Window();
        CrowdSoak.start(
                h,
                CrowdSoak.Config.firstLoop(
                        "first_loop_stress",
                        FirstLoopScene.Config.STRESS,
                        FIRST_LOOP_SHORT_TICKS,
                        FIRST_LOOP_STRESS_LANE,
                        1),
                r -> judgeSoak(h, r, window, true),
                window::start,
                window::sampleForeignLoad);
    }

    static void economySoak(GameTestHelper h) {
        Window window = new Window();
        CrowdSoak.start(
                h,
                CrowdSoak.Config.firstLoop("economy_soak", EconomyScene.Config.SOAK, ECONOMY_TICKS, ECONOMY_LANE, 1),
                r -> judgeSoak(h, r, window, false),
                window::start,
                window::sampleForeignLoad);
    }

    static void economyStress(GameTestHelper h) {
        Window window = new Window();
        CrowdSoak.start(
                h,
                CrowdSoak.Config.firstLoop(
                        "economy_stress", EconomyScene.Config.STRESS, FIRST_LOOP_SHORT_TICKS, ECONOMY_STRESS_LANE, 1),
                r -> judgeSoak(h, r, window, true),
                window::start,
                window::sampleForeignLoad);
    }

    static void firstLoopFast(GameTestHelper h) {
        Window window = new Window();
        CrowdSoak.start(
                h,
                CrowdSoak.Config.firstLoop(
                        "first_loop_fast",
                        FirstLoopScene.Config.SOAK,
                        FIRST_LOOP_SHORT_TICKS,
                        FIRST_LOOP_FAST_LANE,
                        MatchControl.FAST),
                r -> judgeSoak(h, r, window, false),
                window::start,
                window::sampleForeignLoad);
    }
}
