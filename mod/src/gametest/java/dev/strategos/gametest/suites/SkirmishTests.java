package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;

import dev.strategos.core.content.MapManifest;
import dev.strategos.core.protocol.SetAi;
import dev.strategos.core.protocol.SkirmishSetup;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.InputEvent;
import dev.strategos.core.sim.MatchLifecycle;
import dev.strategos.core.sim.PlayRules;
import dev.strategos.core.sim.TickInputs;
import dev.strategos.core.sim.ai.Levels;
import dev.strategos.core.sim.ai.StrategyScript;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.gametest.MatchFixture;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.gametest.net.MockViewer;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.content.MapPackageLoader;
import dev.strategos.mod.server.content.Scenarios;
import dev.strategos.mod.server.match.MatchRefusedException;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.match.MatchSpace;
import dev.strategos.mod.server.match.Skirmishes;
import dev.strategos.mod.server.net.payload.SkirmishPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.resources.ResourceLocation;

/**
 * MATCH-02's setup pipeline, headless (spec §2.11): {@link Skirmishes#start} with the GameTest's own placer standing
 * for the arena's next slot (a GameTest world is no arena; the slot allocation itself is the arena smoke's). Each
 * import test runs alone in {@code runGameTestSlow}, far east of its structure, as {@link MapTests}' do.
 */
public final class SkirmishTests {
    private static final ResourceLocation FIRST_SKIRMISH = MapTests.FIRST_SKIRMISH;
    private static final int FAR = 2_048;
    private static final int SETUP_LANE = 13;
    private static final int TWO_LANE = 14;
    private static final int POPULATION = 120;

    private SkirmishTests() {}

    /** A placer that puts each skirmish's match in lane {@code lane + n} of the test, n counting from zero. */
    private static Skirmishes.Placer lanes(GameTestHelper h, int lane) {
        int[] next = {lane};
        return (map, options) -> {
            MapManifest m = MapPackageLoader.load(h.getLevel().getServer(), map).manifest();
            BlockPos corner = h.absolutePos(BlockPos.ZERO);
            BlockPos origin = new BlockPos(corner.getX() + next[0]++ * FAR, corner.getY(), corner.getZ());
            MatchSpace space = new MatchSpace(
                    h.getLevel().dimension(),
                    origin,
                    m.sizeX(),
                    m.sizeZ(),
                    origin.getY(),
                    origin.getY() + m.height() - 1);
            return MatchFixture.create(
                    h,
                    space,
                    MatchFixture.options(SimHarness.DEFAULT_SEED, h)
                            .withMap(map, origin.getY() + m.startSlots().get(0).y())
                            .withPlay(options.play()));
        };
    }

    private static MockViewer viewer(GameTestHelper h, String name, int lane) {
        BlockPos corner = h.absolutePos(BlockPos.ZERO);
        return MockViewer.join(
                h.getLevel(), name, corner.getX() + lane * FAR, corner.getY() + 16, corner.getZ() + 192, 2);
    }

