package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;
import static dev.strategos.gametest.suites.Units.handles;
import static dev.strategos.gametest.suites.Units.match;
import static dev.strategos.gametest.suites.Units.move;
import static dev.strategos.gametest.suites.Units.spawn;
import static dev.strategos.gametest.suites.Units.unit;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.strategos.core.content.CorePaths;
import dev.strategos.core.content.RawTemplate;
import dev.strategos.core.content.TemplateProblem;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.stats.StatBreakdown;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.content.ServerTemplates;
import dev.strategos.mod.server.match.MatchRuntime;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * DATA-03.3 and .4 in a real server (spec §7.3): a reload's templates handed over as the listener's apply hands them.
 * A set with errors, or one that drops a live unit's template, is refused and the server keeps its set; a good one
 * changes a walking unit's speed from the next tick. Each runs alone in its batch: the server's set is server-wide.
 * UNIT-04.3 reads a worker's and a spearman's values from the bundled set, in a batch of its own.
 */
public final class ContentTests {
    private static final String UNITS = "data/strategos/strategos/templates/unit/";

    private ContentTests() {}

    /**
     * DATA-03.3: a reload with a compile error, a lint error and a file that did not parse is refused, each problem
     * naming its file and JSON path; the server's set and id table stay, and so does a running match's set.
     */
    @StrategosTest(batch = "content_errors", solo = true, timeoutTicks = 100)
    public static void reloadWithErrorsKeepsSet(GameTestHelper h) {
        StrategosServer server = StrategosServer.get();
        String before = server.templates().set().version();
        int table = server.templates().table().size();
        MatchRuntime rt = match(h);
        List<TemplateProblem> problems = new ArrayList<>();
        List<RawTemplate> raws = bundled(server, problems);
        raws.add(raw("broken_speed", """
                {"parent": "strategos:template/unit_base", "Identity": {"name": "broken"},
                 "UnitMotion": {"walkSpeed": "fast"}}"""));
        raws.add(raw("broken_skin", """
                {"parent": "strategos:template/unit_base", "Identity": {"name": "broken"},
                 "Body": {"skin": "strategos:units/none"}}"""));
        problems.add(TemplateProblem.error(UNITS + "garbled.json", "", "does not parse: unterminated object"));

        ServerTemplates.Outcome outcome = server.loadTemplates(raws, problems);

        check(!outcome.accepted(), "a set with errors was accepted: " + outcome.message());
        named(outcome, UNITS + "broken_speed.json", "/UnitMotion/walkSpeed");
        named(outcome, UNITS + "broken_skin.json", "/Body/skin");
        named(outcome, UNITS + "garbled.json", "");
        check(
                server.templates().set().version().equals(before),
                "the set changed: " + server.templates().set());
        check(
                server.templates().table().size() == table,
                "the id table grew: " + server.templates().table());
        h.startSequence()
                .thenExecuteAfter(
                        2,
                        () -> check(
                                core(rt).queries().templates().version().equals(before),
                                "the match swapped to " + core(rt).queries().templates()))
                .thenSucceed();
    }

    /**
     * DATA-03.3: a reload that drops the template of a live unit of a running match is refused with the template and
     * the match named; the unit keeps its template and the server its set.
     */
    @StrategosTest(batch = "content_used", solo = true, timeoutTicks = 100)
    public static void reloadRemovingUsedTemplateRefused(GameTestHelper h) {
        StrategosServer server = StrategosServer.get();
        String before = server.templates().set().version();
        MatchRuntime rt = match(h);
        spawn(rt, 16.5, 16.5, 1, 0);
        h.startSequence()
                .thenWaitUntil(() -> check(handles(rt).length == 1, "no unit yet"))
                .thenExecute(() -> {
                    List<TemplateProblem> problems = new ArrayList<>();
                    List<RawTemplate> raws = bundled(server, problems);
                    raws.removeIf(r -> r.id().equals(TemplateSet.DEBUG_WALKER));

                    ServerTemplates.Outcome outcome = server.loadTemplates(raws, problems);

                    check(!outcome.accepted(), "a set without a live unit's template was accepted");
                    check(
                            outcome.message().contains(TemplateSet.DEBUG_WALKER.value())
                                    && outcome.message()
                                            .contains("match " + rt.id().value()),
                            "the refusal names neither the template nor the match: " + outcome.message());
                    check(server.templates().set().version().equals(before), "the set changed");
                })
                .thenExecuteAfter(2, () -> {
                    check(
                            unit(rt, handles(rt)[0]).template().equals(TemplateSet.DEBUG_WALKER),
                            "the unit's template changed");
                    check(core(rt).queries().templates().version().equals(before), "the match swapped");
                })
                .thenSucceed();
    }

