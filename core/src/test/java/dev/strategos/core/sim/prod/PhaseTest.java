package dev.strategos.core.sim.prod;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.strategos.core.content.TechDef;
import dev.strategos.core.content.TemplateCompiler;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.Research;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TechId;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchFactionView;
import dev.strategos.core.sim.OutputEvent;
import dev.strategos.core.sim.TechResearched;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.DamageStages;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * TECH-02 in L1: Town's requirements, what it unlocks, its cost, its bonuses, and that Village cannot be researched.
 */
class PhaseTest {
    static final TechId TOWN = new TechId("strategos:phase_town");
    static final TechId VILLAGE = new TechId("strategos:phase_village");
    static final TechId TOWN_AXES = new TechId("strategos:town_axes");
    static final TemplateId HOUSE = new TemplateId("strategos:building/house");
    static final TemplateId STOREHOUSE = new TemplateId("strategos:building/storehouse");
    static final TemplateId FARMSTEAD = new TemplateId("strategos:building/farmstead");
    static final TemplateId BARRACKS = new TemplateId("strategos:building/barracks");
    static final TemplateId FIELD = new TemplateId("strategos:building/field");

    /** The unlocks the test-only Town carries, as its TOKENS effects add them. */
    static final List<String> UNLOCKS = List.of("strategos:gather_wood_2", "strategos:unit/infantry");

    /**
     * The built-in set with a Town that unlocks a unit and a tech (the mechanism, T18 to T20 fill it for real), the
     * civic centre researching Village too, and a tech that requires Town. All the built-in techs stay.
     */
    static TemplateSet testSet() {
        TemplateSet b = TemplateSet.builtin();
        List<TemplateCompiler.Flat> flats = new ArrayList<>();
        for (UnitTemplate t : b.all()) {
            JsonObject json = b.flattened(t.id()).deepCopy();
            if (t.id().value().equals("strategos:building/civic_centre")) {
                json.getAsJsonObject("Researcher").addProperty("techs", TOWN.value() + " " + VILLAGE.value());
            }
            if (t.id().equals(STOREHOUSE)) {
                json.getAsJsonObject("Researcher").addProperty("techs", "strategos:gather_wood_1 " + TOWN_AXES.value());
            }
            flats.add(new TemplateCompiler.Flat(t.id(), t.netIndex(), json));
        }
        List<TemplateCompiler.TechFlat> techs = new ArrayList<>();
        for (TechDef t : b.techs()) {
            JsonObject json = t.toJson();
            if (t.id().equals(TOWN)) {
                JsonArray effects = json.getAsJsonArray("effects");
                effects.add(JsonParser.parseString("""
                        {"path":"Trainer/units","op":"tokens","tokens":"+strategos:unit/infantry",
                         "filter":"CivicCentre","stage":0,"priority":0}
                        """));
                effects.add(JsonParser.parseString("""
                        {"path":"Researcher/techs","op":"tokens","tokens":"+strategos:gather_wood_2",
                         "filter":"CivicCentre","stage":0,"priority":0}
                        """));
                JsonArray unlocks = json.has("unlocks") ? json.getAsJsonArray("unlocks") : new JsonArray();
                UNLOCKS.forEach(unlocks::add); // after whatever the shipped Town unlocks
                json.add("unlocks", unlocks);
            }
            techs.add(new TemplateCompiler.TechFlat(t.id(), t.netIndex(), json));
        }
        int next = b.techs().stream().mapToInt(TechDef::netIndex).max().orElse(-1) + 1;
        techs.add(new TemplateCompiler.TechFlat(
                new TechId("strategos:gather_wood_2"),
                next,
                JsonParser.parseString("{\"icon\":\"minecraft:iron_axe\",\"time\":5}")
                        .getAsJsonObject()));
        techs.add(new TemplateCompiler.TechFlat(
                TOWN_AXES,
                next + 1,
                JsonParser.parseString("{\"icon\":\"minecraft:iron_axe\",\"time\":5,\"requires\":{\"phase\":\"town\"}}")
                        .getAsJsonObject()));
        TemplateCompiler.Result result = TemplateCompiler.fromFlattened(flats, techs);
        assertThat(result.problems()).isEmpty();
        return result.set();
    }

    private static ProdScene rich(TemplateSet set) {
        ProdScene p = ProdScene.of(2_000, 2_000, set);
        grant(p, "stone", 1_000);
        grant(p, "iron", 1_000);
        return p;
    }

