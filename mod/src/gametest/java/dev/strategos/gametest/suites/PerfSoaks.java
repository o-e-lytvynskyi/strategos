package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.PerfVerdicts.judgeSoak;

import dev.strategos.core.protocol.MatchControl;
import dev.strategos.core.sim.ai.Levels;
import dev.strategos.core.testkit.BattleScene;
import dev.strategos.core.testkit.EconomyScene;
import dev.strategos.core.testkit.FirstLoopScene;
import dev.strategos.core.testkit.MultiplayerScene;
import dev.strategos.core.testkit.MvpScene;
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

    static void multiplayer(GameTestHelper h, boolean stress) {
        Window window = new Window();
        var scale = stress ? MvpScene.Config.STRESS : MvpScene.Config.SOAK;
        var scene = new MultiplayerScene.Config(mvp(h, scale));
        var config = CrowdSoak.Config.multiplayer(
                stress ? "m7_multiplayer_stress" : "m7_multiplayer", scene, stress ? 6_000 : 24_000, stress ? 46 : 45);
        CrowdSoak.start(
                h, config, result -> judgeSoak(h, result, window, stress), window::start, window::sampleForeignLoad);
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
