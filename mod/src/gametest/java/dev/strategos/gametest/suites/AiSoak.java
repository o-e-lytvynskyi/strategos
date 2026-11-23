package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;

import dev.strategos.core.content.MapManifest;
import dev.strategos.core.content.Scenario;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchLifecycle;
import dev.strategos.core.sim.PlayRules;
import dev.strategos.core.sim.ai.Levels;
import dev.strategos.core.sim.command.ScenarioSetup;
import dev.strategos.core.sim.journal.ReplayDriver;
import dev.strategos.core.sim.journal.ReplayResult;
import dev.strategos.gametest.MatchFixture;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.platform.Ids;
import dev.strategos.mod.server.content.MapPackageLoader;
import dev.strategos.mod.server.content.Scenarios;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.match.MatchSpace;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA-12.4, the AI-versus-AI soak (spec §2.21): {@value #SEEDS} seeds of the skirmish script at Normal against itself on
 * the first skirmish map, in the real skirmish's rules (Conquest under Fog), all under {@code /tick sprint}, which is
 * {@code runGameTestSoak}'s alone ({@code verifySoak}). It asserts no win rate, and no ending: a match runs to its
 * conquest or to the 45-minute limit, and each journal replays to its hash with no match faulted. The wall time is the
 * run's, in the report line {@code L3 wall time}; the matches' outcomes are in the log lines {@code aisoak seed}.
 */
public final class AiSoak {
    static final int SEEDS = 20;
    /** The match limit of QA-12: 45 game minutes. */
    static final long LIMIT = 45L * 60 * 20;

    private static final Logger LOG = LoggerFactory.getLogger(AiSoak.class);
    private static final int FAR = 2_048;
    private static final int FIRST_LANE = 1;
    /** Matches played at a time: a tick costs all of them, and twenty at once ran far slower than their sum. */
    static final int PARALLEL = 4;

    private AiSoak() {}

    /** The soak's batch: its tests run in {@code runGameTestSoak} only. */
    public static final String BATCH = "aisoak";

    private static final class Game {
        final long seed;
        final MatchRuntime rt;
        final MapManifest manifest;
        boolean started;
        boolean done;

        Game(long seed, MatchRuntime rt, MapManifest manifest) {
            this.seed = seed;
            this.rt = rt;
            this.manifest = manifest;
        }
    }

    @StrategosTest(batch = BATCH, solo = true, timeoutTicks = 2_000_000)
    public static void twentySeedsOfNormalAgainstNormal(GameTestHelper h) {
        StrategosServer server = StrategosServer.get();
        MapManifest manifest = MapPackageLoader.load(h.getLevel().getServer(), MapTests.FIRST_SKIRMISH)
                .manifest();
        String shipped = Objects.requireNonNull(
                Scenarios.script(h.getLevel().getServer(), Levels.PROFILE), "the shipped ai/skirmish.json");
        String normal = Levels.resolve(shipped, "normal");
        Scenario scenario = Scenarios.load(h.getLevel().getServer(), Ids.id("first_skirmish"));
        List<Scenario.Slot> slots = new ArrayList<>();
        for (Scenario.Slot s : scenario.slots()) {
            slots.add(new Scenario.Slot(ScenarioSetup.AI + Levels.PROFILE, s.resources(), s.entities()));
        }
        Scenario computers = new Scenario(scenario.map(), scenario.victory(), slots);
        BlockPos corner = h.absolutePos(BlockPos.ZERO);
        int seeds = Integer.getInteger("strategos.aisoak.seeds", SEEDS); // fewer for a trial run or a shard
        long firstSeed = Long.getLong("strategos.aisoak.firstSeed", 1L); // a shard's first seed (05-testing.md)
        int window = Integer.getInteger("strategos.aisoak.parallel", PARALLEL);
        Game[] games = new Game[seeds];
        int[] next = {0};
        Runnable opener = () -> {
            int k = next[0]++;
            long seed = firstSeed + k;
            BlockPos origin = new BlockPos(corner.getX() + (FIRST_LANE + k) * FAR, corner.getY(), corner.getZ());
            MatchSpace space = new MatchSpace(
                    h.getLevel().dimension(),
                    origin,
                    manifest.sizeX(),
                    manifest.sizeZ(),
                    origin.getY(),
                    origin.getY() + manifest.height() - 1);
            MatchRuntime rt = MatchFixture.create(
                    h,
                    space,
                    MatchFixture.options(seed, h)
                            .withPlay(PlayRules.DEFAULTS)
                            .withMap(
                                    MapTests.FIRST_SKIRMISH,
                                    origin.getY() + manifest.startSlots().get(0).y()));
            Game g = new Game(seed, rt, manifest);
            games[k] = g;
        };
        server.server().tickRateManager().requestGameToSprint(Integer.MAX_VALUE); // /tick sprint
        var tally = new Object() {
            int ended;
            int unfinished;
            long ticks;
        };
        h.onEachTick(() -> {
            int live = 0;
            for (int k = 0; k < next[0]; k++) {
                if (!games[k].done) {
                    live++;
                }
            }
            while (live < window && next[0] < seeds) { // a window of matches at a time: each tick costs them all
                opener.run();
                live++;
            }
            int open = 0;
            for (int k = 0; k < next[0]; k++) {
                Game g = games[k];
                if (g.done) {
                    continue;
                }
                MatchLifecycle life = g.rt.lifecycle();
                check(life != MatchLifecycle.FAULTED, "seed " + g.seed + " faulted: " + g.rt.faultMessage());
                if (life == MatchLifecycle.PREPARING) {
                    open++;
                    continue;
                }
                MatchCore core = Objects.requireNonNull(g.rt.core(), "seed " + g.seed + " has no core");
                if (!g.started) {
                    g.started = true;
                    Scenarios.apply(
                            g.rt,
                            computers,
                            g.manifest.startSlots(),
                            core.queries().templates(),
                            profile -> normal);
                    open++;
                    continue;
                }
                long tick = core.queries().tick();
                boolean ended = life == MatchLifecycle.ENDED;
                if (!ended && tick < LIMIT) {
                    open++;
                    continue;
                }
                g.done = true;
                ReplayResult replay =
                        ReplayDriver.replay(core.journal(), core.queries().templates());
                check(replay.matches(), "seed " + g.seed + ": the replay diverged: " + replay);
                check(replay.finalHash() == core.stateHash(), "seed " + g.seed + ": the replay ends at another hash");
                if (ended) {
                    tally.ended++;
                } else {
                    tally.unfinished++;
                }
                tally.ticks += tick;
                int[] centres = new int[2];
                int[] buildings = new int[2];
                for (var b : core.queries().buildings()) {
                    if (b.faction() < 2) {
                        buildings[b.faction()]++;
                        if (b.template().value().endsWith("civic_centre")) {
                            centres[b.faction()]++;
                        }
                    }
                }
                LOG.info(
                        "aisoak seed {}: {} at tick {} ({} min), civic centres {}/{}, buildings {}/{}; the journal"
                                + " replays to its hash",
                        g.seed,
                        ended ? "conquered" : "no conquest by the limit",
                        tick,
                        String.format("%.1f", tick / 1200.0),
                        centres[0],
                        centres[1],
                        buildings[0],
                        buildings[1]);
                StrategosServer.get().matches().dispose(g.rt.id()); // its lane is free of bodies and chunks
            }
            if (open == 0 && next[0] == seeds) {
                LOG.info(
                        "aisoak: {} matches, {} by conquest, {} to the limit, {} game ticks in all",
                        seeds,
                        tally.ended,
                        tally.unfinished,
                        tally.ticks);
                server.server().tickRateManager().stopSprinting();
                h.succeed();
            }
        });
    }
}
