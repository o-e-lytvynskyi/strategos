package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.RawCiv;
import dev.strategos.core.content.RawTemplate;
import dev.strategos.core.content.Scenario;
import dev.strategos.core.content.StartSlot;
import dev.strategos.core.content.TemplateProblem;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.shared.CivId;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.ai.Levels;
import dev.strategos.core.sim.build.BuildingView;
import dev.strategos.core.sim.civ.AiHint;
import dev.strategos.core.sim.command.ScenarioSetup;
import dev.strategos.core.testkit.Seeds;
import dev.strategos.gametest.MatchFixture;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.content.Scenarios;
import dev.strategos.mod.server.content.ServerTemplates;
import dev.strategos.mod.server.content.TemplateFiles;
import dev.strategos.mod.server.match.MatchRuntime;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Two civilisations in a real match (CIV-01.4, CIV-05): faction 0 plays the default and faction 1 the probe, a test-only
 * civilisation. Its files are the GameTest resources' {@code probe/} folder, read here and loaded into the server's
 * template set beside the pack's own as a reload does (a data pack of the probe would be seen by every test); {@link
 * #unloadProbe} reloads the pack's set after the batch. Each faction starts from its own civilisation's start kit and
 * plays its own strategy script.
 */
public final class CivTests {
    private static final CivId PROBE = new CivId("strategos:probe");
    private static final TemplateId CENTRE = new TemplateId("strategos:building/civic_centre");
    private static final TemplateId HOUSE = new TemplateId("strategos:building/house");
    private static final TemplateId STOREHOUSE = new TemplateId("strategos:building/storehouse");
    private static final TemplateId PROBE_INFANTRY = new TemplateId("strategos:unit/probe_infantry");
    private static final TemplateId HORSEMAN = new TemplateId("strategos:unit/horseman");
    private static final TemplateId SHEEP = new TemplateId("strategos:unit/sheep");
    private static final TemplateId PIG = new TemplateId("strategos:unit/pig");
    private static final int TICKS = 4_800;

    private CivTests() {}

