package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;

import dev.strategos.core.content.TemplateSet;
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
import dev.strategos.core.sim.journal.ReplayDriver;
import dev.strategos.core.sim.journal.ReplayResult;
import dev.strategos.gametest.MatchFixture;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.gametest.net.MockViewer;
import dev.strategos.mod.server.content.Scenarios;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.net.payload.MatchEventPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;

/**
 * The computer players in a real level (AI-17, QA-11, AI-02): two seeds of bot against bot and one of the economy
 * script, whose journals replay in-process to the live hash; and an announced wave that reaches the other faction's
 * player.
 */
public final class AiTests {
    static final TemplateId CENTRE = new TemplateId("strategos:building/civic_centre");

    private AiTests() {}

    /** Both factions' centres on the 48 arena, five workers and 300 food each. */
    static MatchRuntime town(GameTestHelper h, long seed) {
        MatchRuntime rt = MatchFixture.create(h, MatchFixture.options(seed, h));
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
        MatchRuntime rt = town(h, 5);
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
        MatchRuntime rt = town(h, 1);
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
}
