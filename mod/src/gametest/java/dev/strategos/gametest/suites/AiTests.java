package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;

import dev.strategos.core.client.hud.RaidBar;
import dev.strategos.core.content.MapManifest;
import dev.strategos.core.content.Scenario;
import dev.strategos.core.content.StartSlot;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.AnnounceWave;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.MatchNews;
import dev.strategos.core.protocol.SetAi;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchLifecycle;
import dev.strategos.core.sim.PlayRules;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.WaveAnnounced;
import dev.strategos.core.sim.command.ScenarioSetup;
import dev.strategos.core.sim.journal.ReplayDriver;
import dev.strategos.core.sim.journal.ReplayResult;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.gametest.MatchFixture;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.gametest.net.MockViewer;
import dev.strategos.mod.platform.Ids;
import dev.strategos.mod.server.content.MapPackageLoader;
import dev.strategos.mod.server.content.Scenarios;
import dev.strategos.mod.server.match.MatchOptions;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.match.MatchSpace;
import dev.strategos.mod.server.net.payload.MatchEventPayload;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.world.phys.Vec3;

/**
 * The computer players in a real level (AI-17, QA-11, AI-02): two seeds of bot against bot and one of the economy
 * script, whose journals replay in-process to the live hash; and an announced wave that reaches the other faction's
 * player.
 */
public final class AiTests {
    static final TemplateId CENTRE = new TemplateId("strategos:building/civic_centre");

    private static final int WAVES_LANE = 17;

    private AiTests() {}

    /** The computer sees through fog (no map starts to march on in a GameTest): AI-17's tests are not about fog. */
    private static final PlayRules AI_SEES = new PlayRules(PlayRules.Victory.NONE, false, true, true);

    /** Both factions' centres on the 48 arena, five workers and 300 food each. */
    static MatchRuntime town(GameTestHelper h, long seed) {
        return town(h, seed, MatchOptions.GAMETEST_PLAY);
    }

