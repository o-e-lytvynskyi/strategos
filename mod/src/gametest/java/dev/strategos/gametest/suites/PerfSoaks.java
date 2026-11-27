package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.PerfVerdicts.judgeSoak;

import dev.strategos.core.protocol.MatchControl;
import dev.strategos.core.sim.ai.Levels;
import dev.strategos.core.testkit.BattleScene;
import dev.strategos.core.testkit.EconomyScene;
import dev.strategos.core.testkit.FirstLoopScene;
import dev.strategos.core.testkit.M10Scene;
import dev.strategos.core.testkit.MvpScene;
import dev.strategos.core.testkit.TerritoryScene;
import dev.strategos.gametest.suites.PerfVerdicts.Window;
import dev.strategos.mod.server.content.Scenarios;
import java.util.Objects;
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
    private static final int BATTLE_TICKS = Integer.getInteger("strategos.battle.ticks", 6_000);
    private static final int BATTLE_LANE = 9;
    private static final int BATTLE_STRESS_LANE = 10;
    /** PERF-09: 30 game minutes; {@code -Dstrategos.mvp.ticks} shortens a trial run. */
    private static final int MVP_TICKS = Integer.getInteger("strategos.mvp.ticks", MvpScene.SOAK_TICKS);

    private static final int MVP_STRESS_TICKS = Integer.getInteger("strategos.mvpStress.ticks", 6_000);
    private static final int MVP_LANE = 11;
    private static final int MVP_STRESS_LANE = 12;
    /** PERF-16: 15 game minutes; {@code -Pterritory.ticks} shortens a trial run, {@code territory.stressTicks} the stress's. */
    private static final int TERRITORY_TICKS =
            Integer.getInteger("strategos.territory.ticks", TerritoryScene.SOAK_TICKS);

    private static final int TERRITORY_STRESS_TICKS = Integer.getInteger("strategos.territoryStress.ticks", 6_000);
    private static final int TERRITORY_LANE = 13;
    private static final int TERRITORY_STRESS_LANE = 14;
    /** PERF-17: 15 game minutes; {@code -Pm10.ticks} shortens a trial run, {@code m10.stressTicks} the stress's. */
    private static final int M10_TICKS = Integer.getInteger("strategos.m10.ticks", M10Scene.SOAK_TICKS);

    private static final int M10_STRESS_TICKS = Integer.getInteger("strategos.m10Stress.ticks", 6_000);
    private static final int M10_LANE = 15;
    private static final int M10_STRESS_LANE = 16;

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

    static void battleSoak(GameTestHelper h) {
        Window window = new Window();
        CrowdSoak.start(
                h,
                CrowdSoak.Config.firstLoop("battle_soak", BattleScene.Config.SOAK, BATTLE_TICKS, BATTLE_LANE, 1),
                r -> judgeSoak(h, r, window, false),
                window::start,
                window::sampleForeignLoad);
    }

    static void battleStress(GameTestHelper h) {
        Window window = new Window();
        CrowdSoak.start(
                h,
                CrowdSoak.Config.firstLoop(
                        "battle_stress", BattleScene.Config.STRESS, FIRST_LOOP_SHORT_TICKS, BATTLE_STRESS_LANE, 1),
                r -> judgeSoak(h, r, window, true),
                window::start,
                window::sampleForeignLoad);
    }

    /** The scene at {@code scale}, both factions played by the shipped skirmish script at Hard. */
    private static MvpScene.Config mvp(GameTestHelper h, MvpScene.Config scale) {
        return scale.withAi(Objects.requireNonNull(
                Scenarios.script(h.getLevel().getServer(), Levels.PROFILE), "the shipped ai/skirmish.json"));
    }

    static void mvpSoak(GameTestHelper h) {
        Window window = new Window();
        CrowdSoak.start(
                h,
                CrowdSoak.Config.firstLoop("mvp_soak", mvp(h, MvpScene.Config.SOAK), MVP_TICKS, MVP_LANE, 1),
                r -> judgeSoak(h, r, window, false),
                window::start,
                window::sampleForeignLoad);
    }

    static void mvpStress(GameTestHelper h) {
        Window window = new Window();
        CrowdSoak.start(
                h,
                CrowdSoak.Config.firstLoop(
                        "mvp_stress", mvp(h, MvpScene.Config.STRESS), MVP_STRESS_TICKS, MVP_STRESS_LANE, 1),
                r -> judgeSoak(h, r, window, true),
                window::start,
                window::sampleForeignLoad);
    }

    /** The scene at {@code scale}, its four factions played by the shipped skirmish script at Hard. */
    private static TerritoryScene.Config territory(GameTestHelper h, TerritoryScene.Config scale) {
        return scale.withAi(Objects.requireNonNull(
                Scenarios.script(h.getLevel().getServer(), Levels.PROFILE), "the shipped ai/skirmish.json"));
    }

    static void territorySoak(GameTestHelper h) {
        Window window = new Window();
        CrowdSoak.start(
                h,
                CrowdSoak.Config.firstLoop(
                        "territory_soak", territory(h, TerritoryScene.Config.SOAK), TERRITORY_TICKS, TERRITORY_LANE, 1),
                r -> judgeSoak(h, r, window, false),
                window::start,
                window::sampleForeignLoad);
    }

    static void territoryStress(GameTestHelper h) {
        Window window = new Window();
        CrowdSoak.start(
                h,
                CrowdSoak.Config.firstLoop(
                        "territory_stress",
                        territory(h, TerritoryScene.Config.STRESS),
                        TERRITORY_STRESS_TICKS,
                        TERRITORY_STRESS_LANE,
                        1),
                r -> judgeSoak(h, r, window, true),
                window::start,
                window::sampleForeignLoad);
    }

    /** The M10 scene at {@code scale}, its four factions played by the shipped skirmish script at Hard. */
    private static M10Scene.Config m10(GameTestHelper h, M10Scene.Config scale) {
        return scale.withAi(Objects.requireNonNull(
                Scenarios.script(h.getLevel().getServer(), Levels.PROFILE), "the shipped ai/skirmish.json"));
    }

    static void m10Soak(GameTestHelper h) {
        Window window = new Window();
        CrowdSoak.start(
                h,
                CrowdSoak.Config.firstLoop("m10_soak", m10(h, M10Scene.Config.SOAK), M10_TICKS, M10_LANE, 1),
                r -> judgeSoak(h, r, window, false),
                window::start,
                window::sampleForeignLoad);
    }

    static void m10Stress(GameTestHelper h) {
        Window window = new Window();
        CrowdSoak.start(
                h,
                CrowdSoak.Config.firstLoop(
                        "m10_stress", m10(h, M10Scene.Config.STRESS), M10_STRESS_TICKS, M10_STRESS_LANE, 1),
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
