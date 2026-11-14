package dev.strategos.core.sim.ai;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.Scenario;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.TechResearched;
import dev.strategos.core.testkit.JournalFiles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * QA-12's AI-versus-AI opening and level balance (M6 spec §2.21), run by {@code :core:openingBalance}: five pairings of
 * the skirmish script on the first skirmish map with fog on and conquest, 200 seeds each, the sides swapped on odd
 * seeds, matches in parallel over the machine's cores and each of them single-threaded. It writes
 * {@code summary.json} and {@code summary.md} and judges QA-12's criteria with AI-05.2, MAP-02.4 and AI-18.3; the
 * process exits 1 when one fails, and tells which.
 */
public final class OpeningBalance {
    /** QA-12's seeds per pairing. */
    static final int SEEDS = 200;

    /** A match's limit: 45 game minutes. */
    static final long LIMIT = 54_000;

    private static final long MINUTE = 1_200;
    private static final String PHASE_TOWN = "strategos:phase_town";

    /** One pairing: A against B, each a level and an opening ("" draws by the script's weights). */
    record Pairing(String name, String a, String aLevel, String aOpening, String b, String bLevel, String bOpening) {
        static Pairing openings(String x, String y) {
            return new Pairing(x + "-" + y, x, "normal", x, y, "normal", y);
        }

        static Pairing levels(String strong, String weak) {
            return new Pairing(strong + "-" + weak, strong, strong, "", weak, weak, "");
        }
    }

    static List<Pairing> pairings() {
        return List.of(
                Pairing.openings("rush", "boom"),
                Pairing.openings("rush", "balanced"),
                Pairing.openings("boom", "balanced"),
                Pairing.levels("hard", "normal"),
                Pairing.levels("normal", "easy"));
    }

    /** One match's record. {@code aSlot} is the start A played; winner -1 when the match did not end by conquest. */
    record Row(
            String pairing,
            long seed,
            int aSlot,
            int winnerSlot,
            boolean aWon,
            long ticks,
            long firstContact,
            List<String> openings, // by slot
            List<Long> townAt, // by slot, -1 never
            List<Long> commands,
            List<Long> waves,
            List<Integer> unitsLost,
            List<Long> idleWorkerSeconds,
            List<Integer> centres, // by slot, at the match's last sample (every 600 ticks)
            List<Integer> soldiers, // by slot, as above
            List<Long>
                    mop, // fell0 fell1 (tick of the first sample without a centre, -1 none), buildings0/1 (not fields),
            // workers0/1, idle0/1, walking0/1, fighting0/1 soldiers, at the last sample
            long wallMillis,
            String failure) {
        boolean ended() {
            return failure.isEmpty() && winnerSlot >= 0;
        }
    }

    private OpeningBalance() {}