    private static MatchRuntime town(GameTestHelper h, long seed, PlayRules play) {
        MatchRuntime rt = MatchFixture.create(h, MatchFixture.options(seed, h).withPlay(play));
        rt.submit(PlayerSlot.ADMIN, new SpawnBuilding(CENTRE, new FactionSlot(0), 2, 2, 0));
        rt.submit(PlayerSlot.ADMIN, new SpawnBuilding(CENTRE, new FactionSlot(1), 34, 34, 0));
        for (int f = 0; f < 2; f++) {
            rt.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(f), "food", 300));
            rt.submit(
                    PlayerSlot.ADMIN,
                    new SpawnUnits(
                            TemplateSet.WORKER,
                            new FactionSlot(f),
                            new Vec2d(f == 0 ? 18 : 30, f == 0 ? 18 : 30),
                            5,
                            2));
        }
        return rt;
    }

    /** QA-11.3: a seed of bot against bot plays 1,200 ticks, both commanding, and its journal replays to its hash. */
    static void replays(GameTestHelper h, long seed) {
        MatchRuntime rt = town(h, seed);
        String script = Objects.requireNonNull(Scenarios.script(h.getLevel().getServer(), "first_loop"));
        for (int f = 0; f < 2; f++) {
            rt.submit(PlayerSlot.ADMIN, new SetAi(new FactionSlot(f), "first_loop", script));
        }
        int[] commands = new int[2];
        rt.addListener(out -> out.events().forEach(e -> {
            if (e instanceof CommandResult r
                    && !r.issuer().isAdmin()
                    && r.issuer().value() < 2) {
                commands[r.issuer().value()]++;
            }
        }));
        h.startSequence()
                .thenWaitUntil(() -> check(
                        core(rt).queries().tick() >= 1_200,
                        "at tick " + core(rt).queries().tick()))
                .thenExecute(() -> {
                    check(commands[0] > 0 && commands[1] > 0, "commands judged: " + commands[0] + ", " + commands[1]);
                    var core = core(rt);
                    ReplayResult replay =
                            ReplayDriver.replay(core.journal(), core.queries().templates());
                    check(replay.matches(), "the replay diverged: " + replay);
                    check(replay.finalHash() == core.stateHash(), "the replay ends at another hash");
                })
                .thenSucceed();
    }

    /**
     * AI-02.5: a seed of the economy script on both sides, with wood and stone to build: each places a building on a site it
     * found in the real level's ground, and the match's journal replays to its hash; the journal's L1 replay is the
     * replay suite's.
     */
    @StrategosTest(arena = "arena_flat_48", batch = "ai_economy", solo = true, timeoutTicks = 1_600)
    public static void economySeedReplaysToItsHash(GameTestHelper h) {
        MatchRuntime rt = town(h, 3);
        String script = Objects.requireNonNull(Scenarios.script(h.getLevel().getServer(), "economy"));
        for (int f = 0; f < 2; f++) {
            rt.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(f), "wood", 400));
            rt.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(f), "stone", 200));
            rt.submit(PlayerSlot.ADMIN, new SetAi(new FactionSlot(f), "economy", script));
        }
        int[] placed = new int[2];
        rt.addListener(out -> out.events().forEach(e -> {
            if (e instanceof CommandResult r
                    && !r.issuer().isAdmin()
                    && r.issuer().value() < 2
                    && r.kind().equals("strategos:place_building")
                    && r.accepted() > 0) {
                placed[r.issuer().value()]++;
            }
        }));
        h.startSequence()
                .thenWaitUntil(() -> check(
                        core(rt).queries().tick() >= 1_200,
                        "at tick " + core(rt).queries().tick()))
                .thenExecute(() -> {
                    check(placed[0] > 0 && placed[1] > 0, "buildings placed: " + placed[0] + ", " + placed[1]);
                    var core = core(rt);
                    ReplayResult replay =
                            ReplayDriver.replay(core.journal(), core.queries().templates());
                    check(replay.matches(), "the replay diverged: " + replay);
                    check(replay.finalHash() == core.stateHash(), "the replay ends at another hash");
                })
                .thenSucceed();
    }

    /**
     * AI-03.4: a short seeded battle of the battle bot against itself, each side's six spearmen sent as a wave at 20 s
     * with the shipped tactics knobs: both attack, and the match's journal replays to the live hash (the bots' own
     * commands are not journaled; a replay thinks them again).
     */
    @StrategosTest(arena = "arena_flat_48", batch = "ai_battle", solo = true, timeoutTicks = 1_700)
    public static void battleSeedReplaysToItsHash(GameTestHelper h) {
        MatchRuntime rt = town(h, 5, AI_SEES);
        String script = Objects.requireNonNull(Scenarios.script(h.getLevel().getServer(), "battle"))
                .replace("\"first\":450", "\"first\":1")
                .replace("\"min\":14", "\"min\":4")
                .replace("\"muster\":24", "\"muster\":12")
                .replace("\"launch\":0.6", "\"launch\":0.4") // six spearmen against six and a centre
                .replace("\"spears\":6", "\"spears\":0"); // the shipped reserve of six would be the whole army
        for (int f = 0; f < 2; f++) {
            rt.submit(
                    PlayerSlot.ADMIN,
                    new SpawnUnits(
                            TemplateSet.INFANTRY,
                            new FactionSlot(f),
                            new Vec2d(f == 0 ? 12 : 26, f == 0 ? 12 : 26),
                            6,
                            2));
            rt.submit(PlayerSlot.ADMIN, new SetAi(new FactionSlot(f), "battle", script));
        }
        int[] attacks = new int[2];
        rt.addListener(out -> out.events().forEach(e -> {
            if (e instanceof CommandResult r
                    && !r.issuer().isAdmin()
                    && r.issuer().value() < 2
                    && r.kind().startsWith("strategos:attack")
                    && r.accepted() > 0) {
                attacks[r.issuer().value()]++;
            }
        }));
        h.startSequence()
                .thenWaitUntil(() -> check(
                        core(rt).queries().tick() >= 1_500,
                        "at tick " + core(rt).queries().tick()))
                .thenExecute(() -> {
                    check(attacks[0] > 0 && attacks[1] > 0, "attacks accepted: " + attacks[0] + ", " + attacks[1]);
                    var core = core(rt);
                    ReplayResult replay =
                            ReplayDriver.replay(core.journal(), core.queries().templates());
                    check(replay.matches(), "the replay diverged: " + replay);
                    check(replay.finalHash() == core.stateHash(), "the replay ends at another hash");
                })
                .thenSucceed();
    }

    @StrategosTest(arena = "arena_flat_48", batch = "ai_seed_1", solo = true, timeoutTicks = 1_600)
    public static void seedOneReplaysToItsHash(GameTestHelper h) {
        replays(h, 1);
    }

    @StrategosTest(arena = "arena_flat_48", batch = "ai_seed_2", solo = true, timeoutTicks = 1_600)
    public static void seedTwoReplaysToItsHash(GameTestHelper h) {
        replays(h, 2);
    }

    /** AI-17.3: faction 1's computer announces a wave; faction 0's player hears it, 30 s at least before it leaves. */
    @StrategosTest(arena = "arena_flat_48", timeoutTicks = 400)
    public static void theWaveAnnouncementReachesTheOpponents(GameTestHelper h) {
        MatchRuntime rt = town(h, 1, AI_SEES);
        rt.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.INFANTRY, new FactionSlot(1), new Vec2d(28, 28), 2, 1));
        String quick = """
                {"maxWorkers":0,"ratio":{},"build":[],"housingMargin":0,
                 "attack":{"first":5,"every":600,"min":1,"muster":8,"announce":30},"sites":[]}""";
        rt.submit(PlayerSlot.ADMIN, new SetAi(new FactionSlot(1), "quick", quick.replace("\n", "")));
        MockViewer viewer = BuildingTests.seated(h, rt, "strategos-wave", 0);
        h.startSequence()
                .thenWaitUntil(() -> {
                    viewer.poll();
                    List<MatchNews> waves = new ArrayList<>();
                    for (var p : viewer.capture().of(ClientboundCustomPayloadPacket.class)) {
                        if (p.payload() instanceof MatchEventPayload m
                                && m.news().kind() == MatchNews.WAVE) {
                            waves.add(m.news());
                        }
                    }
                    check(!waves.isEmpty(), "no wave heard yet");
                    MatchNews w = waves.get(0);
                    check(w.faction() == 1, "the wave of " + w.faction());
                    check(w.ticks() - w.tick() >= 600, "only " + (w.ticks() - w.tick()) + " ticks of warning");
                })
                .thenExecute(viewer::close)
                .thenSucceed();
    }

    /** A bot that only waves: soldiers it has, a wave at 5 s of at least one, announced 30 s ahead. */
    private static final String WAVER = """
            {"maxWorkers":0,"ratio":{},"build":[],"housingMargin":0,
             "attack":{"first":5,"every":3000,"min":1,"muster":8,"announce":30},"sites":[]}""".replace("\n", "");

    /**
     * AI-04.5: the raid bar's news (the WAVE {@code match_event}) reaches every participant of the match whose wave it
     * is and nobody else: a viewer in another match and one in no match are sent none; the bar the news draws
     * ({@link RaidBar}) is there for the opponent and none for the faction whose wave it is.
     */
    @StrategosTest(arena = "arena_flat_48", timeoutTicks = 500)
    public static void theRaidBarReachesOnlyItsAudience(GameTestHelper h) {
        MatchRuntime a = MatchFixture.create(
                h,
                0,
                0,
                30,
                30,
                MatchFixture.options(SimHarness.DEFAULT_SEED, h).withPlay(AI_SEES));
        MatchRuntime b = MatchFixture.create(h, 32, 0, 14, 30, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        a.submit(PlayerSlot.ADMIN, new SpawnBuilding(CENTRE, new FactionSlot(0), 2, 2, 0));
        a.submit(PlayerSlot.ADMIN, new SpawnBuilding(CENTRE, new FactionSlot(1), 17, 17, 0));
        a.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.INFANTRY, new FactionSlot(1), new Vec2d(14, 14), 2, 1));
        a.submit(PlayerSlot.ADMIN, new SetAi(new FactionSlot(1), "waver", WAVER));
        MockViewer opponent = BuildingTests.seated(h, a, "strat-opponent", 0);
        MockViewer owner = BuildingTests.seated(h, a, "strat-owner", 1);
        Vec3 at = h.absoluteVec(new Vec3(9.5, 2, 12.5));
        MockViewer elsewhere = MockViewer.join(h.getLevel(), "strat-elsewhere", at.x, at.y, at.z, 4);
        MockViewer outsider = MockViewer.join(h.getLevel(), "strat-outsider", at.x, at.y, at.z, 4);
        NetFixtures.seat(b, elsewhere, 0);
        var seen = new Object() {
            int ticks;
        };
        h.onEachTick(() -> {
            for (MockViewer v : List.of(opponent, owner, elsewhere, outsider)) {
                v.poll();
            }
            List<MatchNews> opponents = waves(opponent, a);
            if (opponents.isEmpty()) {
                return;
            }
            if (seen.ticks++ < 10) {
                return; // let the other viewers' packets arrive
            }
            MatchNews wave = opponents.get(0);
            check(!waves(owner, a).isEmpty(), "the faction's own player is a participant and is sent the news");
            check(waves(elsewhere, a).isEmpty() && waves(outsider, a).isEmpty(), "the news left the match");
            check(
                    RaidBar.of(wave, 0, wave.tick()) != null && RaidBar.of(wave, 1, wave.tick()) == null,
                    "the bar is the opponent's, not the owner's");
            elsewhere.close();
            outsider.close();
            opponent.close();
            owner.close();
            h.succeed();
        });
    }

    private static List<MatchNews> waves(MockViewer viewer, MatchRuntime rt) {
        List<MatchNews> out = new ArrayList<>();
        for (MatchEventPayload p : NetFixtures.payloads(viewer, MatchEventPayload.class)) {
            if (p.news().kind() == MatchNews.WAVE
                    && p.news().matchId() == rt.id().value()) {
                out.add(p.news());
            }
        }
        return out;
    }

    /**
     * AI-04.4: on the first skirmish map, a wave of faction 0's soldiers musters at a camp at least 40 blocks out, leaves
     * when announced and walks to the other faction's centre, coming within 30 blocks of it with no ladder rung above
     * the repath.
     */
    @StrategosTest(batch = "ai_waves", solo = true, timeoutTicks = 40_000)
    public static void wavesReachTheBase(GameTestHelper h) {
        MapManifest m = MapPackageLoader.load(h.getLevel().getServer(), MapTests.FIRST_SKIRMISH)
                .manifest();
        BlockPos corner = h.absolutePos(BlockPos.ZERO);
        BlockPos origin = new BlockPos(corner.getX() + WAVES_LANE * 2_048, corner.getY(), corner.getZ());
        MatchSpace space = new MatchSpace(
                h.getLevel().dimension(), origin, m.sizeX(), m.sizeZ(), origin.getY(), origin.getY() + m.height() - 1);
        MatchRuntime rt = MatchFixture.create(
                h,
                space,
                MatchFixture.options(SimHarness.DEFAULT_SEED, h)
                        .withPlay(AI_SEES)
                        .withMap(
                                MapTests.FIRST_SKIRMISH,
                                origin.getY() + m.startSlots().get(0).y()));
        var state = new Object() {
            boolean started;
            long announced = -1;
            long leaves;
            double camp;
        };
        rt.addListener(out -> out.events().forEach(e -> {
            if (e instanceof WaveAnnounced w && w.faction().value() == 0 && state.announced < 0) {
                state.announced = out.tick();
                state.leaves = w.leaves();
                StartSlot from = m.startSlots().get(0);
                state.camp = Math.hypot(w.x() - from.x(), w.z() - from.z());
            }
        }));
        h.onEachTick(() -> {
            if (rt.lifecycle() == MatchLifecycle.PREPARING) {
                return;
            }
            check(
                    rt.lifecycle() == MatchLifecycle.RUNNING,
                    "the match is " + rt.lifecycle() + ": " + rt.faultMessage());
            MatchCore core = core(rt);
            StartSlot west = m.startSlots().get(0);
            StartSlot east = m.startSlots().get(1);
            if (!state.started) {
                state.started = true;
                Scenario scenario = Scenarios.load(h.getLevel().getServer(), Ids.id("first_skirmish"));
                List<Scenario.Slot> slots = new ArrayList<>();
                for (int f = 0; f < scenario.slots().size(); f++) {
                    Scenario.Slot s = scenario.slots().get(f);
                    slots.add(new Scenario.Slot(
                            f == 0 ? ScenarioSetup.AI + "waver" : "human", s.resources(), s.entities()));
                }
                for (Command c : ScenarioSetup.commands(
                        new Scenario(scenario.map(), scenario.victory(), slots),
                        m.startSlots(),
                        core.queries().templates(),
                        p -> WAVER)) {
                    rt.submit(PlayerSlot.ADMIN, c);
                }
                rt.submit(
                        PlayerSlot.ADMIN,
                        new SpawnUnits(
                                TemplateSet.INFANTRY,
                                new FactionSlot(0),
                                new Vec2d(west.x() + 0.5, west.z() + 12.5),
                                6,
                                2));
                return;
            }
            long[] rungs = core.queries().ladderRungs();
            for (int rung = 2; rung < rungs.length; rung++) {
                check(rungs[rung] == 0, "the stuck ladder reached rung " + rung + ": " + Arrays.toString(rungs));
            }
            if (state.announced < 0) {
                return;
            }
            check(state.leaves - state.announced >= AnnounceWave.LEAST, "announced too late");
            check(state.camp >= 40, "the camp lay " + state.camp + " blocks from the start");
            int near = 0;
            for (int handle : core.queries().unitHandlesOf(new FactionSlot(0))) {
                UnitView u = core.queries().unit(handle);
                if (u != null
                        && u.template().equals(TemplateSet.INFANTRY)
                        && Math.hypot(u.x() - east.x(), u.z() - east.z()) <= 30) {
                    near++;
                }
            }
            if (near > 0) {
                h.succeed(); // the wave reached the base; the rungs were held at zero every tick up to here
            }
        });
    }
}
