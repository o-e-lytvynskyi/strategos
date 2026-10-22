package dev.strategos.gametest.spike;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.neoforged.fml.ModList;

/** A spike's output (spec 8.3): {@code results.csv}, {@code results.md}, {@code env.json}, then {@code DONE}. */
final class SpikeReport {
    record Row(
            String config,
            String body,
            int count,
            String state,
            int ticks,
            double p50,
            double p95,
            double p99,
            double max,
            double stepP50,
            double adapterP50,
            double heapMb,
            int entities) {}

    private final Path dir;
    private final List<Row> rows = new ArrayList<>();

    SpikeReport(Path dir) {
        this.dir = dir;
    }

    /** The output directory of a spike: {@code <out>/<id>}, with {@code -lithium} when Lithium is loaded. */
    static Path dirFor(String id) {
        String out = System.getProperty("strategos.spike.out", "spike-out");
        return Path.of(out, id + (lithium() ? "-lithium" : ""));
    }

    static boolean lithium() {
        return ModList.get().isLoaded("lithium");
    }

    void add(Row row) {
        rows.add(row);
    }

    List<Row> rows() {
        return rows;
    }

    /** The p50 of the first baseline row, which the per-body costs subtract. */
    double baselineP50() {
        return rows.stream()
                .filter(r -> r.body().equals("none"))
                .findFirst()
                .map(Row::p50)
                .orElse(Double.NaN);
    }

    double perBodyUs(Row r) {
        return r.count() == 0 ? Double.NaN : (r.p50() - baselineP50()) * 1000 / r.count();
    }

    /** For production bodies: the cost a body adds beyond the core's step. */
    double beyondCoreUs(Row r) {
        return r.count() == 0 || Double.isNaN(r.stepP50())
                ? Double.NaN
                : (r.p50() - baselineP50() - r.stepP50()) * 1000 / r.count();
    }

    void writeCsv() {
        StringBuilder csv = new StringBuilder(
                "config,body,count,state,ticks,tick_p50_ms,tick_p95_ms,tick_p99_ms,tick_max_ms,step_p50_ms,"
                        + "adapter_p50_ms,per_body_us,beyond_core_us,heap_mb,entities\n");
        for (Row r : rows) {
            csv.append(String.join(
                            ",",
                            r.config(),
                            r.body(),
                            String.valueOf(r.count()),
                            r.state(),
                            String.valueOf(r.ticks()),
                            num(r.p50()),
                            num(r.p95()),
                            num(r.p99()),
                            num(r.max()),
                            num(r.stepP50()),
                            num(r.adapterP50()),
                            num(perBodyUs(r)),
                            num(beyondCoreUs(r)),
                            num(r.heapMb()),
                            String.valueOf(r.entities())))
                    .append('\n');
        }
        write("results.csv", csv.toString());
    }

    void writeMarkdown(String title, List<String> verdicts) {
        StringBuilder md = new StringBuilder("# " + title + "\n\n");
        md.append("| Config | Body | Count | State | p50 ms | p95 ms | p99 ms | max ms | step p50 ms | adapter p50 ms"
                + " | µs/body | beyond core µs | heap MB | entities |\n");
        md.append("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (Row r : rows) {
            md.append(String.format(
                    Locale.ROOT,
                    "| %s | %s | %d | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %d |%n",
                    r.config(),
                    r.body(),
                    r.count(),
                    r.state(),
                    num(r.p50()),
                    num(r.p95()),
                    num(r.p99()),
                    num(r.max()),
                    num(r.stepP50()),
                    num(r.adapterP50()),
                    num(perBodyUs(r)),
                    num(beyondCoreUs(r)),
                    num(r.heapMb()),
                    r.entities()));
        }
        md.append('\n');
        verdicts.forEach(v -> md.append("- ").append(v).append('\n'));
        write("results.md", md.toString());
    }

    void writeEnv() {
        JsonObject env = new JsonObject();
        env.addProperty(
                "jvm",
                System.getProperty("java.vm.name") + " " + System.getProperty("java.version") + " ("
                        + System.getProperty("java.vendor") + ")");
        env.addProperty(
                "os",
                System.getProperty("os.name") + " " + System.getProperty("os.version") + " "
                        + System.getProperty("os.arch"));
        env.addProperty("cpus", Runtime.getRuntime().availableProcessors());
        env.addProperty("maxHeapMb", Runtime.getRuntime().maxMemory() / (1024 * 1024));
        env.addProperty("neoforge", System.getProperty("strategos.neoforge.version", "unknown"));
        env.addProperty("lithium", lithium());
        env.addProperty("modernfix", ModList.get().isLoaded("modernfix"));
        env.addProperty("ferritecore", ModList.get().isLoaded("ferritecore"));
        write("env.json", new GsonBuilder().setPrettyPrinting().create().toJson(env) + "\n");
    }

    void done() {
        write("DONE", "done\n");
    }

    /** A file of the spike's own, beside the shared ones. */
    void writeText(String name, String text) {
        write(name, text);
    }

    private void write(String name, String text) {
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(name), text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write the spike's " + name, e);
        }
    }

    static String num(double v) {
        return Double.isNaN(v) ? "" : String.format(Locale.ROOT, "%.3f", v);
    }
}