    private static void grant(ProdScene p, String resource, long amount) {
        p.h.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(0), resource, amount));
        p.h.step();
    }

    private static int spawn(ProdScene p, TemplateId template, int k) {
        return spawn(p, template, k, 0);
    }

    private static int spawn(ProdScene p, TemplateId template, int k, int faction) {
        p.h.submit(
                PlayerSlot.ADMIN,
                new SpawnBuilding(template, new FactionSlot(faction), 14 + 10 * (k % 4), 14 + 10 * (k / 4), 0));
        p.h.step();
        int[] all = p.s.buildings().handles();
        return all[all.length - 1];
    }

    private static CommandResult research(ProdScene p, int building, TechId tech) {
        p.h.submit(ProdScene.P0, new Research(building, tech));
        p.h.step();
        return p.h.results().get(p.h.results().size() - 1);
    }

    private static boolean untilResearched(ProdScene p, int limit) {
        for (int t = 0; t < limit; t++) {
            for (OutputEvent e : p.h.step().events()) {
                if (e instanceof TechResearched) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    void requirementsTable() {
        record Row(
                String name,
                int houses,
                int foundations,
                int others,
                int rivals, // houses of faction 1
                int sites, // houses of faction 0 still under construction
                List<Long> stock, // food, wood, stone, iron in whole units
                CommandOutcome outcome,
                RejectReason reason) {
            Row(
                    String name,
                    int houses,
                    int foundations,
                    int others,
                    List<Long> stock,
                    CommandOutcome outcome,
                    RejectReason reason) {
                this(name, houses, foundations, others, 0, 0, stock, outcome, reason);
            }
        }
        List<Long> enough = List.of(500L, 500L, 300L, 200L);
        List<Row> rows = new ArrayList<>();
        for (int n = 0; n <= 5; n++) {
            rows.add(new Row(
                    n + " houses",
                    n,
                    0,
                    0,
                    enough,
                    n == 5 ? CommandOutcome.ACCEPTED : CommandOutcome.REJECTED,
                    n == 5 ? null : RejectReason.REQUIREMENTS));
        }
        rows.add(new Row("six houses", 6, 0, 0, enough, CommandOutcome.ACCEPTED, null));
        rows.add(new Row("four and a foundation", 4, 1, 0, enough, CommandOutcome.REJECTED, RejectReason.REQUIREMENTS));
        rows.add(new Row("four and a field", 4, 0, 1, enough, CommandOutcome.REJECTED, RejectReason.REQUIREMENTS));
        rows.add(new Row(
                "another faction's five", 0, 0, 0, 5, 0, enough, CommandOutcome.REJECTED, RejectReason.REQUIREMENTS));
        rows.add(new Row(
                "four and a site under construction",
                4,
                0,
                0,
                0,
                1,
                enough,
                CommandOutcome.REJECTED,
                RejectReason.REQUIREMENTS));
        rows.add(new Row(
                "no food",
                5,
                0,
                0,
                List.of(499L, 500L, 300L, 200L),
                CommandOutcome.REJECTED,
                RejectReason.NOT_ENOUGH_RESOURCES));
        rows.add(new Row(
                "no wood",
                5,
                0,
                0,
                List.of(500L, 499L, 300L, 200L),
                CommandOutcome.REJECTED,
                RejectReason.NOT_ENOUGH_RESOURCES));
        rows.add(new Row(
                "no stone",
                5,
                0,
                0,
                List.of(500L, 500L, 299L, 200L),
                CommandOutcome.REJECTED,
                RejectReason.NOT_ENOUGH_RESOURCES));
        rows.add(new Row(
                "no iron",
                5,
                0,
                0,
                List.of(500L, 500L, 300L, 199L),
                CommandOutcome.REJECTED,
                RejectReason.NOT_ENOUGH_RESOURCES));
        for (Row row : rows) {
            ProdScene p = ProdScene.of(row.stock.get(0), row.stock.get(1));
            grant(p, "stone", row.stock.get(2));
            grant(p, "iron", row.stock.get(3));
            int k = 0;
            for (int i = 0; i < row.houses; i++) {
                spawn(p, HOUSE, k++);
            }
            for (int i = 0; i < row.foundations; i++) {
                int site = spawn(p, HOUSE, k++);
                p.s.buildings().setState(site, BuildingStore.FOUNDATION, 0);
            }
            for (int i = 0; i < row.others; i++) {
                spawn(p, FIELD, k++);
            }
            for (int i = 0; i < row.sites; i++) {
                int site = spawn(p, HOUSE, k++);
                p.s.buildings().setState(site, BuildingStore.CONSTRUCTING, 0);
            }
            for (int i = 0; i < row.rivals; i++) {
                spawn(p, HOUSE, k++, 1);
            }
            CommandResult result = research(p, p.centre, TOWN);
            assertThat(result.outcome()).as(row.name).isEqualTo(row.outcome);
            assertThat(result.firstReason()).as(row.name).isEqualTo(row.reason);
            if (row.reason == RejectReason.NOT_ENOUGH_RESOURCES) {
                assertThat(result.missing()).as(row.name).contains(1_000L);
            }
        }
    }

    @Test
    void allFourVillageTemplatesCount() {
        ProdScene p = rich(TemplateSet.builtin());
        spawn(p, HOUSE, 0);
        spawn(p, STOREHOUSE, 1);
        spawn(p, FARMSTEAD, 2);
        spawn(p, BARRACKS, 3);
        assertThat(research(p, p.centre, TOWN).firstReason()).as("four").isEqualTo(RejectReason.REQUIREMENTS);
        spawn(p, HOUSE, 4);
        assertThat(research(p, p.centre, TOWN).outcome()).as("five").isEqualTo(CommandOutcome.ACCEPTED);
    }

    @Test
    void townCostsStoneOrIron() {
        TechDef town = TemplateSet.builtin().tech(TOWN);
        assertThat(town).isNotNull();
        assertThat(town.cost().getOrDefault("stone", 0.0) + town.cost().getOrDefault("iron", 0.0))
                .as("Town's cost holds stone or iron")
                .isPositive();
        assertThat(town.cost()).containsEntry("food", 500.0).containsEntry("wood", 500.0);
        assertThat(town.ticks()).isEqualTo(1_200);
        assertThat(town.ordinal()).isEqualTo(1);
    }

    @Test
    void theVillagePhaseCannotBeResearched() {
        ProdScene p = rich(testSet()); // here the civic centre even lists it
        TechDef village = p.s.templates().tech(VILLAGE);
        assertThat(village).isNotNull();
        assertThat(village.ordinal()).isZero();
        assertThat(village.cost()).isEmpty();
        assertThat(research(p, p.centre, VILLAGE).firstReason()).isEqualTo(RejectReason.UNSUPPORTED);
        assertThat(Techs.phase(p.s, 0)).as("every faction starts in Village").isZero();
        assertThat(new MatchFactionView(p.s, 0).reached("village")).isTrue();
        assertThat(p.s.progress().empty())
                .as("and Progress stays empty (row 19)")
                .isTrue();
    }

    @Test
    void townUnlocksExactlyItsList() {
        TemplateSet shipped = TemplateSet.builtin();
        assertThat(unlockedByTown(shipped))
                .as("the shipped Town: the token diff of every template is its unlocks")
                .containsExactlyInAnyOrderElementsOf(shipped.tech(TOWN).unlocks());
        TemplateSet set = testSet();
        assertThat(unlockedByTown(set))
                .as("the test-only Town, whatever the shipped one adds")
                .containsExactlyInAnyOrderElementsOf(set.tech(TOWN).unlocks())
                .containsAll(UNLOCKS);
    }

    /** The tokens Town adds to any list of any template for faction 0, nothing removed. */
    private static TreeSet<String> unlockedByTown(TemplateSet set) {
        ProdScene p = rich(set);
        for (int k = 0; k < 5; k++) {
            spawn(p, HOUSE, k);
        }
        List<List<List<String>>> before = tokenLists(p, set);
        assertThat(research(p, p.centre, TOWN).outcome()).isEqualTo(CommandOutcome.ACCEPTED);
        assertThat(untilResearched(p, 1_300)).isTrue();
        List<List<List<String>>> after = tokenLists(p, set);
        TreeSet<String> added = new TreeSet<>();
        for (int t = 0; t < before.size(); t++) {
            for (int path = 0; path < before.get(t).size(); path++) {
                List<String> was = before.get(t).get(path);
                List<String> now = after.get(t).get(path);
                assertThat(now)
                        .as("template %d path %d keeps what it had", t, path)
                        .containsAll(was);
                now.stream().filter(token -> !was.contains(token)).forEach(added::add);
            }
        }
        assertThat(Techs.phase(p.s, 0)).isEqualTo(1);
        return added;
    }

    /** Every template's every token list for faction 0, in template and path order. */
    private static List<List<List<String>>> tokenLists(ProdScene p, TemplateSet set) {
        List<List<List<String>>> out = new ArrayList<>();
        for (UnitTemplate t : set.all()) {
            List<List<String>> lists = new ArrayList<>();
            for (int path = 0; path < set.tokenPaths().size(); path++) {
                List<String> tokens = p.s.stats().blockTokens(t.netIndex(), 0, path);
                lists.add(tokens == null ? List.of() : List.copyOf(tokens));
            }
            out.add(lists);
        }
        return out;
    }

    @Test
    void aPhaseRequirementIsMetOnceTownIsResearched() {
        TemplateSet set = testSet();
        ProdScene p = rich(set);
        for (int k = 0; k < 5; k++) {
            spawn(p, HOUSE, k);
        }
        int shed = spawn(p, STOREHOUSE, 5);
        assertThat(new MatchFactionView(p.s, 0).reached("town")).isFalse();
        assertThat(research(p, shed, TOWN_AXES).firstReason()).isEqualTo(RejectReason.REQUIREMENTS);
        assertThat(research(p, p.centre, TOWN).outcome()).isEqualTo(CommandOutcome.ACCEPTED);
        assertThat(untilResearched(p, 1_300)).isTrue();
        assertThat(new MatchFactionView(p.s, 0).reached("town")).isTrue();
        assertThat(research(p, shed, TOWN_AXES).outcome()).isEqualTo(CommandOutcome.ACCEPTED);
    }

    @Test
    void theBonusesApplyAtTown() {
        TemplateSet set = TemplateSet.builtin();
        ProdScene p = rich(set);
        int house = spawn(p, HOUSE, 0);
        for (int k = 1; k < 5; k++) {
            spawn(p, HOUSE, k);
        }
        int houseTemplate = set.byId(HOUSE).netIndex();
        int centreTemplate =
                set.byId(new TemplateId("strategos:building/civic_centre")).netIndex();
        int worker = set.byId(TemplateSet.WORKER).netIndex();
        int hp = p.s.stats().path("Health/max");
        int vision = p.s.stats().path("Vision/range");
        assertThat(p.s.stats().blockValue(houseTemplate, 0, hp)).isEqualTo(400);
        assertThat(p.s.stats().blockValue(houseTemplate, 0, vision)).isEqualTo(12);
        int fieldTemplate0 = set.byId(FIELD).netIndex();
        double fieldHp = p.s.stats().blockValue(fieldTemplate0, 0, hp);
        double fieldVision = p.s.stats().blockValue(fieldTemplate0, 0, vision);
        double workerHp = p.s.stats().blockValue(worker, 0, hp);
        double workerVision = p.s.stats().blockValue(worker, 0, vision);
        assertThat(research(p, p.centre, TOWN).outcome()).isEqualTo(CommandOutcome.ACCEPTED);
        assertThat(untilResearched(p, 1_300)).isTrue();
        assertThat(p.s.stats().blockValue(houseTemplate, 0, hp))
                .isCloseTo(440, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(p.s.stats().blockValue(centreTemplate, 0, hp))
                .isCloseTo(2_200, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(p.s.stats().blockValue(houseTemplate, 0, vision)).isEqualTo(14);
        assertThat(p.s.stats().blockValue(worker, 0, hp))
                .as("units are not buildings")
                .isEqualTo(workerHp);
        assertThat(p.s.stats().blockValue(worker, 0, vision)).isEqualTo(workerVision);
        assertThat(p.s.stats().blockValue(houseTemplate, 1, hp))
                .as("the other faction's")
                .isEqualTo(400);
        assertThat(p.s.stats().blockValue(fieldTemplate0, 0, hp))
                .as("a field gets no bonus")
                .isEqualTo(fieldHp);
        assertThat(p.s.stats().blockValue(fieldTemplate0, 0, vision)).isEqualTo(fieldVision);
        assertThat(p.s.buildings().alive(house)).isTrue();
    }

    @Test
    void aDamagedBuildingsStageFollowsTheNewMostHitPoints() {
        TemplateSet set = TemplateSet.builtin();
        ProdScene p = rich(set);
        int house = spawn(p, HOUSE, 0);
        for (int k = 1; k < 5; k++) {
            spawn(p, HOUSE, k);
        }
        p.s.buildings().setDamage(house, 142); // 258 of 400 left: 64.5 %, below the second threshold of 65
        DamageStages.update(p.s, house);
        assertThat(p.s.buildings().stage(house)).isEqualTo(2);
        assertThat(research(p, p.centre, TOWN).outcome()).isEqualTo(CommandOutcome.ACCEPTED);
        assertThat(untilResearched(p, 1_300)).isTrue();
        int hp = p.s.stats().path("Health/max");
        double most = p.s.stats().blockValue(set.byId(HOUSE).netIndex(), 0, hp);
        double fraction = (most - 142) / most;
        int fresh = DamageStages.stageFor(
                fraction,
                2,
                DamageStages.threshold(p.s, house, 1),
                DamageStages.threshold(p.s, house, 2),
                DamageStages.threshold(p.s, house, 3));
        assertThat(fresh)
                .as("at 440 hit points 298 left is 67.7 %, over 65 + 2")
                .isEqualTo(1);
        assertThat(p.s.buildings().stage(house)).isEqualTo(fresh);
    }
}