    /**
     * MATCH-02.1: the setup the screen sends (map, rules, level, resources, cap, visibility) makes a match on that map;
     * once imported it holds the chosen population cap, visibility and Conquest, twice the scenario's starting
     * resources, the player seated in slot 0 and the computer commanding from slot 1, playing ai:skirmish at the chosen level (Hard); and the same player cannot start
     * a second skirmish while the first is still importing.
     */
    @StrategosTest(batch = "map_skirmish_setup", solo = true, timeoutTicks = 40_000)
    public static void setupMakesTheMatchWithTheChosenSettings(GameTestHelper h) {
        MockViewer player = viewer(h, "strategos-sk-one", SETUP_LANE);
        SkirmishSetup setup = new SkirmishSetup(
                FIRST_SKIRMISH.toString(),
                SkirmishSetup.Level.HARD,
                SkirmishSetup.StartResources.HIGH,
                POPULATION,
                SkirmishSetup.Visibility.EXPLORED);
        StrategosServer server = StrategosServer.get();
        MatchRuntime rt =
                server.skirmishes().start(server, setup, player.player().getUUID(), lanes(h, SETUP_LANE));
        check(rt.map() != null && rt.map().equals(FIRST_SKIRMISH), "the match is on " + rt.map());
        check(rt.lifecycle() == MatchLifecycle.PREPARING, "not preparing: " + rt.lifecycle());
        try {
            server.skirmishes()
                    .start(server, SkirmishSetup.DEFAULTS, player.player().getUUID(), lanes(h, SETUP_LANE + 5));
            check(false, "a second skirmish started while the first was importing");
        } catch (MatchRefusedException expected) {
            check(server.matches().all().stream().filter(m -> m != rt).noneMatch(m -> m.map() != null), "stray match");
        }
        int[] commands = {0};
        rt.addListener(out -> out.events().forEach(e -> {
            if (e instanceof dev.strategos.core.sim.CommandResult r
                    && !r.issuer().isAdmin()
                    && r.issuer().value() == 1) {
                commands[0]++;
            }
        }));
        h.startSequence()
                .thenWaitUntil(() -> {
                    check(rt.lifecycle() != MatchLifecycle.PREPARING, "importing");
                    check(server.matches().seatOf(player.player().getUUID()) != null, "the player is not seated yet");
                })
                .thenExecute(() -> {
                    var seat = server.matches().seatOf(player.player().getUUID());
                    check(seat.match() == rt && seat.slot().value() == 0, "seated at " + seat.slot());
                    var q = core(rt).queries();
                    check(
                            q.config().rules().economy().populationMax() == POPULATION,
                            "population cap " + q.config().rules().economy().populationMax());
                    PlayRules play = q.config().rules().play();
                    check(play.explored() && !play.revealMap(), "visibility " + play);
                    check(play.victory() == PlayRules.Victory.CONQUEST, "victory " + play.victory());
                    check(q.buildings().stream().filter(b -> b.faction() <= 1).count() >= 2, "no centres");
                    long a = q.treasury(new FactionSlot(0)).stock().stream()
                            .mapToLong(Long::longValue)
                            .sum();
                    long b = q.treasury(new FactionSlot(1)).stock().stream()
                            .mapToLong(Long::longValue)
                            .sum();
                    // the scenario grants 300 food, 100 stone and 100 iron, doubled; the treasury counts thousandths
                    check(a == 1_000_000L && b == 1_000_000L, "treasuries " + a + " and " + b);
                })
                .thenExecute(() -> {
                    // AI-05: slot 1 is the computer, ai:skirmish at the chosen level; the journal holds what it plays
                    List<SetAi> seated = new ArrayList<>();
                    for (TickInputs t : core(rt).journal().nonEmptyTicks()) {
                        for (InputEvent e : t.events()) {
                            if (e instanceof CommandEnvelope c && c.command() instanceof SetAi set) {
                                seated.add(set);
                            }
                        }
                    }
                    check(seated.size() == 1, "computer players set: " + seated.size());
                    SetAi set = seated.get(0);
                    check(
                            set.faction().value() == 1,
                            "the computer sits in slot " + set.faction().value());
                    check(set.profile().equals(Levels.PROFILE), "profile " + set.profile());
                    String shipped = Scenarios.script(server.server(), Levels.PROFILE);
                    check(shipped != null, "no ai/skirmish.json");
                    check(set.script().equals(Levels.resolve(shipped, "hard")), "not the script resolved at Hard");
                    check(StrategyScript.parse(set.script()).level().name().equals("hard"), "level not Hard");
                })
                .thenWaitUntil(() -> check(commands[0] > 0, "the computer in slot 1 has commanded nothing yet"))
                .thenExecute(() -> server.matches().dispose(rt.id()))
                .thenSucceed();
    }

