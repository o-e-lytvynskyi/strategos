package dev.strategos.core.sim.ai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.Research;
import dev.strategos.core.protocol.Train;
import dev.strategos.core.shared.TechId;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * AI-09 (M10 §2.19): the AI researches along the tree. Nothing before {@code techs.from}; then the first technology of
 * {@code order} the rules would take and the stock pays, in order; a phase only once its requirements stand; the stock
 * is kept for the first (the hold), and a phase is saved for only once a building of the faction would research it but for its requirements.
 */
class TechManagerTest {
    private static final String WOOD_1 = "strategos:gather_wood_1"; // food 100, wood 50
    private static final String STONE_1 = "strategos:gather_stone_1"; // food 100, wood 100
    private static final String IRON_1 = "strategos:gather_iron_1"; // food 100, wood 100, stone 50
    private static final String WOOD_2 = "strategos:gather_wood_2"; // needs Town and wood 1
    private static final String TOWN = "strategos:phase_town"; // needs five village buildings
    private static final int FROM = 10; // seconds
    private static final int HOLD = 100; // ticks, depth.techHold

    private final DepthScene s = new DepthScene();
    private final AiMemory memory = new AiMemory();

    private StrategyScript script(String... order) {
        String techs = "{\"from\": " + FROM + ", \"order\": [\"" + String.join("\",\"", order) + "\"]}";
        return DepthScene.script(DepthScene.depth(techs));
    }

    private List<Command> research(StrategyScript script) {
        return s.think((v, b, sink) -> new TechManager(script, memory).think(v, b, sink));
    }

    private static TechId researched(List<Command> sent) {
        assertThat(sent).as("one research").hasSize(1).allMatch(Research.class::isInstance);
        return ((Research) sent.get(0)).tech();
    }

    @Test
    void nothingIsResearchedBeforeTheScriptsMinute() {
        s.building(DepthScene.STOREHOUSE, DepthScene.OWN, 40, 20);
        s.grant(5_000, 5_000, 5_000, 5_000);
        StrategyScript script = script(WOOD_1);

        assertThat(s.h.tick()).isLessThan(20L * FROM);
        assertThat(research(script)).as("before from").isEmpty();

        s.ticks(20 * FROM);
        assertThat(researched(research(script))).isEqualTo(new TechId(WOOD_1));
    }

    @Test
    void theFirstTechnologyOfTheListThatTheRulesTakeGoes_InOrder() {
        s.building(DepthScene.STOREHOUSE, DepthScene.OWN, 40, 20);
        s.grant(5_000, 5_000, 5_000, 5_000);
        s.ticks(20 * FROM);
        // wood 2 stands first in the list but needs Town: the rules would not take it; stone 1 stands before wood 1
        StrategyScript script = script(WOOD_2, STONE_1, WOOD_1);

        assertThat(researched(research(script)))
                .as("not wood 2 (no Town), not wood 1 (later in the list)")
                .isEqualTo(new TechId(STONE_1));
    }

    @Test
    void aPhaseGoesOnlyOnceItsRequirementsStand() {
        s.grant(5_000, 5_000, 5_000, 5_000);
        s.ticks(20 * FROM);
        StrategyScript script = script(TOWN);

        assertThat(research(script)).as("one centre and no village around it").isEmpty();

        for (int i = 0; i < 5; i++) {
            s.building(DepthScene.HOUSE, DepthScene.OWN, 30 + 8 * i, 40);
        }
        assertThat(researched(research(script))).isEqualTo(new TechId(TOWN));
    }

    @Test
    void theStockIsKeptForTheFirstUntilTheHoldRunsOut() {
        s.building(DepthScene.STOREHOUSE, DepthScene.OWN, 40, 20);
        s.grant(1_000, 1_000, 0, 0); // iron 1 wants 50 stone: out of reach; wood 1 is paid
        s.ticks(20 * FROM);
        StrategyScript script = script(IRON_1, WOOD_1);

        assertThat(research(script))
                .as("the hold starts: wood 1 waits behind iron 1")
                .isEmpty();
        s.ticks(HOLD / 2);
        assertThat(research(script)).as("still inside the hold").isEmpty();
        s.ticks(HOLD);
        assertThat(researched(research(script)))
                .as("the hold is spent: the cheaper one goes")
                .isEqualTo(new TechId(WOOD_1));
    }

