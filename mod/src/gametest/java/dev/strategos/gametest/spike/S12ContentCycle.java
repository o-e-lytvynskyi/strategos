package dev.strategos.gametest.spike;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.strategos.core.content.TemplateProblem;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.content.ServerTemplates;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Spike S12 (spec §8.3, ADR-0012): the content cycle on the spike server. The world's data pack {@code strategos_s12}
 * holds twenty templates (four abstract bases over two levels of inheritance, four mixins, twelve units) and loads with
 * the server. Three {@code /reload}s are timed end to end as the command runs them: on the server thread, where
 * {@code MinecraftServer#reloadResources} blocks until its future is done (MinecraftServer.java:1547); each must go
 * through the reload listener and keep the set. Then one more timed reload enables {@code strategos_s12_errors}, whose
 * thirteen files plant twelve errors: it must be refused, keep the set, and report each planted error, and nothing
 * else, at its file and JSON path. The template work alone (read, parse, compile, lint) is timed too, three times.
 * Exit: every reload at most 2 s. Writes {@code results.md}, {@code results.json}, {@code env.json} and {@code DONE}
 * to {@code out/s12}.
 */
final class S12ContentCycle implements Spike {
    private static final Logger LOG = LoggerFactory.getLogger(S12ContentCycle.class);
    private static final long EXIT_NANOS = 2_000_000_000L;
    private static final int RUNS = 3;
    private static final int UNITS = 12;
    private static final String PACK = "file/strategos_s12";
    private static final String ERRORS_PACK = "file/strategos_s12_errors";
    private static final String ERRORS = "data/s12e/strategos/templates/unit/";

    /** A planted error: its file, the JSON pointer it must name, and a word of its message. */
    private record Planted(String file, String pointer, String says) {}

    private static final List<Planted> PLANTED = List.of(
            new Planted(ERRORS + "typo_key.json", "/UnitMotion/walkSped", "unknown key"),
            new Planted(ERRORS + "wrong_type.json", "/Body/width", "expected a number"),
            new Planted(ERRORS + "negative_speed.json", "/UnitMotion/walkSpeed", "positive"),
            new Planted(ERRORS + "unknown_mobility.json", "/UnitMotion/mobility", "unknown mobility"),
            new Planted(ERRORS + "missing_parent.json", "/parent", "unknown parent"),
            new Planted(ERRORS + "cycle_a.json", "/parent", "inheritance cycle"),
            new Planted(ERRORS + "missing_mixin.json", "/mixins/1", "unknown mixin"),
            new Planted(ERRORS + "bad_op.json", "/UnitMotion/walkSpeed/$op", "unknown $op"),
            new Planted(ERRORS + "unknown_entity.json", "/Body/entityType", "unknown entity type"),
            new Planted(ERRORS + "unknown_skin.json", "/Body/skin", "unknown skin"),
            new Planted(ERRORS + "garbled.json", "", "line"),
            new Planted(ERRORS + "not_an_object.json", "", "a template is a JSON object"));

    private final MinecraftServer server;
    private final List<Long> reloads = new ArrayList<>();
    private final List<Long> templateWork = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private final List<String> found = new ArrayList<>();
    private long errorsReload = -1;
    private int units;
    private int templates;

    S12ContentCycle(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public void preTick() {}

    @Override
    public boolean postTick() {
        StrategosServer strategos = StrategosServer.get();
        ServerTemplates set = strategos.templates();
        templates = set.set().all().size();
        units = (int) set.set().all().stream()
                .map(UnitTemplate::id)
                .filter(id -> id.value().startsWith("s12:"))
                .count();
        expect(units == UNITS, "the server started with " + units + " S12 units: " + set.outcome());
        Collection<String> selected = List.copyOf(server.getPackRepository().getSelectedIds());
        expect(selected.contains(PACK) && !selected.contains(ERRORS_PACK), "the selected packs are " + selected);
        String version = set.set().version();
        for (int k = 0; k < RUNS; k++) {
            long loads = set.loads();
            reloads.add(reload(selected));
            expect(
                    set.loads() == loads + 1,
                    "reload " + k + " loaded the templates " + (set.loads() - loads) + " times");
            expect(set.set().version().equals(version), "reload " + k + " changed the set: " + set.outcome());
        }
        for (int k = 0; k < RUNS; k++) {
            long start = System.nanoTime();
            List<TemplateProblem> problems = new ArrayList<>();
            ServerTemplates.Outcome outcome = strategos.loadTemplates(strategos.packTemplates(problems), problems);
            templateWork.add(System.nanoTime() - start);
            expect(outcome.accepted(), "the template work alone was refused: " + outcome.message());
        }
        List<String> withErrors = new ArrayList<>(selected);
        withErrors.add(ERRORS_PACK);
        long loads = set.loads();
        errorsReload = reload(withErrors);
        expect(set.loads() == loads + 1, "the errors reload loaded the templates " + (set.loads() - loads) + " times");
        expect(set.outcome().startsWith("refused"), "the errors reload was not refused: " + set.outcome());
        expect(set.set().version().equals(version), "the errors reload changed the set");
        checkProblems(set.problems());
        return true;
    }

    /** Every planted error at its file and pointer, and no error that was not planted. */
    private void checkProblems(List<TemplateProblem> problems) {
        for (TemplateProblem p : problems) {
            found.add(p.severity() + " " + p);
        }
        for (Planted planted : PLANTED) {
            expect(
                    problems.stream().anyMatch(p -> is(p, planted)),
                    "no error at " + planted.file() + " " + planted.pointer() + " saying '" + planted.says() + "'");
        }
        for (TemplateProblem p : problems) {
            if (p.severity() == TemplateProblem.Severity.ERROR
                    && PLANTED.stream().noneMatch(planted -> is(p, planted))) {
                failures.add("an error nobody planted: " + p);
            }
        }
    }

    private static boolean is(TemplateProblem p, Planted planted) {
        return p.severity() == TemplateProblem.Severity.ERROR
                && p.file().equals(planted.file())
                && p.pointer().equals(planted.pointer())
                && p.message().contains(planted.says());
    }

    /** A {@code /reload} with {@code packs} selected, as the command runs it on the server thread; its nanoseconds. */
    private long reload(Collection<String> packs) {
        long start = System.nanoTime();
        server.reloadResources(packs).join();
        long took = System.nanoTime() - start;
        LOG.info("S12: reload of {} took {} ms", packs, ms(took));
        return took;
    }

    private void expect(boolean ok, String failure) {
        if (!ok) {
            LOG.error("S12: {}", failure);
            failures.add(failure);
        }
    }

    @Override
    public void finish(boolean complete) {
        SpikeReport report = new SpikeReport(SpikeReport.dirFor("s12"));
        long worst = errorsReload;
        for (long r : reloads) {
            worst = Math.max(worst, r);
        }
        boolean fast = worst >= 0 && worst <= EXIT_NANOS;
        boolean pass = fast && failures.isEmpty() && reloads.size() == RUNS;
        StringBuilder md = new StringBuilder("# S12 content cycle\n\n");
        md.append(String.format(
                Locale.ROOT,
                "Templates in the set: %d, of which %d S12 units (20 S12 files: 4 abstract bases, 4 mixins, 12"
                        + " units).%n%n| Run | ms |%n|---|---|%n",
                templates,
                units));
        for (int k = 0; k < reloads.size(); k++) {
            md.append("| /reload ")
                    .append(k + 1)
                    .append(" | ")
                    .append(ms(reloads.get(k)))
                    .append(" |\n");
        }
        md.append("| /reload with the errors pack | ").append(ms(errorsReload)).append(" |\n");
        for (int k = 0; k < templateWork.size(); k++) {
            md.append("| template work alone ")
                    .append(k + 1)
                    .append(" | ")
                    .append(ms(templateWork.get(k)))
                    .append(" |\n");
        }
        md.append("\n## Problems of the errors reload\n\n");
        found.forEach(f -> md.append("- `").append(f).append("`\n"));
        md.append("\n## Verdict\n\n- reloads ≤ 2 s: ")
                .append(fast ? "yes" : "no")
                .append(" (worst ")
                .append(ms(worst))
                .append(" ms)\n- every planted error at its file and path, none other: ")
                .append(failures.isEmpty() ? "yes" : "no")
                .append('\n');
        failures.forEach(f -> md.append("- failure: ").append(f).append('\n'));
        md.append("- exit: ").append(pass ? "PASS" : "FAIL").append('\n');
        JsonObject json = new JsonObject();
        JsonArray runs = new JsonArray();
        reloads.forEach(runs::add);
        json.add("reloadNanos", runs);
        json.addProperty("errorsReloadNanos", errorsReload);
        JsonArray work = new JsonArray();
        templateWork.forEach(work::add);
        json.add("templateWorkNanos", work);
        json.addProperty("templates", templates);
        json.addProperty("s12Units", units);
        JsonArray problems = new JsonArray();
        found.forEach(problems::add);
        json.add("problems", problems);
        JsonArray failed = new JsonArray();
        failures.forEach(failed::add);
        json.add("failures", failed);
        json.addProperty("pass", pass);
        report.writeText("results.md", md.toString());
        report.writeText(
                "results.json", new GsonBuilder().setPrettyPrinting().create().toJson(json) + "\n");
        report.writeEnv();
        LOG.info("S12: {}; worst reload {} ms; failures {}", pass ? "PASS" : "FAIL", ms(worst), failures);
        if (complete) {
            report.done();
        }
    }

    private static String ms(long nanos) {
        return nanos < 0 ? "" : String.format(Locale.ROOT, "%.1f", nanos / 1e6);
    }
}
