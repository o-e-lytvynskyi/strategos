package dev.strategos.core.sim.ai;

import com.google.gson.GsonBuilder;
import dev.strategos.core.content.CivDef;
import dev.strategos.core.content.Scenario;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.CivId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import org.jspecify.annotations.Nullable;

/**
 * QA-16's balance matrix, run by {@code :core:balanceMatrix}: AI-versus-AI whole matches on the first skirmish map with
 * fog and conquest, for every ordered pair of the template set's civilisations at each level (normal and hard; Easy is left out, see {@link #LEVELS}; both
 * sides at the same level), the sides swapped on odd seeds. It reports wins per side and per start slot, match length
 * and phase times, flags a pairing outside 80 to 120 wins of 200 (a mirror per start slot), and lists what changed in
 * {@code docs/balance/sheet.csv} since the previous run. The process exits 1 when anything is flagged.
 */
public final class BalanceMatrix {
    /** QA-16's seeds per pairing. */
    static final int SEEDS = 200;

    /** A match's default limit: 90 game minutes. */
    static final int LIMIT_MINUTES = 90;

    /**
     * The levels the matrix plays: Normal and Hard. Easy is left out (the owner, 2026-11-26): two Easy players end only
     * 37 of 200 matches in 90 minutes, as Easy has no mop-up, and Easy is meant to lose to a person, not to beat an Easy
     * opponent; teaching Easy to finish a won match is M11's (AI-12). The aisoak's L3 samples still rotate all three.
     */
    static final List<String> LEVELS = List.of("normal", "hard");

    private BalanceMatrix() {}

    /** One pairing: civilisation A against civilisation B, both at {@code level}; a mirror when they are the same. */
    record Pairing(String name, CivId a, CivId b, String level) {
        boolean mirror() {
            return a.equals(b);
        }

        /** The runner's form of this pairing: both sides at the level, the script's weights drawing the openings. */
        OpeningBalance.Pairing runner() {
            return new OpeningBalance.Pairing(name, slug(a), level, "", slug(b), level, "");
        }
    }

    /** One match's record. Slots are start slots; winner -1 when the match did not end by conquest. */
    record Row(
            String pairing,
            long seed,
            int aSlot,
            int winnerSlot,
            boolean aWon,
            long ticks,
            List<Long> townAt, // by slot, -1 never
            List<Long> cityAt, // by slot, -1 never
            String failure) {
        boolean ended() {
            return failure.isEmpty() && winnerSlot >= 0;
        }
    }

    private static String slug(CivId id) {
        String v = id.value();
        return v.substring(v.indexOf(':') + 1);
    }

    /** Every ordered pair of {@code civs} at every level, civilisations in the outer loops. */
    static List<Pairing> pairings(List<CivId> civs) {
        List<Pairing> out = new ArrayList<>();
        for (CivId a : civs) {
            for (CivId b : civs) {
                for (String level : LEVELS) {
                    out.add(new Pairing(slug(a) + "-" + slug(b) + "-" + level, a, b, level));
                }
            }
        }
        return out;
    }

    static List<CivId> civsOf(TemplateSet set) {
        return set.civs().stream().map(CivDef::id).toList();
    }

    /** The lowest win count inside the band: 40 % of {@code seeds} (80 of 200). */
    static int bandLow(int seeds) {
        return seeds * 2 / 5;
    }

    /** The highest win count inside the band: 60 % of {@code seeds} (120 of 200). */
    static int bandHigh(int seeds) {
        return seeds * 3 / 5;
    }

    /**
     * The flags of {@code p} over its {@code rows}: failures, and wins outside the band, per start slot for a mirror and
     * per side otherwise.
     */
    static List<String> flags(Pairing p, List<Row> rows, int seeds) {
        List<String> bad = new ArrayList<>();
        for (Row r : rows) {
            if (!r.failure().isEmpty()) {
                bad.add(p.name() + " seed " + r.seed() + ": " + r.failure());
            }
        }
        int lo = bandLow(seeds);
        int hi = bandHigh(seeds);
        for (int side = 0; side < 2; side++) {
            int s = side;
            long wins = p.mirror()
                    ? rows.stream().filter(r -> r.winnerSlot() == s).count()
                    : rows.stream()
                            .filter(r -> r.winnerSlot() >= 0 && (r.winnerSlot() == r.aSlot()) == (s == 0))
                            .count();
            if (wins < lo || wins > hi) {
                String who = p.mirror()
                        ? "start " + side
                        : (side == 0 ? slug(p.a()) : slug(p.b())) + " as side " + (side == 0 ? "A" : "B");
                bad.add(String.format(
                        Locale.ROOT, "%s: %s won %d of %d, outside %d to %d", p.name(), who, wins, seeds, lo, hi));
            }
        }
        return bad;
    }