    /**
     * MATCH-02.2: a second skirmish, another player's, is a match of its own: its own space and map, its player in
     * slot 0 of it, the first one's player left where it was.
     */
    @StrategosTest(batch = "map_skirmish_two", solo = true, timeoutTicks = 60_000)
    public static void aSecondSkirmishIsAMatchOfItsOwn(GameTestHelper h) {
        MockViewer a = viewer(h, "strategos-sk-a", TWO_LANE);
        MockViewer b = viewer(h, "strategos-sk-b", TWO_LANE + 1);
        StrategosServer server = StrategosServer.get();
        Skirmishes.Placer placer = lanes(h, TWO_LANE);
        MatchRuntime first = server.skirmishes()
                .start(server, SkirmishSetup.DEFAULTS, a.player().getUUID(), placer);
        MatchRuntime second = server.skirmishes()
                .start(server, SkirmishSetup.DEFAULTS, b.player().getUUID(), placer);
        check(first != second && !first.id().equals(second.id()), "one match for both");
        check(!first.space().origin().equals(second.space().origin()), "both matches in one space");
        h.startSequence()
                .thenWaitUntil(() -> {
                    check(server.matches().seatOf(a.player().getUUID()) != null, "a is not seated yet");
                    check(server.matches().seatOf(b.player().getUUID()) != null, "b is not seated yet");
                })
                .thenExecute(() -> {
                    var seatA = server.matches().seatOf(a.player().getUUID());
                    var seatB = server.matches().seatOf(b.player().getUUID());
                    check(seatA.match() == first && seatA.slot().value() == 0, "a sits at " + seatA);
                    check(seatB.match() == second && seatB.slot().value() == 0, "b sits at " + seatB);
                    check(
                            core(first).queries().config().rules().play().victory() == PlayRules.Victory.CONQUEST
                                    && core(second)
                                                    .queries()
                                                    .config()
                                                    .rules()
                                                    .play()
                                                    .victory()
                                            == PlayRules.Victory.CONQUEST,
                            "not both conquest");
                    server.matches().dispose(first.id());
                    server.matches().dispose(second.id());
                })
                .thenSucceed();
    }

    /**
     * MATCH-02: the payload is untrusted client input. A player who is neither the host nor an operator makes no match;
     * an operator's request reaches the pipeline, which refuses it here for want of an arena, and tells the sender.
     */
    @StrategosTest(batch = "skirmish_payload", timeoutTicks = 200)
    public static void thePayloadNeedsTheHostOrAnOperator(GameTestHelper h) {
        MockViewer plain = viewer(h, "strategos-sk-pl", 0);
        MockViewer op = viewer(h, "strategos-sk-op", 0).operator(2);
        StrategosServer server = StrategosServer.get();
        int before = server.matches().all().size();
        plain.send(new SkirmishPayload(SkirmishSetup.DEFAULTS));
        op.send(new SkirmishPayload(SkirmishSetup.DEFAULTS));
        h.startSequence()
                .thenWaitUntil(() -> {
                    op.poll();
                    check(
                            op.capture().of(ClientboundSystemChatPacket.class).stream()
                                    .anyMatch(p -> p.content().getString().contains("skirmish refused")),
                            "the operator was told nothing");
                })
                .thenExecute(() -> {
                    plain.poll();
                    List<String> said = new ArrayList<>();
                    op.capture()
                            .of(ClientboundSystemChatPacket.class)
                            .forEach(p -> said.add(p.content().getString()));
                    check(said.stream().anyMatch(s -> s.contains("skirmish refused")), "the operator heard " + said);
                    check(
                            plain.capture().of(ClientboundSystemChatPacket.class).stream()
                                    .noneMatch(p -> p.content().getString().contains("skirmish")),
                            "the plain player was answered");
                    check(server.matches().all().size() == before, "a match was made");
                    check(!plain.disconnected() && !op.disconnected(), "a player was disconnected");
                })
                .thenSucceed();
    }
}
