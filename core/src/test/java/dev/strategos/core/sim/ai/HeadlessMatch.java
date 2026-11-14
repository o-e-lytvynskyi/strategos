package dev.strategos.core.sim.ai;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.Scenario;
import dev.strategos.core.content.StartSlot;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.NoticeKind;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchEnded;
import dev.strategos.core.sim.MatchRules;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.Notice;
import dev.strategos.core.sim.OutputEvent;
import dev.strategos.core.sim.PlayRules;
import dev.strategos.core.sim.TickOutputs;
import dev.strategos.core.sim.UnderAttack;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.UnitDied;
import dev.strategos.core.sim.UnitHurt;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.WaveAnnounced;
import dev.strategos.core.sim.build.BuildingView;
import dev.strategos.core.sim.command.ScenarioSetup;
import dev.strategos.core.sim.journal.JournalExport;
import dev.strategos.core.testkit.FirstSkirmish;
import dev.strategos.core.testkit.ProvingGround;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelWorld;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * One bot-versus-bot match in the core (QA-11, M3 spec §1.6 row 16): the first-loop scenario on the proving ground,
 * every slot played by the first-loop strategy, stepped until the match ends, it fails, or the game-time limit. It
 * fails on an exception, on a stall (a computer player that sent no command for two game minutes), or with no winner
 * by the limit, and gathers the telemetry a summary reports.
 */
final class HeadlessMatch {
    /** Two game minutes: a computer player silent so long has stalled. */
    static final long STALL = 2_400;

    /** The Done gate's 30 game minutes. */
    static final long LIMIT = 36_000;

    /**
     * M3 §8.4: after minute 5 no training waits for food longer than a minute; waiting is every building's queue empty
     * while the faction holds less food than a unit costs, and not under attack: a raided economy may starve, as the
     * losing side's does in a match's last minutes. Reported, not failed: M3's scripted bot meets it in nine seeds of ten
     * (the tenth waits 65 s), and the bot's economy is AI-02's (M4).
     */
    static final long FOOD_WAIT_FROM = 6_000;

    static final long FOOD_WAIT_MOST = 60;

    /** How long after an alert a faction counts as raided: an alert's own life (AlertSystem's QUIET). */
    private static final long RAIDED = 600;

    /** A worker's and a spearman's food, in thousandths. */
    private static final long UNIT_FOOD = 60_000;

    static final Path DATA = Path.of("../mod/src/main/resources/data/strategos/strategos");

    /** What one match did. */
    record Summary(
            long seed,
            int winner,
            long ticks,
            long firstContact,
            List<List<Integer>> army,
            List<Long> idleWorkerSeconds,
            List<Long> foodWaitSeconds,
            List<Long> foodWaitEndedAt,
            List<List<Long>> banked,
            List<Integer> unitsLost,
            List<Integer> stuck,
            List<Long> commands,
            List<Long> waves,
            List<Long> announcedAt,
            List<Long> leavesAt,
            List<Long> leftAt,
            List<Long> buildOrderDoneAt,
            int maxSpent,
            long journalHash,
            String failure) {
        boolean failed() {
            return !failure.isEmpty();
        }
    }

    private HeadlessMatch() {}