    /** Plays {@code seed} of {@code p} on the first skirmish map for at most {@code limit} ticks. */
    static Row play(
            Pairing p,
            long seed,
            HeadlessMatch.Ground ground,
            Scenario scenario,
            String skirmish,
            long limit,
            TemplateSet set,
            Path dir) {
        OpeningBalance.Played played =
                OpeningBalance.playOn(p.runner(), seed, ground, scenario, skirmish, dir, limit, set, p.a(), p.b());
        OpeningBalance.Row r = played.row();
        return new Row(
                p.name(),
                seed,
                r.aSlot(),
                r.winnerSlot(),
                r.aWon(),
                r.ticks(),
                r.townAt(),
                played.cityAt(),
                r.failure());
    }

    /** Plays seeds {@code 1..seeds} of every pairing over {@code threads} threads; the rows in pairing and seed order. */
    static List<Row> playAll(List<Pairing> pairings, int seeds, long limit, int threads, TemplateSet set, Path dir)
            throws Exception {
        Files.createDirectories(dir);
        String skirmish = HeadlessMatch.read("ai/skirmish.json");
        HeadlessMatch.Ground ground = HeadlessMatch.Ground.firstSkirmish();
        Scenario scenario = OpeningBalance.firstSkirmish();
        List<Callable<Row>> jobs = new ArrayList<>();
        for (Pairing p : pairings) {
            for (long seed = 1; seed <= seeds; seed++) {
                long s = seed;
                jobs.add(() -> play(p, s, ground, scenario, skirmish, limit, set, dir));
            }
        }
        return OpeningBalance.inParallel(jobs, threads, "balanceMatrix");
    }

    // ---- the balance sheet diff ----

    /** The rows of a sheet csv by {@code template|path} to the value column; quoted fields may hold commas. */
    static Map<String, String> sheetRows(String csv) {
        Map<String, String> out = new LinkedHashMap<>();
        boolean header = true;
        for (String line : (Iterable<String>) csv.lines()::iterator) {
            if (header) {
                header = false;
                continue;
            }
            if (line.isBlank()) {
                continue;
            }
            List<String> f = fields(line);
            if (f.size() >= 3) {
                out.put(f.get(0) + " | " + f.get(1), f.get(2));
            }
        }
        return out;
    }

    private static List<String> fields(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }

    /** The sheet's changes from {@code previous} to {@code current}: one line per row, {@code key: old -> new}. */
    static List<String> sheetDiff(String previous, String current) {
        Map<String, String> before = sheetRows(previous);
        Map<String, String> after = sheetRows(current);
        Set<String> keys = new LinkedHashSet<>(before.keySet());
        keys.addAll(after.keySet());
        List<String> out = new ArrayList<>();
        for (String k : keys) {
            String o = before.get(k);
            String n = after.get(k);
            if (o == null) {
                out.add(k + ": (new) -> " + n);
            } else if (n == null) {
                out.add(k + ": " + o + " -> (removed)");
            } else if (!o.equals(n)) {
                out.add(k + ": " + o + " -> " + n);
            }
        }
        return out;
    }

    // ---- the summary ----

    private static long count(List<Row> rows, java.util.function.Predicate<Row> p) {
        return rows.stream().filter(p).count();
    }

    private static String phase(List<Row> rows, boolean city, boolean sideB) {
        List<Long> at = rows.stream()
                .map(r -> (city ? r.cityAt() : r.townAt()).get(sideB ? 1 - r.aSlot() : r.aSlot()))
                .filter(t -> t >= 0)
                .toList();
        return at.isEmpty() ? "-" : OpeningBalance.clock(OpeningBalance.median(at)) + " (" + at.size() + ")";
    }

    static List<Row> of(List<Row> rows, String pairing) {
        return rows.stream().filter(r -> r.pairing().equals(pairing)).toList();
    }

