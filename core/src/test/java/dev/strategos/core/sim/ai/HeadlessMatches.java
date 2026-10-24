package dev.strategos.core.sim.ai;

import com.google.gson.GsonBuilder;
import dev.strategos.core.testkit.JournalFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The headless match runner (QA-11): plays bot against bot for each seed given, writes {@code summary.json} with each
 * match's telemetry and the journal of each failing seed beside it, and exits with 1 when any match failed (an
 * exception, a stall, or no winner by 30 game minutes). {@code ./gradlew :core:headlessMatches -Pseeds=1..10}.
 */
public final class HeadlessMatches {
    private HeadlessMatches() {}

    /** Seeds as "1..10", "3,5,8" or both mixed: "1..3,7". */
    static List<Long> seeds(String spec) {
        List<Long> out = new ArrayList<>();
        for (String part : spec.split(",", -1)) {
            String p = part.trim();
            if (p.contains("..")) {
                long a = Long.parseLong(p.substring(0, p.indexOf("..")));
                long b = Long.parseLong(p.substring(p.indexOf("..") + 2));
                for (long s = a; s <= b; s++) {
                    out.add(s);
                }
            } else if (!p.isEmpty()) {
                out.add(Long.parseLong(p));
            }
        }
        return out;
    }

    /** Runs {@code seeds} up to {@code limit} ticks each into {@code dir}; the summaries. */
    static List<HeadlessMatch.Summary> run(List<Long> seeds, long limit, Path dir) throws IOException {
        Files.createDirectories(dir);
        String script = HeadlessMatch.read("ai/first_loop.json");
        List<HeadlessMatch.Summary> out = new ArrayList<>();
        for (long seed : seeds) {
            HeadlessMatch.Result r = HeadlessMatch.run(seed, limit, script, tick -> {});
            out.add(r.summary());
            if (r.summary().failed()) {
                JournalFiles.write(dir.resolve("seed-" + seed + JournalFiles.EXTENSION), r.journal());
            }
        }
        Files.writeString(
                dir.resolve("summary.json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(out));
        return out;
    }

    public static void main(String[] args) throws IOException {
        List<Long> seeds = seeds(args.length > 0 ? args[0] : "1..10");
        Path dir = Path.of(args.length > 1 ? args[1] : "build/reports/headless");
        List<HeadlessMatch.Summary> all = run(seeds, HeadlessMatch.LIMIT, dir);
        int failed = 0;
        for (HeadlessMatch.Summary s : all) {
            System.out.println("seed " + s.seed() + ": "
                    + (s.failed()
                            ? "FAILED " + s.failure()
                            : "faction " + s.winner() + " won at " + s.ticks() / 1200 + ":"
                                    + String.format(java.util.Locale.ROOT, "%02d", s.ticks() / 20 % 60)));
            if (s.failed()) {
                failed++;
            }
        }
        System.out.println(all.size() - failed + " of " + all.size() + " matches ended with a winner; "
                + dir.resolve("summary.json"));
        if (failed > 0) {
            System.exit(1);
        }
    }
}