    /** A building reference alone must prevent an otherwise valid reload from retiring its template (#24). */
    @StrategosTest(batch = "content_used_building", solo = true)
    public static void reloadMissingBuildingKeepsSetAndTable(GameTestHelper h) {
        StrategosServer server = StrategosServer.get();
        MatchRuntime rt = match(h);
        TemplateId house = new TemplateId("strategos:building/house");
        rt.submit(PlayerSlot.ADMIN, new dev.strategos.core.protocol.SpawnBuilding(house, new FactionSlot(0), 8, 8, 0));
        var before = server.templates().set();
        var table = server.templates().table();
        h.startSequence()
                .thenWaitUntil(() -> check(core(rt).queries().buildings().size() == 1, "no building yet"))
                .thenExecute(() -> {
                    List<TemplateProblem> problems = new ArrayList<>();
                    List<RawTemplate> raws = bundled(server, problems);
                    raws.removeIf(raw -> raw.id().equals(house));
                    var outcome = server.loadTemplates(raws, problems);
                    check(!outcome.accepted(), "reload dropped an occupied building template");
                    check(
                            outcome.message().contains(house.value())
                                    && outcome.message().contains("in use"),
                            outcome.message());
                    check(server.templates().set() == before, "server set changed after refusal");
                    check(server.templates().table().equals(table), "server id table changed after refusal");
                })
                .thenExecuteAfter(2, () -> {
                    check(core(rt).queries().templates() == before, "match swapped after refusal");
                    check(core(rt).queries().buildings().getFirst().template().equals(house), "building changed");
                })
                .thenSucceed();
    }

    @StrategosTest(batch = "content_ended", solo = true)
    public static void endedMatchDoesNotBlockReload(GameTestHelper h) {
        terminalMatchDoesNotBlockReload(h, false);
    }

    @StrategosTest(batch = "content_faulted", solo = true)
    public static void faultedMatchDoesNotBlockReload(GameTestHelper h) {
        terminalMatchDoesNotBlockReload(h, true);
    }

    private static void terminalMatchDoesNotBlockReload(GameTestHelper h, boolean faulted) {
        StrategosServer server = StrategosServer.get();
        MatchRuntime rt = match(h);
        TemplateId house = new TemplateId("strategos:building/house");
        rt.submit(PlayerSlot.ADMIN, new dev.strategos.core.protocol.SpawnBuilding(house, new FactionSlot(0), 4, 4, 0));
        h.startSequence()
                .thenWaitUntil(() -> check(core(rt).queries().buildings().size() == 1, "no building yet"))
                .thenExecute(() -> {
                    if (faulted) {
                        rt.fault(new IllegalStateException("terminal reload regression"), "test");
                    } else {
                        server.matches().end(rt.id());
                    }
                    var before = core(rt).queries().templates();
                    List<TemplateProblem> problems = new ArrayList<>();
                    List<RawTemplate> raws = bundled(server, problems);
                    raws.removeIf(r -> r.id().equals(house));
                    restoringBundled(server, () -> {
                        var outcome = server.loadTemplates(raws, problems);
                        check(outcome.accepted(), "terminal match blocked reload: " + outcome.message());
                        check(server.templates().set().byId(house) == null, "server retained the removed template");
                        check(core(rt).queries().templates() == before, "terminal match's frozen set changed");
                        check(
                                core(rt).queries()
                                        .buildings()
                                        .getFirst()
                                        .template()
                                        .equals(house),
                                "frozen queries failed");
                    });
                })
                .thenSucceed();
    }