    private static String text(String file) {
        try (InputStream in = CivTests.class.getClassLoader().getResourceAsStream("probe/" + file)) {
            return new String(
                    Objects.requireNonNull(in, "no GameTest resource probe/" + file)
                            .readAllBytes(),
                    UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static RawTemplate template(String id, String file) {
        return new RawTemplate(
                new TemplateId(id),
                "probe/" + file,
                JsonParser.parseString(text(file)).getAsJsonObject());
    }

    /** The pack's templates, techs and civilisations and the probe's, loaded as a reload loads them. */
    private static void loadProbe() {
        StrategosServer server = StrategosServer.get();
        List<TemplateProblem> problems = new ArrayList<>();
        TemplateFiles.Read read = TemplateFiles.read(server.server().getResourceManager());
        problems.addAll(read.problems());
        List<RawTemplate> raws = new ArrayList<>(TemplateFiles.raws(read, JsonOps.INSTANCE, problems));
        raws.add(template("strategos:unit/probe_infantry", "templates/unit/probe_infantry.json"));
        raws.add(template("strategos:building/probe_barracks", "templates/building/probe_barracks.json"));
        List<RawCiv> civs = new ArrayList<>(TemplateFiles.rawCivs(read, JsonOps.INSTANCE, problems));
        civs.add(new RawCiv(
                PROBE,
                "probe/civs/probe.json",
                JsonParser.parseString(text("civs/probe.json")).getAsJsonObject()));
        check(problems.isEmpty(), "the pack has problems: " + problems);
        ServerTemplates.Outcome outcome = server.loadTemplates(
                raws,
                TemplateFiles.rawTechs(read, JsonOps.INSTANCE, problems),
                civs,
                problems,
                TemplateFiles.blueprints(read, problems));
        check(outcome.accepted(), "the probe was refused: " + outcome.message());
    }

    /** The pack's own set again, after the batch whose match has been disposed. */
    public static void unloadProbe() {
        StrategosServer server = StrategosServer.get();
        check(server.reloadTemplates().accepted(), "the pack's templates did not reload");
    }

    private static int count(MatchCore core, int faction, TemplateId template) {
        int n = 0;
        for (int handle : core.queries().unitHandlesOf(new FactionSlot(faction))) {
            UnitView u = core.queries().unit(handle);
            n += u != null && u.template().equals(template) ? 1 : 0;
        }
        return n;
    }

    private static int buildings(MatchCore core, int faction, TemplateId template) {
        int n = 0;
        for (BuildingView b : core.queries().buildings()) {
            n += b.faction() == faction && b.template().equals(template) ? 1 : 0;
        }
        return n;
    }

    /**
     * Faction 0 plays the default, faction 1 the probe, from a scenario of two kit slots. At the start each holds its
     * own kit (the default's horseman and pigs, the probe's six workers and wood); the rosters, the roles and the AI
     * hints are the civilisations'; and for {@value #TICKS} ticks the probe builds on without a house, a storehouse or a
     * builder, trains its infantry (the Gatherer class) and stays under its fixed cap, while the default builds its own.
     */
    @StrategosTest(arena = "arena_flat_48", batch = "civ_two", solo = true, timeoutTicks = TICKS + 200)
    public static void twoCivilisations(GameTestHelper h) {
        loadProbe();
        MatchRuntime rt = MatchFixture.create(
                h, MatchFixture.options(Seeds.first("canary"), h).withCivs(List.of(CivId.DEFAULT, PROBE)));
        List<StartSlot> starts = List.of(new StartSlot(12, 0, 12, ScenarioSetup.KIT_YAW), new StartSlot(34, 0, 34, 90));
        Scenario scenario = new Scenario(
                "none",
                "none",
                List.of(
                        new Scenario.Slot("ai:skirmish", Map.of(), List.of(), CivId.DEFAULT, true),
                        new Scenario.Slot("ai:probe", Map.of(), List.of(), CivId.DEFAULT, true)));
        String skirmish = Levels.resolve(
                Objects.requireNonNull(Scenarios.script(h.getLevel().getServer(), "skirmish"), "the skirmish script"),
                "normal");
        String probe = Levels.resolve(text("ai/probe.json"), "normal");
        TemplateSet templates = Objects.requireNonNull(core(rt).queries().templates());
        for (Command c : ScenarioSetup.commands(
                scenario,
                starts,
                templates,
                profile -> profile.equals("probe") ? probe : skirmish,
                List.of(CivId.DEFAULT, PROBE),
                r -> r)) {
            rt.submit(PlayerSlot.ADMIN, c);
        }
        for (int f = 0;
                f < 2;
                f++) { // the arena has no trees, rock or ore: what the barracks and soldiers cost is granted
            rt.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(f), "wood", 1_500));
            rt.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(f), "food", 1_000));
            rt.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(f), "stone", 500));
            rt.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(f), "iron", 200));
        }
        int[] builders = {0};
        h.startSequence()
                .thenWaitUntil(() -> check(core(rt).queries().tick() >= 3, "the setup has not run"))
                .thenExecute(() -> {
                    MatchCore core = core(rt);
                    var q = core.queries();
                    check(
                            q.config().civs().equals(List.of(CivId.DEFAULT, PROBE)),
                            "civs " + q.config().civs());
                    check(templates.civ(PROBE) != null, "the probe is not in the loaded set");
                    FactionSlot f0 = new FactionSlot(0);
                    FactionSlot f1 = new FactionSlot(1);
                    // rosters, roles and hints
                    check(HOUSE.equals(q.role(f0, "house")), "the default has a house role");
                    check(q.role(f1, "house") == null, "the probe has a house role: " + q.role(f1, "house"));
                    check(q.role(f1, "storehouse") == null, "the probe has a storehouse role");
                    check(
                            PROBE_INFANTRY.equals(q.role(f1, "infantry")),
                            "the probe's infantry: " + q.role(f1, "infantry"));
                    check(q.aiHints(f0) == AiHint.ALL, "the default's hints " + q.aiHints(f0));
                    check(q.aiHints(f1) == 0, "the probe's hints " + q.aiHints(f1));
                    // the rules: housing counts for the default, a fixed cap of 60 for the probe
                    check(q.populationCap(f1) == 60, "the probe's cap " + q.populationCap(f1));
                    check(q.populationCap(f0) < 60, "the default's cap " + q.populationCap(f0));
                    // the start kits
                    check(buildings(core, 0, CENTRE) == 1, "the default's centre");
                    check(buildings(core, 1, CENTRE) == 1, "the probe's centre");
                    check(
                            count(core, 0, TemplateSet.WORKER) == 5,
                            "the default's workers " + count(core, 0, TemplateSet.WORKER));
                    check(
                            count(core, 1, TemplateSet.WORKER) == 6,
                            "the probe's workers " + count(core, 1, TemplateSet.WORKER));
                    check(count(core, 0, HORSEMAN) == 1, "the default's horseman");
                    check(count(core, 1, HORSEMAN) == 0, "the probe has a horseman");
                    int wood = q.config().rules().economy().indexOf("wood");
                    check(q.treasury(f1).stock().get(wood) == 1_750_000, "the probe's own wood: 250 and the grant");
                    check(q.treasury(f0).stock().get(wood) == 1_500_000, "the default's wood: the grant only");
                    int gaia = q.config().factions();
                    check(count(core, gaia, PIG) == 2 && count(core, gaia, SHEEP) == 8, "the livestock of both kits");
                })
                .thenWaitUntil(() -> {
                    MatchCore core = core(rt);
                    for (int handle : core.queries().unitHandlesOf(new FactionSlot(1))) {
                        UnitView u = core.queries().unit(handle);
                        builders[0] += u != null && u.state() == UnitState.BUILDING ? 1 : 0;
                    }
                    check(
                            core.queries().tick() >= TICKS,
                            "tick " + core.queries().tick());
                })
                .thenExecute(() -> {
                    MatchCore core = core(rt);
                    check(builders[0] == 0, "a probe unit built: " + builders[0]);
                    check(
                            buildings(core, 1, HOUSE) == 0 && buildings(core, 1, STOREHOUSE) == 0,
                            "a probe house or storehouse");
                    check(
                            count(core, 1, PROBE_INFANTRY) > 0,
                            "the probe trained no infantry: "
                                    + core.queries().buildings().stream()
                                            .filter(b -> b.faction() == 1)
                                            .map(b -> b.template().value() + "/" + b.state())
                                            .toList()
                                    + " stock "
                                    + core.queries()
                                            .treasury(new FactionSlot(1))
                                            .stock()
                                    + " workers " + count(core, 1, TemplateSet.WORKER));
                    check(
                            core.queries().buildings().stream()
                                            .filter(b -> b.faction() == 1)
                                            .count()
                                    > 1,
                            "the probe built nothing");
                    check(core.queries().populationUsed(new FactionSlot(1)) <= 60, "the probe passed its cap");
                    check(count(core, 0, TemplateSet.WORKER) > 5, "the default's economy did not grow");
                })
                .thenSucceed();
    }
}
