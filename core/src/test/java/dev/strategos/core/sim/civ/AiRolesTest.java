package dev.strategos.core.sim.civ;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.PlaceBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.Train;
import dev.strategos.core.shared.CivId;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.MatchRules;
import dev.strategos.core.sim.PlayRules;
import dev.strategos.core.sim.ai.AiBudget;
import dev.strategos.core.sim.ai.AiView;
import dev.strategos.core.sim.ai.ComputerPlayer;
import dev.strategos.core.testkit.SimHarness;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * CIV-05: the AI names no template. It reads its faction's civilisation roles: a civilisation without a {@code house}
 * role gets no houses and the build order goes on; the roles {@code ranged}, {@code cavalry} and {@code siege} decide
 * which unit it trains for them, and a missing one means that unit is not trained.
 */
class AiRolesTest {
    private static final MatchRules PLAIN =
            MatchRules.DEFAULTS.withPlay(new PlayRules(PlayRules.Victory.NONE, false, true, false));
    private static final List<CivId> LEAN_VS_DEFAULT = List.of(CivScene.LEAN, CivId.DEFAULT);
    private static final TemplateId BARRACKS = new TemplateId("strategos:building/barracks");
    private static final TemplateId ARSENAL = new TemplateId("strategos:building/arsenal");
    private static final TemplateId ARCHER = new TemplateId("strategos:unit/archer");
    private static final TemplateId HORSEMAN = new TemplateId("strategos:unit/horseman");
    private static final TemplateId RAM = new TemplateId("strategos:unit/ram");
    private static final String ATTACK =
            "\"attack\":{\"first\":100000,\"every\":100000,\"min\":4,\"muster\":24," + "\"announce\":30},\"sites\":[]";

    private static JsonObject civ(String roles, String rules) {
        return JsonParser.parseString("{\"roles\":" + roles + ",\"rules\":" + rules + "}")
                .getAsJsonObject();
    }

    private static SimHarness match(JsonObject civ) {
        SimHarness h = CivScene.match(CivScene.with(civ), LEAN_VS_DEFAULT, RuleCatalogue.DEFAULT, PLAIN);
        h.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(0), "iron", 1_000));
        h.step();
        return h;
    }

    private static List<Command> think(SimHarness h, String script) {
        ComputerPlayer p = new ComputerPlayer(0, "t", script);
        AiBudget budget = new AiBudget(AiBudget.CAP);
        List<Command> sent = new ArrayList<>();
        p.think(new AiView(h.core().queries(), 0, budget), budget, sent::add);
        return sent;
    }

    private static List<String> placed(List<Command> sent) {
        return sent.stream()
                .filter(PlaceBuilding.class::isInstance)
                .map(c -> ((PlaceBuilding) c).template().value())
                .toList();
    }

    private static List<TemplateId> trained(List<Command> sent) {
        return sent.stream()
                .filter(Train.class::isInstance)
                .map(c -> ((Train) c).template())
                .toList();
    }

    /** {@code workers} stand under {@code rules}: the AI would want a house at once if its rules needed them. */
    private static SimHarness crowded(String roles, String rules, int workers) {
        SimHarness h = match(civ(roles, rules));
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(CivScene.WORKER, new FactionSlot(0), new Vec2d(14, 14), workers, 2));
        h.step();
        return h;
    }

    private static final String FIXED_8 = "{\"population\":{\"id\":\"fixed\",\"params\":{\"cap\":8}}}";

    private static final String HOUSING = "{\"maxWorkers\":0,\"ratio\":{},\"housingMargin\":2,\"soldierQueue\":0,"
            + "\"build\":[{\"building\":\"strategos:building/barracks\",\"population\":0}]," + ATTACK + "}";

    @Test
    void aCivilisationWithoutAHouseRoleBuildsNoHousesAndDoesNotStall() {
        List<String> placed = placed(think(crowded("{\"worker\":\"strategos:unit/worker\"}", FIXED_8, 7), HOUSING));
        assertThat(placed)
                .as("no house role: the housing branch is skipped and the build order goes on")
                .containsExactly("strategos:building/barracks");
    }

    @Test
    void aCivilisationWithAHouseRoleBuildsOne() {
        List<String> placed = placed(think(crowded("{\"house\":\"strategos:building/house\"}", "{}", 19), HOUSING));
        assertThat(placed).containsExactly("strategos:building/house");
    }

    private static final String COMPOSED = "{\"maxWorkers\":0,\"ratio\":{},\"build\":[],\"soldierQueue\":2,"
            + "\"battle\":{\"replan\":600}," + ATTACK + "}";

    private static List<TemplateId> trainedBy(String roles, String script, TemplateId building) {
        SimHarness h = match(civ(roles, "{}"));
        CivScene.building(h, 0, building, 20, 20);
        h.step();
        return trained(think(h, script));
    }

    @Test
    void theRangedRoleDecidesWhichUnitTheAiTrainsForIt() {
        // no infantry role: the AI's only choices are the civilisation's ranged and cavalry units
        assertThat(trainedBy(
                        "{\"house\":\"strategos:building/house\",\"ranged\":\"strategos:unit/archer\"}",
                        COMPOSED,
                        BARRACKS))
                .containsExactly(ARCHER);
        assertThat(trainedBy(
                        "{\"house\":\"strategos:building/house\",\"ranged\":\"strategos:unit/horseman\"}",
                        COMPOSED,
                        BARRACKS))
                .containsExactly(HORSEMAN);
    }

    @Test
    void theCavalryRoleDecidesWhichUnitTheAiTrainsForIt() {
        assertThat(trainedBy(
                        "{\"house\":\"strategos:building/house\",\"cavalry\":\"strategos:unit/archer\"}",
                        COMPOSED,
                        BARRACKS))
                .containsExactly(ARCHER);
    }

    @Test
    void aCivilisationWithNoSoldierRolesTrainsNone() {
        assertThat(trainedBy("{\"house\":\"strategos:building/house\"}", COMPOSED, BARRACKS))
                .isEmpty();
    }

    private static final String SIEGE = "{\"maxWorkers\":0,\"ratio\":{},\"build\":[],\"soldierQueue\":0,"
            + "\"battle\":{\"rams\":1}," + ATTACK + "}";

    @Test
    void theSiegeRoleIsTheRamTheArsenalTrains() {
        assertThat(trainedBy(
                        "{\"house\":\"strategos:building/house\",\"siege\":\"strategos:unit/ram\"}", SIEGE, ARSENAL))
                .containsExactly(RAM);
        assertThat(trainedBy("{\"house\":\"strategos:building/house\"}", SIEGE, ARSENAL))
                .as("no siege role: no ram is wanted")
                .isEmpty();
    }

    @Test
    void theDefaultCivilisationKeepsEveryRole() {
        TemplateSet set = CivScene.with(civ("{\"house\":\"strategos:building/house\"}", "{}"));
        assertThat(set.civ(CivId.DEFAULT).roles().get("ranged")).isEqualTo(ARCHER);
        assertThat(set.civ(CivId.DEFAULT).roles().get("cavalry")).isEqualTo(HORSEMAN);
        assertThat(set.civ(CivId.DEFAULT).roles().get("siege")).isEqualTo(RAM);
    }
}