    /** A reference introduced after publication must fault before the queued swap advances the match. */
    @StrategosTest(batch = "content_swap_race", solo = true)
    public static void invalidatedPublishedSwapFaultsMatch(GameTestHelper h) {
        StrategosServer server = StrategosServer.get();
        MatchRuntime rt = dev.strategos.gametest.MatchFixture.create(
                h,
                dev.strategos.gametest.MatchFixture.options(dev.strategos.core.testkit.SimHarness.DEFAULT_SEED, h)
                        .withFaultPolicy(dev.strategos.mod.server.match.FaultPolicy.CONTAIN));
        TemplateId house = new TemplateId("strategos:building/house");
        h.startSequence()
                .thenExecute(() -> {
                    server.matches().pause(rt.id());
                    List<TemplateProblem> problems = new ArrayList<>();
                    List<RawTemplate> raws = bundled(server, problems);
                    raws.removeIf(r -> r.id().equals(house));
                    restoringBundled(server, () -> {
                        var outcome = server.loadTemplates(raws, problems);
                        check(outcome.accepted(), "unused template removal was refused: " + outcome.message());
                        var c = core(rt);
                        long tick = c.queries().tick();
                        // Inject the boundary race deterministically, before the adapter delivers its queued swap.
                        c.step(new dev.strategos.core.sim.TickInputs(
                                tick + 1,
                                List.of(new dev.strategos.core.sim.CommandEnvelope(
                                        PlayerSlot.ADMIN,
                                        1000,
                                        tick,
                                        new dev.strategos.core.protocol.SpawnBuilding(
                                                house, new FactionSlot(0), 4, 4, 0)))));
                        check(c.queries().buildings().size() == 1, "race did not introduce the reference");
                        long before = c.queries().tick();
                        server.faults().run(rt, "published swap", () -> rt.tick(h.getLevel()));
                        check(rt.lifecycle() == dev.strategos.core.sim.MatchLifecycle.FAULTED, "match kept running");
                        check(c.queries().tick() == before, "invalid swap advanced simulation");
                        check(
                                rt.faultMessage() != null && rt.faultMessage().contains(house.value()),
                                "fault omitted id");
                        check(c.queries().buildings().getFirst().template().equals(house), "old state unusable");
                    });
                })
                .thenSucceed();
    }

    /**
     * DATA-03.4: a walking unit's step is the bundled walk speed until a reload raises it, and the new speed from the
     * next tick on; the bundled set loads back at the end, at the same version.
     */
    @StrategosTest(batch = "content_speed", solo = true, timeoutTicks = 300)
    public static void reloadChangesSpeedNextTick(GameTestHelper h) {
        StrategosServer server = StrategosServer.get();
        String before = server.templates().set().version();
        double bundledSpeed = speed(TemplateSet.builtin());
        double faster = 0.375;
        MatchRuntime rt = match(h);
        spawn(rt, 4.5, 16.5, 1, 0);
        double[] x = new double[2];
        h.startSequence()
                .thenWaitUntil(() -> check(handles(rt).length == 1, "no unit yet"))
                .thenExecute(() -> move(rt, handles(rt)[0], 28.5, 16.5))
                .thenWaitUntil(() -> {
                    double now = x(rt);
                    double step = now - x[0];
                    x[0] = now;
                    check(Math.abs(step - bundledSpeed) < 1e-6, "not walking at the bundled speed yet: " + step);
                })
                .thenExecute(() -> {
                    List<TemplateProblem> problems = new ArrayList<>();
                    List<RawTemplate> raws = withWalkerSpeed(bundled(server, problems), faster);
                    ServerTemplates.Outcome outcome = server.loadTemplates(raws, problems);
                    check(outcome.accepted(), "the faster set was refused: " + outcome.message() + " " + problems);
                    x[1] = x(rt);
                })
                .thenExecuteAfter(1, () -> {
                    double step = x(rt) - x[1];
                    check(Math.abs(step - faster) < 1e-6, "the tick after the reload stepped " + step);
                    check(
                            core(rt).queries()
                                    .templates()
                                    .version()
                                    .equals(server.templates().set().version()),
                            "the match runs " + core(rt).queries().templates());
                    List<TemplateProblem> problems = new ArrayList<>();
                    ServerTemplates.Outcome back = server.loadTemplates(bundled(server, problems), problems);
                    check(back.accepted(), "the bundled set did not load back: " + back.message());
                    check(server.templates().set().version().equals(before), "the bundled set loads back as another");
                })
                .thenSucceed();
    }

