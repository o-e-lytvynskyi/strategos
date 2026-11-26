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
 * is kept for the first (the hold), and a soldier below {@code keep} still passes the kept stock.
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

    private StrategyScript script(int keep, String... order) {
        String techs = "{\"from\": " + FROM + ", \"keep\": " + keep + ", \"order\": [\"" + String.join("\",\"", order)
                + "\"]}";
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
        StrategyScript script = script(0, WOOD_1);

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
        StrategyScript script = script(0, WOOD_2, STONE_1, WOOD_1);

        assertThat(researched(research(script)))
                .as("not wood 2 (no Town), not wood 1 (later in the list)")
                .isEqualTo(new TechId(STONE_1));
    }

    @Test
    void aPhaseGoesOnlyOnceItsRequirementsStand() {
        s.grant(5_000, 5_000, 5_000, 5_000);
        s.ticks(20 * FROM);
        StrategyScript script = script(0, TOWN);

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
        StrategyScript script = script(0, IRON_1, WOOD_1);

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
    void aSoldierBelowKeepPassesTheStockKeptForATechnology() {
        s.building(DepthScene.STOREHOUSE, DepthScene.OWN, 40, 20);
        s.building(DepthScene.BARRACKS, DepthScene.OWN, 50, 40);
        s.grant(1_000, 1_000, 0, 0); // iron 1 (stone 50) is out of reach: the stock is kept for it
        s.ticks(20 * FROM);
        StrategyScript few = script(2, IRON_1);
        research(few); // the hold begins: the stock is kept for iron 1

        List<Command> sent = s.think((v, b, sink) -> new ProductionManager(few, memory).think(v, b, sink));
        assertThat(sent).as("a soldier is trained past the kept stock").anyMatch(Train.class::isInstance);

        s.spawn(DepthScene.INFANTRY, DepthScene.OWN, 60, 60, 2);
        List<Command> enough = s.think((v, b, sink) -> new ProductionManager(few, memory).think(v, b, sink));
        assertThat(enough).as("two soldiers stand: the stock stays kept").noneMatch(Train.class::isInstance);
    }
}