    static String markdown(
            List<Pairing> pairings,
            List<Row> rows,
            int seeds,
            long limit,
            List<String> flags,
            @Nullable List<String> sheet) {
        StringBuilder md = new StringBuilder("# QA-16: balance matrix\n\n");
        md.append(String.format(
                Locale.ROOT,
                "First skirmish map, fog on, conquest, limit %s; %d seeds per pairing, sides swapped on odd seeds; band %d to %d wins.%n%n",
                OpeningBalance.clock(limit),
                seeds,
                bandLow(seeds),
                bandHigh(seeds)));
        md.append(
                "## Pairings\n\n| Pairing | A wins | B wins | Start 0 wins | Start 1 wins | Unfinished | Median | Longest "
                        + "| A Town | B Town | A City | B City |\n|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (Pairing p : pairings) {
            List<Row> r = of(rows, p.name());
            List<Long> ticks = r.stream().map(Row::ticks).toList();
            md.append(String.format(
                    Locale.ROOT,
                    "| %s | %d | %d | %d | %d | %d | %s | %s | %s | %s | %s | %s |%n",
                    p.name(),
                    count(r, Row::aWon),
                    count(r, x -> x.winnerSlot() >= 0 && !x.aWon()),
                    count(r, x -> x.winnerSlot() == 0),
                    count(r, x -> x.winnerSlot() == 1),
                    count(r, x -> !x.ended()),
                    OpeningBalance.clock(OpeningBalance.median(ticks)),
                    OpeningBalance.clock(ticks.isEmpty() ? 0 : Collections.max(ticks)),
                    phase(r, false, false),
                    phase(r, false, true),
                    phase(r, true, false),
                    phase(r, true, true)));
        }
        md.append("\nPhase columns: median of those reached (how many reached). Seeds: 1 to ")
                .append(seeds)
                .append(".\n");
        md.append("\n## Flags\n\n");
        if (flags.isEmpty()) {
            md.append("none\n");
        }
        for (String f : flags) {
            md.append("- ").append(f).append('\n');
        }
        md.append("\n## Balance sheet since the previous run\n\n");
        if (sheet == null) {
            md.append("no previous run\n");
        } else if (sheet.isEmpty()) {
            md.append("no changed rows\n");
        }
        if (sheet != null) {
            for (String c : sheet) {
                md.append("- ").append(c).append('\n');
            }
        }
        return md.toString();
    }

    /**
     * Arguments: the output directory, the seeds per pairing (200), the threads (the cores), the limit in game minutes
     * (90) and the balance sheet's csv. Unfinished matches are counted and reported, never dropped.
     */
    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args.length > 0 ? args[0] : "build/reports/balance-matrix");
        int seeds = args.length > 1 ? Integer.parseInt(args[1]) : SEEDS;
        int threads = args.length > 2
                ? Integer.parseInt(args[2])
                : Runtime.getRuntime().availableProcessors();
        long limit = (args.length > 3 ? Long.parseLong(args[3]) : LIMIT_MINUTES) * 1_200;
        Path sheet = Path.of(args.length > 4 ? args[4] : "../docs/balance/sheet.csv");
        TemplateSet set = TemplateSet.builtin();
        List<Pairing> pairings = pairings(civsOf(set));
        Files.createDirectories(dir);
        List<String> changes = sheetChanges(dir, sheet);
        long started = System.nanoTime();
        List<Row> rows = playAll(pairings, seeds, limit, threads, set, dir);
        double minutes = (System.nanoTime() - started) / 6e10;
        List<String> flags = new ArrayList<>();
        for (Pairing p : pairings) {
            flags.addAll(flags(p, of(rows, p.name()), seeds));
        }
        Files.writeString(
                dir.resolve("summary.json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(rows));
        String md = markdown(pairings, rows, seeds, limit, flags, changes);
        Files.writeString(dir.resolve("summary.md"), md);
        System.out.println(md);
        System.out.printf(
                Locale.ROOT, "%d matches in %.1f minutes of wall time on %d threads%n", rows.size(), minutes, threads);
        for (String f : flags) {
            System.out.println("FLAGGED " + f);
        }
        if (!flags.isEmpty()) {
            System.exit(1);
        }
        System.out.println("balanceMatrix: no pairing flagged; " + dir.resolve("summary.md"));
    }

    /** Diffs {@code sheet} against the previous run's copy in {@code dir} (null: none), then keeps this run's copy. */
    private static @Nullable List<String> sheetChanges(Path dir, Path sheet) throws IOException {
        Path copy = dir.resolve("sheet.csv");
        String now = Files.readString(sheet);
        List<String> changes = Files.exists(copy) ? sheetDiff(Files.readString(copy), now) : null;
        Files.writeString(copy, now);
        return changes;
    }
}