    /** Plays {@code seed} of {@code p}. */
    static Row play(Pairing p, long seed, HeadlessMatch.Ground ground, Scenario scenario, String skirmish, Path dir) {
        String a = Levels.resolve(skirmish, p.aLevel(), p.aOpening());
        String b = Levels.resolve(skirmish, p.bLevel(), p.bOpening());
        int aSlot = seed % 2 == 0 ? 0 : 1;
        List<String> scripts = aSlot == 0 ? List.of(a, b) : List.of(b, a);
        String[] opening = {"", ""};
        long[] town = {-1, -1};
        int[] centres = {0, 0};
        int[] soldiers = {0, 0};
        long[] mop = {-1, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        long started = System.nanoTime();
        HeadlessMatch.Result r = HeadlessMatch.run(
                ground,
                scenario,
                seed,
                LIMIT,
                scripts,
                (h, out) -> {
                    if (h.tick() == 300) { // every player has thought by tick 140
                        for (int f = 0; f < 2; f++) {
                            opening[f] = KernelAccess.state(h.core())
                                    .aiSlots()
                                    .get(f)
                                    .openingName();
                        }
                    }
                    if (h.tick() % 600 == 0) {
                        centres[0] = centres[1] = soldiers[0] = soldiers[1] = 0;
                        for (int k = 2; k < mop.length; k++) {
                            mop[k] = 0;
                        }
                        for (var building : h.core().queries().buildings()) {
                            if (building.template().value().endsWith("civic_centre") && building.faction() < 2) {
                                centres[building.faction()]++;
                            }
                            if (building.faction() < 2
                                    && !building.template().value().endsWith("field")) {
                                mop[2 + building.faction()]++;
                            }
                        }
                        for (int f = 0; f < 2; f++) {
                            if (centres[f] == 0 && mop[f] < 0 && h.tick() > 1_200) {
                                mop[f] = h.tick();
                            }
                        }
                        for (int handle : h.core().queries().unitHandles()) {
                            var u = h.core().queries().unit(handle);
                            if (u != null
                                    && u.faction().value() < 2
                                    && !u.template().equals(dev.strategos.core.content.TemplateSet.WORKER)) {
                                soldiers[u.faction().value()]++;
                                int slot = u.faction().value();
                                if (u.state() == dev.strategos.core.sim.UnitState.IDLE) {
                                    mop[6 + slot]++;
                                } else if (u.state() == dev.strategos.core.sim.UnitState.WALKING) {
                                    mop[8 + slot]++;
                                } else if (u.state() == dev.strategos.core.sim.UnitState.COMBAT) {
                                    mop[10 + slot]++;
                                }
                            }
                            if (u != null
                                    && u.faction().value() < 2
                                    && u.template().equals(dev.strategos.core.content.TemplateSet.WORKER)) {
                                mop[4 + u.faction().value()]++;
                            }
                        }
                    }
                    for (var e : out.events()) {
                        if (e instanceof TechResearched t && t.tech().value().equals(PHASE_TOWN)) {
                            town[t.faction().value()] = out.tick();
                        }
                    }
                },
                true,
                HeadlessMatch.FOGGED);
        HeadlessMatch.Summary s = r.summary();
        if (s.failed()) {
            JournalFiles.write(dir.resolve(p.name() + "-seed-" + seed + JournalFiles.EXTENSION), r.journal());
        }
        return new Row(
                p.name(),
                seed,
                aSlot,
                s.winner(),
                s.winner() == aSlot,
                s.ticks(),
                s.firstContact(),
                List.of(opening[0], opening[1]),
                List.of(town[0], town[1]),
                s.commands(),
                s.waves(),
                s.unitsLost(),
                s.idleWorkerSeconds(),
                List.of(centres[0], centres[1]),
                List.of(soldiers[0], soldiers[1]),
                java.util.Arrays.stream(mop).boxed().toList(),
                (System.nanoTime() - started) / 1_000_000,
                s.failure());
    }

    static Scenario firstSkirmish() {
        return Scenario.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString(HeadlessMatch.read("scenarios/first_skirmish.json")))
                .getOrThrow();
    }

