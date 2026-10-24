package dev.strategos.core.sim.ai;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.Scenario;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.NoticeKind;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchEnded;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.Notice;
import dev.strategos.core.sim.OutputEvent;
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
import dev.strategos.core.testkit.ProvingGround;
import dev.strategos.core.testkit.SimHarness;
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
        ProvingGround ground = ProvingGround.generate();
        SimHarness h = SimHarness.builder()
                .size(ProvingGround.SIZE, ProvingGround.SIZE)
                .yBand(0, ProvingGround.HEIGHT, 16)
                .seed(seed)
                .build();
        h.publishAll(ground.voxels());
        h.step();
        Scenario scenario = firstLoop();
        List<Scenario.Slot> computers = new ArrayList<>();
        for (Scenario.Slot slot : scenario.slots()) {
            computers.add(new Scenario.Slot(ScenarioSetup.AI + "first_loop", slot.resources(), slot.entities()));
        }
        Scenario played = new Scenario(scenario.map(), scenario.victory(), computers);
        for (Command c : ScenarioSetup.commands(played, ground.startSlots(), TemplateSet.builtin(), p -> script)) {
            h.submit(PlayerSlot.ADMIN, c);
        }
        MatchState s = KernelAccess.state(h.core());
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
        int steps = StrategyScript.parse(script).build().size();
        int winner = -2;
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
                    if (doneAt[f] < 0 && p.step() >= steps) {
                        doneAt[f] = out.tick();
                    }
                    if (p.waves() > launched[f]) {
                        launched[f] = p.waves();
                        if (f == 0) {
                            left.add(out.tick()); // its attack order went out in this tick's think
                        }
                    }
                    if (decided && !s.outcome().defeated(f) && out.tick() - Math.max(p.lastCommand(), 1) > STALL) {
                        failure = "faction " + f + " stalled: no command since tick " + p.lastCommand();
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