    /**
     * UNIT-04.3: a worker and a spearman spawned from the server's bundled set carry their templates' values, read
     * through the modifier pipeline: the worker's 50 hit points, flight of 8 blocks and no attack, the spearman's 100,
     * none and 3 hack; and only the worker gathers and builds.
     */
    @StrategosTest(batch = "content_units", timeoutTicks = 100)
    public static void spawnedWorkerAndSpearmanReadTheirStats(GameTestHelper h) {
        MatchRuntime rt = match(h);
        rt.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.WORKER, new FactionSlot(0), new Vec2d(8.5, 8.5), 1, 0));
        rt.submit(
                PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.INFANTRY, new FactionSlot(0), new Vec2d(12.5, 8.5), 1, 0));
        h.startSequence()
                .thenWaitUntil(() -> check(handles(rt).length == 2, "not both spawned yet"))
                .thenExecute(() -> {
                    int worker = -1;
                    int spearman = -1;
                    var templates = core(rt).queries().templates();
                    for (int handle : handles(rt)) {
                        var id = templates
                                .byIndex(unit(rt, handle).templateIndex())
                                .id();
                        if (id.equals(TemplateSet.WORKER)) {
                            worker = handle;
                        } else if (id.equals(TemplateSet.INFANTRY)) {
                            spearman = handle;
                        }
                    }
                    check(worker != -1 && spearman != -1, "a worker and a spearman: " + worker + ", " + spearman);
                    stat(rt, worker, CorePaths.HEALTH, 50);
                    stat(rt, worker, CorePaths.FLEE, 8);
                    stat(rt, worker, CorePaths.MELEE_HACK, 0);
                    stat(rt, spearman, CorePaths.HEALTH, 100);
                    stat(rt, spearman, CorePaths.FLEE, 0);
                    stat(rt, spearman, CorePaths.MELEE_HACK, 3);
                    var classes = core(rt).queries()
                            .templates()
                            .byIndex(unit(rt, worker).templateIndex())
                            .classes();
                    check(
                            classes.contains("Gatherer") && classes.contains("Builder"),
                            "the worker's classes " + classes);
                    var soldier = core(rt).queries()
                            .templates()
                            .byIndex(unit(rt, spearman).templateIndex())
                            .classes();
                    check(!soldier.contains("Gatherer") && !soldier.contains("Builder"), "the spearman's " + soldier);
                })
                .thenSucceed();
    }

    private static void stat(MatchRuntime rt, int handle, String path, double expected) {
        StatBreakdown b = core(rt).queries().stats(handle, path);
        check(b != null && b.result() == expected, "unit " + handle + " " + path + ": " + b + ", not " + expected);
    }

    /** Runs {@code body}, then loads the bundled set back even if it failed, so later batches keep every template. */
    private static void restoringBundled(StrategosServer server, Runnable body) {
        try {
            body.run();
        } finally {
            List<TemplateProblem> problems = new ArrayList<>();
            check(server.loadTemplates(bundled(server, problems), problems).accepted(), "restore bundled set");
        }
    }

    /** The loaded data packs' templates, the bundled ones in a GameTest server, as a reload reads them. */
    private static List<RawTemplate> bundled(StrategosServer server, List<TemplateProblem> problems) {
        List<RawTemplate> raws = new ArrayList<>(server.packTemplates(problems));
        check(problems.isEmpty(), "the bundled templates have problems: " + problems);
        check(!raws.isEmpty(), "no template in the loaded data packs");
        return raws;
    }

    private static List<RawTemplate> withWalkerSpeed(List<RawTemplate> raws, double speed) {
        List<RawTemplate> out = new ArrayList<>();
        for (RawTemplate r : raws) {
            if (r.id().equals(TemplateSet.DEBUG_WALKER)) {
                JsonObject json = r.json();
                json.getAsJsonObject("UnitMotion").addProperty("walkSpeed", speed);
                out.add(new RawTemplate(r.id(), r.file(), json));
            } else {
                out.add(r);
            }
        }
        return out;
    }

    private static RawTemplate raw(String name, String json) {
        return new RawTemplate(
                new TemplateId("strategos:unit/" + name),
                UNITS + name + ".json",
                JsonParser.parseString(json).getAsJsonObject());
    }

    private static void named(ServerTemplates.Outcome outcome, String file, String pointer) {
        check(
                outcome.problems().stream()
                        .anyMatch(p -> p.severity() == TemplateProblem.Severity.ERROR
                                && p.file().equals(file)
                                && p.pointer().equals(pointer)),
                "no error at " + file + " " + pointer + " in " + outcome.problems());
    }

    private static double speed(TemplateSet set) {
        var walker = set.byId(TemplateSet.DEBUG_WALKER);
        check(walker != null, "no debug walker in " + set);
        return walker.walkSpeed();
    }

    private static double x(MatchRuntime rt) {
        return unit(rt, handles(rt)[0]).x();
    }
}