    static String read(String file) {
        try {
            return Files.readString(DATA.resolve(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Scenario firstLoop() {
        return Scenario.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString(read("scenarios/first_loop.json")))
                .getOrThrow();
    }

    /** The match of {@code seed}, both slots the computer's, run to its end or {@code limit} ticks. */
    static Summary play(long seed, long limit) {
        return play(seed, limit, "first_loop");
    }

    /** As {@link #play(long, long)}, both slots playing script {@code ai/<script>.json} (AI-02's {@code economy}). */
    static Summary play(long seed, long limit, String script) {
        return run(seed, limit, read("ai/" + script + ".json"), tick -> {}).summary();
    }

    /** A run: its summary and its journal, for a failing seed's replay. */
    record Result(Summary summary, JournalExport journal) {}

    /**
     * As {@link #play}, both slots playing {@code script}, {@code onTick} told each tick after its step (a test throws
     * there to see an exception fail the run).
     */
    static Result run(long seed, long limit, String script, java.util.function.LongConsumer onTick) {
        return run(seed, limit, script, (h, out) -> onTick.accept(out.tick()), true);
    }

    /** What a test watches of a run: the match after each tick's step, and that tick's outputs. */
    interface Watcher {
        void tick(SimHarness h, TickOutputs out);
    }

    /**
     * As {@link #run(long, long, String, java.util.function.LongConsumer)}, {@code watcher} shown the match each tick;
     * {@code decided}: whether the match is held to QA-11's ending, a winner by the limit and no player silent
     * {@value #STALL} ticks. AI-02's 15-minute matches judge the economy instead, and a player saving for its next
     * soldier may well be silent that long.
     */
    static Result run(long seed, long limit, String script, Watcher watcher, boolean decided) {
        return run(Ground.provingGround(), firstLoop(), seed, limit, script, watcher, decided);
    }

    /** The map a run plays on: its terrain and its start slots. */
    record Ground(int size, int height, VoxelWorld voxels, List<StartSlot> starts) {
        static Ground provingGround() {
            ProvingGround g = ProvingGround.generate();
            return new Ground(ProvingGround.SIZE, ProvingGround.HEIGHT, g.voxels(), g.startSlots());
        }

        static Ground firstSkirmish() {
            FirstSkirmish g = FirstSkirmish.generate();
            return new Ground(FirstSkirmish.SIZE, FirstSkirmish.HEIGHT, g.voxels(), g.startSlots());
        }
    }

    /** As {@link #run(long, long, String, Watcher, boolean)}, on {@code ground} with {@code scenario}'s starts. */
    static Result run(
            Ground ground, Scenario scenario, long seed, long limit, String script, Watcher watcher, boolean decided) {
        return run(ground, scenario, seed, limit, List.of(script, script), watcher, decided);
    }

    /**
     * As above, slot {@code k} playing {@code scripts.get(k)}: two levels, two openings or two scripts in one match
     * (AI-05's Hard against Normal).
     */
    static Result run(
            Ground ground,
            Scenario scenario,
            long seed,
            long limit,
            List<String> scripts,
            Watcher watcher,
            boolean decided) {
        return run(ground, scenario, seed, limit, scripts, watcher, decided, EXPLORED);
    }

    /** The rules of QA-11's matches: explored, the AI seeing through everything, no victory rule of its own. */
    static final PlayRules EXPLORED = new PlayRules(PlayRules.Victory.NONE, false, true, true);

    /**
     * The rules of QA-12's matches (MAP-02 with fog on, AI-18's fair AI): conquest decides, no cell starts explored,
     * the AI sees what its faction sees. The scenario's own victory rule is applied by its setup.
     */
    static final PlayRules FOGGED = new PlayRules(PlayRules.Victory.CONQUEST, false, false, false);

    /** As above, under {@code ai}'s play rules: {@link #EXPLORED} or {@link #FOGGED}. */
    static Result run(
            Ground ground,
            Scenario scenario,
            long seed,
            long limit,
            List<String> scripts,
            Watcher watcher,
            boolean decided,
            PlayRules ai) {
        SimHarness h = SimHarness.builder()
                .size(ground.size(), ground.size())
                .yBand(0, ground.height(), 16)
                .seed(seed)
                .rules(MatchRules.DEFAULTS.withPlay(ai))
                .build();
        h.publishAll(ground.voxels());
        h.step();
        MatchState s = KernelAccess.state(h.core());
        s.setStartPositions(ground.starts()); // AI-18: the AI knows the starts under fog
        List<Scenario.Slot> computers = new ArrayList<>();
        for (Scenario.Slot slot : scenario.slots()) {
            computers.add(new Scenario.Slot(ScenarioSetup.AI + "first_loop", slot.resources(), slot.entities()));
        }
        Scenario played = new Scenario(scenario.map(), scenario.victory(), computers);
        int[] seat = {0}; // the setup asks for each computer slot's script in slot order
        for (Command c :
                ScenarioSetup.commands(played, ground.starts(), TemplateSet.builtin(), p -> scripts.get(seat[0]++))) {
            h.submit(PlayerSlot.ADMIN, c);
        }
        int factions = 2;
        long firstContact = -1;
        List<List<Integer>> army = new ArrayList<>();
        long[] idle = new long[factions];
        long[] waiting = new long[factions];
        long[] foodWait = new long[factions];
        long[] foodWaitAt = new long[factions];
        long[] attackedAt = {-1_000_000, -1_000_000};
        List<List<Long>> banked = new ArrayList<>();
        int[] lost = new int[factions];
        java.util.Set<Integer> animals = new java.util.HashSet<>();
        int[] stuck = new int[factions];
        List<Long> announced = new ArrayList<>();
        List<Long> leaves = new ArrayList<>();
        List<Long> left = new ArrayList<>();
        long[] doneAt = {-1, -1};
        long[] launched = {0, 0};
        int maxSpent = 0;
        int[] steps = {
            StrategyScript.parse(scripts.get(0)).build().size(),
            StrategyScript.parse(scripts.get(1)).build().size()
        };
        int winner = -2;
        boolean watchState =
                ai.victory() == PlayRules.Victory.CONQUEST; // QA-12's watchdog: the state, not the commands
        long lastDigest = 0;
        long changedAt = 0;
        String failure = "";
        try {
            while (h.tick() < limit && winner == -2) {
                TickOutputs out = h.step();
                watcher.tick(h, out);
                for (OutputEvent e : out.events()) {
                    switch (e) {
                        case UnitCreated created
                        when created.faction().value() >= factions ->
                            animals.add(created.handle()); // Gaia's: their slaughter is no contact (ECO-16)
                        case UnitHurt hurt -> {
                            if (firstContact < 0
                                    && hurt.attacker() != Handle.NONE
                                    && !animals.contains(hurt.handle())) {
                                firstContact = out.tick();
                            }
                        }
                        case UnitDied died
                        when died.faction().value() < factions ->
                            lost[died.faction().value()]++;
                        case Notice n
                        when n.kind() == NoticeKind.STUCK -> stuck[n.faction().value()]++;
                        case WaveAnnounced w -> {
                            if (w.faction().value() == 0) {
                                announced.add(out.tick());
                                leaves.add(w.leaves());
                            }
                        }
                        case MatchEnded end -> winner = end.winner();
                        case UnderAttack a -> attackedAt[a.faction().value()] = out.tick();
                        default -> {}
                    }
                }
                if (out.tick() % 20 == 0) {
                    for (UnitView u : h.core().queries().unitHandles().length == 0 ? List.<UnitView>of() : units(h)) {
                        if (u.state() == UnitState.IDLE && u.template().equals(TemplateSet.WORKER)) {
                            idle[u.faction().value()]++;
                        }
                    }
                }
                if (out.tick() % 20 == 0 && out.tick() >= FOOD_WAIT_FROM) {
                    for (int f = 0; f < factions; f++) {
                        if (s.outcome().defeated(f)) {
                            continue;
                        }
                        boolean queued = false;
                        for (BuildingView b : h.core().queries().buildings()) {
                            queued |= b.faction() == f && !b.queue().isEmpty();
                        }
                        int food = h.core().queries().config().rules().economy().indexOf("food");
                        long stock = h.core()
                                .queries()
                                .treasury(new FactionSlot(f))
                                .stock()
                                .get(food);
                        boolean raided = out.tick() - attackedAt[f] < RAIDED;
                        waiting[f] = !queued && stock < UNIT_FOOD && !raided ? waiting[f] + 1 : 0;
                        if (waiting[f] > foodWait[f]) {
                            foodWait[f] = waiting[f];
                            foodWaitAt[f] = out.tick();
                        }
                    }
                }
                if (out.tick() % 1_200 == 0) {
                    List<Integer> a = new ArrayList<>();
                    List<Long> b = new ArrayList<>();
                    for (int f = 0; f < factions; f++) {
                        int n = 0;
                        for (UnitView u : units(h)) {
                            if (u.faction().value() == f && u.template().equals(TemplateSet.INFANTRY)) {
                                n++;
                            }
                        }
                        a.add(n);
                        b.add(h.core().queries().treasury(new FactionSlot(f)).stock().stream()
                                        .mapToLong(Long::longValue)
                                        .sum()
                                / 1000);
                    }
                    army.add(a);
                    banked.add(b);
                }
                for (int f = 0; f < factions; f++) {
                    ComputerPlayer p = s.aiSlots().get(f);
                    if (p == null) {
                        continue;
                    }
                    maxSpent = Math.max(maxSpent, p.lastSpent());
                    if (doneAt[f] < 0 && p.step() >= steps[f]) {
                        doneAt[f] = out.tick();
                    }
                    if (p.waves() > launched[f]) {
                        launched[f] = p.waves();
                        if (f == 0) {
                            left.add(out.tick()); // its attack order went out in this tick's think
                        }
                    }
                    if (decided
                            && !watchState
                            && !s.outcome().defeated(f)
                            && out.tick() - Math.max(p.lastCommand(), 1) > STALL) {
                        failure = "faction " + f + " stalled: no command since tick " + p.lastCommand();
                    }
                }
                if (watchState && decided && out.tick() % STATE_SAMPLE == 0) {
                    long digest = progress(h);
                    if (digest != lastDigest) {
                        lastDigest = digest;
                        changedAt = out.tick();
                    } else if (out.tick() - changedAt >= STALL) {
                        failure = "stalled: no state change since tick " + changedAt;
                    }
                }
                if (!failure.isEmpty()) {
                    break;
                }
            }
            if (failure.isEmpty() && winner == -2 && decided) {
                failure = "no winner by tick " + limit;
            }

        } catch (RuntimeException | AssertionError e) {
            failure = "exception at tick " + h.tick() + ": " + e;
        }
        List<Long> commands = new ArrayList<>();
        List<Long> waves = new ArrayList<>();
        for (int f = 0; f < factions; f++) {
            ComputerPlayer p = s.aiSlots().get(f);
            commands.add(p == null ? 0 : p.commands());
            waves.add(p == null ? 0 : p.waves());
        }
        Summary summary = new Summary(
                seed,
                winner,
                h.tick(),
                firstContact,
                army,
                List.of(idle[0], idle[1]),
                List.of(foodWait[0], foodWait[1]),
                List.of(foodWaitAt[0], foodWaitAt[1]),
                banked,
                List.of(lost[0], lost[1]),
                List.of(stuck[0], stuck[1]),
                commands,
                waves,
                announced,
                leaves,
                left,
                List.of(doneAt[0], doneAt[1]),
                maxSpent,
                h.stateHash(),
                failure);
        return new Result(summary, new JournalExport(h.core().journal(), h.tick(), h.stateHash()));
    }

    /** How often the state is sampled for the watchdog: ticks. */
    static final long STATE_SAMPLE = 100;

    /**
     * A digest of what moves in a match: every unit's place, every building's progress and health, every
     * faction's stock. Not the match's hash, which holds the tick and so always changes: a match whose digest stays
     * the same for {@link #STALL} ticks has stopped.
     */
    static long progress(SimHarness h) {
        long d = 17;
        for (UnitView u : units(h)) {
            d = d * 31 + Double.hashCode(u.x());
            d = d * 31 + Double.hashCode(u.z());
        }
        int factions = 2;
        for (BuildingView b : h.core().queries().buildings()) {
            d = d * 31 + b.handle();
            d = d * 31 + b.progress();
            d = d * 31 + Double.hashCode(b.hitPoints());
        }
        for (int f = 0; f < factions; f++) {
            d = d * 31 + h.core().queries().treasury(new FactionSlot(f)).stock().hashCode();
        }
        return d;
    }

    private static List<UnitView> units(SimHarness h) {
        List<UnitView> out = new ArrayList<>();
        for (int handle : h.core().queries().unitHandles()) {
            UnitView u = h.core().queries().unit(handle);
            if (u != null) {
                out.add(u);
            }
        }
        return out;
    }
}