    @Test
    void aBuildingThatCannotBePlacedHoldsTheTreeOnlyForItsReserveTime() {
        s.building(DepthScene.STOREHOUSE, DepthScene.OWN, 40, 20);
        s.grant(1_000, 100, 0, 0); // wood 1 is paid; with the barracks' cost on top it is not
        s.ticks(20 * FROM);
        StrategyScript script = DepthScene.script(
                DepthScene.depth(techs(WOOD_1)), "{\"building\": \"strategos:building/barracks\", \"population\": 0}");
        memory.set(Slots.DUE_SINCE, 1); // the step has been due since the first tick and no one places it

        assertThat(research(script))
                .as("the step's reserve is running: the stock waits for it")
                .isEmpty();
        s.ticks((int) BuildManager.RESERVE_TICKS);
        assertThat(researched(research(script)))
                .as("the reserve is spent: the tree goes on")
                .isEqualTo(new TechId(WOOD_1));
    }

    private static String techs(String... order) {
        return "{\"from\": " + FROM + ", \"order\": [\"" + String.join("\",\"", order) + "\"]}";
    }

    @Test
    void aSoldierThatCostsTheResourceTheTechnologyLacksIsHeldWhateverTheArmysSize() {
        s.building(DepthScene.STOREHOUSE, DepthScene.OWN, 40, 20);
        s.building(DepthScene.BARRACKS, DepthScene.OWN, 50, 40);
        s.grant(1_000, 80, 0, 0); // iron 1 (wood 100, stone 50) lacks wood and stone; a soldier needs 40 wood
        s.ticks(20 * FROM);
        StrategyScript wave = StrategyScript.parse("""
            { "maxWorkers": 0, "ratio": {}, "build": [], "housingMargin": 0, "soldierQueue": 3, "reserve": true,
              "attack": { "first": 100000, "every": 600, "min": 24, "muster": 24, "announce": 30 },
              "battle": { "focus": "wounded", "behind": 4, "retreat": 0.6, "reach": 28, "defence": 600 },
              "sites": [], %s }""".formatted(DepthScene.depth(techs(IRON_1))));
        research(wave); // the hold begins: the stock is kept for iron 1

        s.spawn(DepthScene.INFANTRY, DepthScene.OWN, 60, 60, 14); // not under half of 24
        List<Command> sent = s.think((v, b, sink) -> new ProductionManager(wave, memory).think(v, b, sink));
        assertThat(sent).as("14 stand of 24, the wood is kept").noneMatch(Train.class::isInstance);
    }

    @Test
    void aPhaseBlockedOnlyByItsBuildingsIsSavedForAlready() {
        String city = "strategos:phase_city";
        for (int i = 0; i < 5; i++) {
            s.building(DepthScene.HOUSE, DepthScene.OWN, 30 + 8 * i, 40);
        }
        s.grant(5_000, 5_000, 5_000, 5_000);
        s.ticks(20 * FROM);
        List<Command> town = research(script(TOWN));
        s.h.submit(new dev.strategos.core.shared.PlayerSlot(0), town.get(0));
        s.ticks(3_000);
        s.building(DepthScene.TEMPLE, DepthScene.OWN, 60, 60);
        s.building(new dev.strategos.core.shared.TemplateId("strategos:building/tower"), DepthScene.OWN, 80, 60);
        // Town stands, two of the three Town buildings stand: City waits for the third; its price is the reserve
        StrategyScript script = script(city);
        assertThat(research(script)).as("nothing to research yet").isEmpty();
        BuildManager.Due[] due = new BuildManager.Due[1];
        s.think((v, b, sink) -> due[0] = TechManager.due(v, script, memory));
        assertThat(due[0]).as("City is the active reserve").isNotNull();
        assertThat(due[0].tech()).isNotNull();
        assertThat(due[0].tech().id()).isEqualTo(new TechId(city));
    }

    @Test
    void aPhaseNoBuildingHoldsYetIsNotSavedFor() {
        s.grant(5_000, 5_000, 5_000, 5_000);
        s.ticks(20 * FROM);
        StrategyScript script = script("strategos:phase_city"); // Town is not researched: the centre does not hold City
        assertThat(research(script)).isEmpty();
        BuildManager.Due[] due = new BuildManager.Due[1];
        s.think((v, b, sink) -> due[0] = TechManager.due(v, script, memory));
        assertThat(due[0]).as("no reserve: nothing a building could research").isNull();
    }
}