    /** Plays every pairing's seeds {@code 1..seeds} over {@code threads} threads; the rows in pairing and seed order. */
    static List<Row> playAll(List<Pairing> pairings, int seeds, int threads, Path dir) throws Exception {
        Files.createDirectories(dir);
        String skirmish = HeadlessMatch.read("ai/skirmish.json");
        HeadlessMatch.Ground ground = HeadlessMatch.Ground.firstSkirmish();
        Scenario scenario = firstSkirmish();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Row>> futures = new ArrayList<>();
            AtomicInteger done = new AtomicInteger();
            int total = pairings.size() * seeds;
            for (Pairing p : pairings) {
                for (long seed = 1; seed <= seeds; seed++) {
                    long s = seed;
                    futures.add(pool.submit(() -> {
                        Row row = play(p, s, ground, scenario, skirmish, dir);
                        int n = done.incrementAndGet();
                        if (n % 25 == 0 || n == total) {
                            System.out.println("openingBalance: " + n + " of " + total + " matches played");
                        }
                        return row;
                    }));
                }
            }
            List<Row> rows = new ArrayList<>();
            for (Future<Row> f : futures) {
                rows.add(f.get());
            }
            return rows;
        } finally {
            pool.shutdownNow();
        }
    }

    // ---- the summary ----

    static String clock(long ticks) {
        long seconds = ticks / 20;
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    static long median(List<Long> values) {
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        return sorted.isEmpty() ? 0 : sorted.get(sorted.size() / 2);
    }

    static List<Row> of(List<Row> rows, String pairing) {
        return rows.stream().filter(r -> r.pairing().equals(pairing)).toList();
    }

    private static long count(List<Row> rows, java.util.function.Predicate<Row> p) {
        return rows.stream().filter(p).count();
    }

    /** One line per pairing for the log and the markdown. */
    static String pairingLine(Pairing p, List<Row> rows) {
        List<Long> ticks = rows.stream().map(Row::ticks).toList();
        return String.format(
                Locale.ROOT,
                "%-16s %s wins %3d of %d, start 0 wins %3d, start 1 wins %3d, median %s, longest %s, A's Town %s, "
                        + "B's Town %s",
                p.name(),
                p.a(),
                count(rows, Row::aWon),
                rows.size(),
                count(rows, r -> r.winnerSlot() == 0),
                count(rows, r -> r.winnerSlot() == 1),
                clock(median(ticks)),
                clock(Collections.max(ticks)),
                clock(median(rows.stream()
                        .map(r -> r.townAt().get(r.aSlot()))
                        .filter(t -> t >= 0)
                        .toList())),
                clock(median(rows.stream()
                        .map(r -> r.townAt().get(1 - r.aSlot()))
                        .filter(t -> t >= 0)
                        .toList())));
    }

    static String markdown(List<Pairing> pairings, List<Row> rows) {
        StringBuilder md = new StringBuilder("# QA-12: opening and level balance\n\n");
        md.append("First skirmish map, fog on, conquest, limit 45:00; sides swapped on odd seeds.\n\n");
        md.append("## Pairings\n\n| Pairing | A wins | Start 0 wins | Start 1 wins | Median | Longest | Matches |\n")
                .append("|---|---|---|---|---|---|---|\n");
        for (Pairing p : pairings) {
            List<Row> r = of(rows, p.name());
            md.append(String.format(
                    Locale.ROOT,
                    "| %s (%s over %s) | %d | %d | %d | %s | %s | %d |%n",
                    p.name(),
                    p.a(),
                    p.b(),
                    count(r, Row::aWon),
                    count(r, x -> x.winnerSlot() == 0),
                    count(r, x -> x.winnerSlot() == 1),
                    clock(median(r.stream().map(Row::ticks).toList())),
                    clock(r.stream().mapToLong(Row::ticks).max().orElse(0)),
                    r.size()));
        }
        md.append(
                "\n## Openings (every match, drawn or pinned)\n\n| Opening | Games | Wins | Rate |\n|---|---|---|---|\n");
        for (String o : List.of("rush", "boom", "balanced")) {
            long games = 0;
            long wins = 0;
            for (Row r : rows) {
                for (int slot = 0; slot < 2; slot++) {
                    if (r.openings().get(slot).equals(o)) {
                        games++;
                        wins += r.winnerSlot() == slot ? 1 : 0;
                    }
                }
            }
            md.append(String.format(
                    Locale.ROOT,
                    "| %s | %d | %d | %.1f %% |%n",
                    o,
                    games,
                    wins,
                    games == 0 ? 0 : 100.0 * wins / games));
        }
        md.append("\n## Levels\n\n| Level | Games | Wins |\n|---|---|---|\n");
        for (Pairing p : pairings.stream().filter(x -> x.aOpening().isEmpty()).toList()) {
            List<Row> r = of(rows, p.name());
            md.append(String.format(
                    Locale.ROOT, "| %s over %s | %d | %d |%n", p.a(), p.b(), r.size(), count(r, Row::aWon)));
        }
        md.append("\n## Starts\n\n| Start | Wins | Matches |\n|---|---|---|\n");
        md.append(String.format(Locale.ROOT, "| 0 | %d | %d |%n", count(rows, r -> r.winnerSlot() == 0), rows.size()));
        md.append(String.format(Locale.ROOT, "| 1 | %d | %d |%n", count(rows, r -> r.winnerSlot() == 1), rows.size()));
        List<Long> all = rows.stream().map(Row::ticks).toList();
        md.append(String.format(
                Locale.ROOT,
                "%nAll matches: median %s, longest %s; %d did not end by conquest.%n",
                clock(median(all)),
                clock(Collections.max(all)),
                count(rows, r -> !r.ended())));
        return md.toString();
    }

    /** QA-12's criteria with AI-05.2, MAP-02.4 and AI-18.3 over {@code rows} of full pairings; the failures. */
    static List<String> judge(List<Pairing> pairings, List<Row> rows) {
        List<String> bad = new ArrayList<>();
        for (Row r : rows) {
            if (!r.failure().isEmpty()) {
                bad.add(r.pairing() + " seed " + r.seed() + ": " + r.failure());
            } else if (r.winnerSlot() < 0) {
                bad.add(r.pairing() + " seed " + r.seed() + ": no conquest");
            } else if (r.ticks() > LIMIT) {
                bad.add(r.pairing() + " seed " + r.seed() + ": ran past 45 minutes");
            }
        }
        long median = median(rows.stream().map(Row::ticks).toList());
        if (median < 20 * MINUTE || median > 35 * MINUTE) {
            bad.add("median match " + clock(median) + " is outside 20:00 to 35:00 (QA-12.1)");
        }
        for (Pairing p : pairings) {
            List<Row> r = of(rows, p.name());
            long a = count(r, Row::aWon);
            if (p.aOpening().isEmpty()) {
                if (a < 124) {
                    bad.add(p.a() + " won only " + a + " of 200 against " + p.b() + " (AI-05.2: 124)");
                }
            } else if (a < 60 || a > 140) {
                bad.add(p.a() + " won " + a + " of 200 against " + p.b() + " (QA-12.2: 60 to 140)");
            }
            for (int slot = 0; slot < 2; slot++) {
                int s = slot;
                long wins = count(r, x -> x.winnerSlot() == s);
                if (wins >= 121) {
                    bad.add("start " + slot + " won " + wins + " of 200 in " + p.name() + " (MAP-02.4: below 121)");
                }
            }
        }
        return bad;
    }

    /**
     * Arguments: the output directory, the seeds per pairing (200), a comma list of pairing names (all) and the
     * threads (the cores). Under 200 seeds or a subset of the pairings the run is a trial: it reports and fails on an
     * exception or a stall only, the thresholds being QA-12's over the full run.
     */
    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args.length > 0 ? args[0] : "build/reports/opening-balance");
        int seeds = args.length > 1 ? Integer.parseInt(args[1]) : SEEDS;
        List<String> only = args.length > 2 && !args[2].isBlank() ? List.of(args[2].split(",")) : List.of();
        int threads = args.length > 3
                ? Integer.parseInt(args[3])
                : Runtime.getRuntime().availableProcessors();
        List<Pairing> pairings = pairings().stream()
                .filter(p -> only.isEmpty() || only.contains(p.name()))
                .toList();
        long started = System.nanoTime();
        List<Row> rows = playAll(pairings, seeds, threads, dir);
        double minutes = (System.nanoTime() - started) / 6e10;
        Files.writeString(
                dir.resolve("summary.json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(rows));
        Files.writeString(dir.resolve("summary.md"), markdown(pairings, rows));
        for (Pairing p : pairings) {
            System.out.println(pairingLine(p, of(rows, p.name())));
        }
        System.out.printf(
                Locale.ROOT, "%d matches in %.1f minutes of wall time on %d threads%n", rows.size(), minutes, threads);
        List<String> bad = judge(pairings, rows);
        boolean full = seeds == SEEDS && pairings.size() == pairings().size();
        if (!full) {
            bad = bad.stream().filter(m -> m.contains(" seed ")).toList();
            System.out.println("a trial run: only exceptions, stalls and unfinished matches are judged");
        }
        for (String m : bad) {
            System.out.println("FAILED " + m);
        }
        if (!bad.isEmpty()) {
            System.exit(1);
        }
        System.out.println("openingBalance: every criterion holds; " + dir.resolve("summary.md"));
    }
}
