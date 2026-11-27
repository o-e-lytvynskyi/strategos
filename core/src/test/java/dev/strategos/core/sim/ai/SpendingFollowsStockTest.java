package dev.strategos.core.sim.ai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.PlaceBuilding;
import dev.strategos.core.protocol.Train;
import dev.strategos.core.shared.TemplateId;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * AI-09 (T25b): spending follows the stock in the depth path's late game, and only there. The army has a floor, the
 * wave's {@code attack.min}, that no reserve breaks; gatherers go where the reserve is short. A script without a {@code
 * depth} block plays as before.
 */
class SpendingFollowsStockTest {
    private static final TemplateId ARSENAL = new TemplateId("strategos:building/arsenal");

    private static StrategyScript script(String depthBlock) {
        return StrategyScript.parse("""
            { "maxWorkers": 0, "ratio": {}, "build": [], "housingMargin": 0, "soldierQueue": 3, "reserve": true,
              "attack": { "first": 100000, "every": 600, "min": 24, "muster": 24, "announce": 30 },
              "battle": { "focus": "wounded", "behind": 4, "retreat": 0.6, "reach": 28, "defence": 600, "rams": 3 },
              "sites": []%s }""".formatted(depthBlock.isEmpty() ? "" : "," + depthBlock));
    }

    /** The commands of a production think in a scene with 24 soldiers standing, the ram's price kept, stock for one. */
    private static List<Command> wave(StrategyScript script) {
        DepthScene s = new DepthScene();
        s.building(DepthScene.STOREHOUSE, DepthScene.OWN, 40, 20);
        s.building(DepthScene.BARRACKS, DepthScene.OWN, 50, 40);
        s.building(ARSENAL, DepthScene.OWN, 70, 40);
        s.grant(100, 100, 0, 0); // a soldier is paid; a soldier and a ram (wood 150, iron 70) together are not
        s.spawn(DepthScene.INFANTRY, DepthScene.OWN, 60, 60, 3);
        return s.think((v, b, sink) -> new ProductionManager(script, new AiMemory()).think(v, b, sink));
    }

    @Test
    void aShortResourceOfTheReserveDrawsGatherersAndAPileDoesNot() {
        double base = EconomyManager.shape(10, 200_000, true, false);
        assertThat(base).isEqualTo(10);
        assertThat(EconomyManager.shape(10, 200_000, true, true))
                .as("lacking for the reserve: its weight grows even in the middle band")
                .isGreaterThan(base);
        assertThat(EconomyManager.shape(10, 50_000, true, true))
                .as("lacking and low: more than low alone")
                .isGreaterThan(EconomyManager.shape(10, 50_000, true, false));
        assertThat(EconomyManager.shape(10, 200_000, false, true))
                .as("knob off")
                .isEqualTo(10);
    }

    @Test
    void theLateGameBeginsWithTheTreesMinuteAndOnlyWithATree() {
        Depth tree = DepthScene.script(DepthScene.depth("{\"from\": 10, \"order\": [\"strategos:phase_city\"]}"))
                .depth();
        assertThat(tree.late(199)).isFalse();
        assertThat(tree.late(200)).isTrue();
        assertThat(Depth.NONE.late(1_000_000)).as("no depth block: never").isFalse();
        assertThat(DepthScene.script(DepthScene.depth("{\"from\": 0}")).depth().late(1_000_000))
                .as("no tree: never")
                .isFalse();
    }

    @Test
    void theRamsPriceDoesNotHoldASoldierBackWhileTheArmyIsBelowTheWaveInTheLateGame() {
        StrategyScript depth = script(DepthScene.depth("{\"from\": 0, \"order\": [\"strategos:gather_wood_1\"]}"));
        assertThat(wave(depth)).as("three soldiers of a 24 wave").anyMatch(Train.class::isInstance);
    }

    @Test
    void aScriptWithoutDepthKeepsTheRamsPriceAsBefore() {
        assertThat(wave(script("")))
                .as("no depth block: the ram's price holds the soldier back")
                .noneMatch(Train.class::isInstance);
    }

    // the reserve is the City phase (food 600, wood 700, stone 350, iron 250); the stock lacks only its iron

    private static final String DEPTH = DepthScene.depth("{\"from\": 0, \"order\": [\"strategos:phase_city\"]}");

    /** The commands of a production think over {@code buildings} with the City reserve kept and iron short. */
    private static List<Command> kept(StrategyScript script, TemplateId... buildings) {
        DepthScene s = new DepthScene();
        s.building(DepthScene.STOREHOUSE, DepthScene.OWN, 40, 20);
        for (int k = 0; k < buildings.length; k++) {
            s.building(buildings[k], DepthScene.OWN, 50 + 20 * k, 40);
        }
        s.grant(1_000, 1_000, 1_000, 50);
        s.spawn(DepthScene.INFANTRY, DepthScene.OWN, 60, 60, 30); // above the floor and the early guard
        AiMemory memory = new AiMemory();
        var city = s.h.core().queries().templates().tech(new dev.strategos.core.shared.TechId("strategos:phase_city"));
        memory.set(Slots.TECH_DUE, (s.h.tick() + 1) << 16 | (city.netIndex() + 1L));
        return s.think((v, b, sink) -> new ProductionManager(script, memory).think(v, b, sink));
    }

    @Test
    void aSoldierThatCostsTheLackingResourceIsHeldAndOneThatDoesNotIsTrained() {
        List<Command> sent = kept(script(DEPTH), DepthScene.BARRACKS);
        assertThat(sent).as("the infantry needs no iron").anyMatch(Train.class::isInstance);
        assertThat(sent)
                .as("nothing that costs iron: the iron is kept for the phase")
                .allMatch(c -> c instanceof Train t && t.template().equals(DepthScene.INFANTRY));
    }

    @Test
    void aHealerIsHeldTheSameWay() {
        assertThat(kept(script(DEPTH), DepthScene.TEMPLE))
                .as("a healer costs iron")
                .noneMatch(Train.class::isInstance);
    }

    @Test
    void aRamIsHeldTheSameWay() {
        assertThat(kept(script(DEPTH), ARSENAL)).as("a ram costs iron").noneMatch(Train.class::isInstance);
    }

    @Test
    void withoutDepthTheOldRulesHold() {
        assertThat(kept(script(""), DepthScene.BARRACKS))
                .as("the whole price of the soldier and the reserve is not in the stock: nothing is trained")
                .noneMatch(Train.class::isInstance);
    }

    @Test
    void theRuleSpendsWhatTheReserveDoesNotLack() {
        long[] held = {600, 700, 350, 250};
        assertThat(ProductionManager.spendable(new long[] {1_000, 1_000, 1_000, 50}, new long[] {0, 40, 0, 0}, held))
                .as("no iron in the price")
                .isTrue();
        assertThat(ProductionManager.spendable(new long[] {1_000, 1_000, 1_000, 50}, new long[] {0, 40, 0, 20}, held))
                .as("iron the reserve lacks")
                .isFalse();
        assertThat(ProductionManager.spendable(new long[] {1_000, 1_000, 1_000, 300}, new long[] {0, 40, 0, 20}, held))
                .as("iron the reserve has: free")
                .isTrue();
    }

    @Test
    void theRamsPriceIsNotHeldWhileAPhaseIsSaved() {
        List<Command> sent = kept(script(DEPTH), DepthScene.BARRACKS, ARSENAL);
        assertThat(sent)
                .as("iron 50 is short of the ram's 70, but the phase's iron is what is kept: the infantry goes")
                .anyMatch(c -> c instanceof Train t && t.template().equals(DepthScene.INFANTRY));
        assertThat(sent)
                .as("the ram costs iron")
                .noneMatch(c -> c instanceof Train t && !t.template().equals(DepthScene.INFANTRY));
    }

    private static final TemplateId TOWER = new TemplateId("strategos:building/tower");

    /** The commands of a build think, City kept and iron short, with {@code step} the build order's next building. */
    private static List<Command> building(String depthBlock, TemplateId step) {
        StrategyScript script =
                DepthScene.script(depthBlock, "{\"building\": \"" + step.value() + "\", \"population\": 0}");
        DepthScene s = new DepthScene();
        s.spawn(DepthScene.WORKER, DepthScene.OWN, 30, 30, 5);
        s.grant(1_000, 1_000, 1_000, 60); // a barracks (iron 50) is paid; City's iron (250) is not in the stock
        AiMemory memory = new AiMemory();
        var city = s.h.core().queries().templates().tech(new dev.strategos.core.shared.TechId("strategos:phase_city"));
        memory.set(Slots.TECH_DUE, (s.h.tick() + 1) << 16 | (city.netIndex() + 1L));
        return s.think((v, b, sink) -> new BuildManager(script, memory).think(v, b, sink));
    }

    @Test
    void aBuildingThatCostsTheResourceAPhaseLacksIsNotPlacedButTheTownBuildingIs() {
        assertThat(building(DEPTH, DepthScene.BARRACKS))
                .as("a barracks costs iron: kept for City")
                .noneMatch(c -> c instanceof dev.strategos.core.protocol.PlaceBuilding);
        assertThat(building(DEPTH, TOWER))
                .as("a Town building is what City asks for: it goes up")
                .anyMatch(c -> c instanceof dev.strategos.core.protocol.PlaceBuilding);
        assertThat(building("", DepthScene.BARRACKS))
                .as("no depth block: the barracks is placed as before")
                .anyMatch(c -> c instanceof dev.strategos.core.protocol.PlaceBuilding);
    }

    @Test
    void aDueBuildStepDoesNotHideThePhaseFromTheSpendingRule() {
        StrategyScript script =
                DepthScene.script(DEPTH, "{\"building\": \"strategos:building/tower\", \"population\": 0}");
        DepthScene s = new DepthScene();
        s.building(DepthScene.STOREHOUSE, DepthScene.OWN, 40, 20);
        s.building(DepthScene.TEMPLE, DepthScene.OWN, 50, 40);
        s.grant(1_000, 1_000, 1_000, 50);
        AiMemory memory = new AiMemory();
        var city = s.h.core().queries().templates().tech(new dev.strategos.core.shared.TechId("strategos:phase_city"));
        memory.set(Slots.TECH_DUE, (s.h.tick() + 1) << 16 | (city.netIndex() + 1L));
        memory.set(Slots.STEP, 0);
        memory.set(Slots.DUE_SINCE, s.h.tick() + 1); // the tower is due and unplaced: it is the active step
        List<Command> sent = s.think((v, b, sink) -> new ProductionManager(script, memory).think(v, b, sink));
        assertThat(sent).as("a healer costs the iron City lacks").noneMatch(Train.class::isInstance);
    }

    @Test
    void aSpendMayNotTakeACoveredResourceOfThePhasesPriceBelowItAboveTheFloor() {
        long[] held = {600, 700, 350, 250};
        assertThat(ProductionManager.keeps(new long[] {1_000, 750, 400, 100}, new long[] {0, 40, 0, 0}, held))
                .as("wood 750 less 40 is 710, still above 700")
                .isTrue();
        assertThat(ProductionManager.keeps(new long[] {1_000, 720, 400, 100}, new long[] {0, 40, 0, 0}, held))
                .as("wood 720 less 40 is 680: under the price")
                .isFalse();
        assertThat(ProductionManager.keeps(new long[] {1_000, 720, 400, 100}, new long[] {50, 0, 0, 0}, held))
                .as("a resource outside the spend is not looked at")
                .isTrue();
    }

    @Test
    void belowTheFloorInfantryPaidFromACoveredResourceIsTrainedAndAnArcherCostingTheLackingIronIsNot() {
        // the first scene: 3 soldiers of 24, wood 720 (covered, price 700) less the infantry's 40 goes under 700
        DepthScene s = new DepthScene();
        s.building(DepthScene.STOREHOUSE, DepthScene.OWN, 40, 20);
        s.building(DepthScene.BARRACKS, DepthScene.OWN, 50, 40);
        s.grant(1_000, 720, 1_000, 50);
        s.spawn(DepthScene.INFANTRY, DepthScene.OWN, 60, 60, 3);
        AiMemory memory = new AiMemory();
        var city = s.h.core().queries().templates().tech(new dev.strategos.core.shared.TechId("strategos:phase_city"));
        memory.set(Slots.TECH_DUE, (s.h.tick() + 1) << 16 | (city.netIndex() + 1L));
        StrategyScript script = script(DEPTH);
        List<Command> below = s.think((v, b, sink) -> new ProductionManager(script, memory).think(v, b, sink));
        assertThat(below)
                .as("below the 24 floor: the covered wood is spent, only the lacking iron is kept")
                .anyMatch(c -> c instanceof Train t && t.template().equals(DepthScene.INFANTRY));
        assertThat(below).noneMatch(c -> c instanceof Train t && !t.template().equals(DepthScene.INFANTRY));

        s.spawn(DepthScene.INFANTRY, DepthScene.OWN, 70, 60, 21);
        List<Command> above = s.think((v, b, sink) -> new ProductionManager(script, memory).think(v, b, sink));
        assertThat(above).as("at the floor: wood stays at the price").noneMatch(Train.class::isInstance);
    }

    @Test
    void aStepThePhaseBlocksIsSkippedForTheTownBuildingsAndComesBackOnceTheyStand() {
        String steps = "{\"building\": \"strategos:building/barracks\", \"population\": 0},"
                + "{\"building\": \"strategos:building/tower\", \"population\": 0}";
        StrategyScript script = DepthScene.script(DEPTH, steps);
        DepthScene s = new DepthScene();
        s.spawn(DepthScene.WORKER, DepthScene.OWN, 30, 30, 5);
        s.grant(1_000, 1_000, 1_000, 60); // a barracks (iron 50) is paid; City's iron (250) is not in the stock
        AiMemory memory = new AiMemory();
        var city = s.h.core().queries().templates().tech(new dev.strategos.core.shared.TechId("strategos:phase_city"));
        long saved = (s.h.tick() + 1) << 16 | (city.netIndex() + 1L);
        memory.set(Slots.TECH_DUE, saved);

        List<Command> first = s.think((v, b, sink) -> new BuildManager(script, memory).think(v, b, sink));
        assertThat(first).as("the barracks is held for City").noneMatch(c -> c instanceof PlaceBuilding);
        assertThat(memory.get(Slots.STEP)).as("the order moves on to the tower").isEqualTo(1);
        List<Command> second = s.think((v, b, sink) -> new BuildManager(script, memory).think(v, b, sink));
        assertThat(second)
                .as("the Town building City asks for goes up")
                .anyMatch(c -> c instanceof PlaceBuilding p && p.template().equals(TOWER));

        s.building(TOWER, DepthScene.OWN, 90, 90);
        memory.set(Slots.STEP, 2); // the order is through
        memory.set(Slots.PLACED, 0);
        List<Command> waiting = s.think((v, b, sink) -> new BuildManager(script, memory).think(v, b, sink));
        assertThat(waiting)
                .as("City is still saved: the skipped barracks waits")
                .noneMatch(c -> c instanceof PlaceBuilding p && p.template().equals(DepthScene.BARRACKS));
        memory.set(Slots.TECH_DUE, 0); // City is researched
        List<Command> back = s.think((v, b, sink) -> new BuildManager(script, memory).think(v, b, sink));
        assertThat(back)
                .as("the barracks is placed once nothing is saved")
                .anyMatch(c -> c instanceof PlaceBuilding p && p.template().equals(DepthScene.BARRACKS));
    }

    @Test
    void aDropsiteByANodeOfTheResourceThePhaseLacksIsNotHeld() {
        StrategyScript script = StrategyScript.parse("""
            { "maxWorkers": 0, "ratio": { "iron": 1 }, "build": [], "housingMargin": 0, "soldierQueue": 3,
              "reserve": true, "dropsite": "strategos:building/storehouse",
              "attack": { "first": 100000, "every": 600, "min": 1, "muster": 24, "announce": 30 },
              "battle": { "focus": "wounded", "behind": 4, "retreat": 0.6, "reach": 28, "defence": 600 },
              "sites": [], %s }""".formatted(DEPTH));
        DepthScene s = new DepthScene();
        s.spawn(DepthScene.WORKER, DepthScene.OWN, 30, 30, 5);
        s.building(DepthScene.STOREHOUSE, DepthScene.OWN, 8, 8); // the dropsite the far one is measured against
        s.h.submit(
                dev.strategos.core.shared.PlayerSlot.ADMIN,
                new dev.strategos.core.protocol.SpawnDeposit(new TemplateId("strategos:building/iron_mine"), 52, 50));
        s.h.step();
        s.grant(1_000, 720, 1_000, 60); // a storehouse (wood 100) would take wood under City's 700; iron is what lacks
        AiMemory memory = new AiMemory();
        var city = s.h.core().queries().templates().tech(new dev.strategos.core.shared.TechId("strategos:phase_city"));
        memory.set(Slots.TECH_DUE, (s.h.tick() + 1) << 16 | (city.netIndex() + 1L));
        List<Command> sent = s.think((v, b, sink) -> new BuildManager(script, memory).think(v, b, sink));
        assertThat(sent)
                .as("the dropsite unlocks the iron the phase lacks")
                .anyMatch(c -> c instanceof PlaceBuilding p && p.template().equals(DepthScene.STOREHOUSE));
    }

    @Test
    void belowHalfTheWaveTheCheapestSoldierPassesAnyReserve() {
        DepthScene s = new DepthScene();
        s.building(DepthScene.STOREHOUSE, DepthScene.OWN, 40, 20);
        s.building(DepthScene.BARRACKS, DepthScene.OWN, 50, 40);
        s.grant(1_000, 100, 1_000, 60); // wood 100 is under City's 700: a soldier needing wood is held by the rule
        s.spawn(DepthScene.INFANTRY, DepthScene.OWN, 60, 60, 2); // of 24: under half
        AiMemory memory = new AiMemory();
        var city = s.h.core().queries().templates().tech(new dev.strategos.core.shared.TechId("strategos:phase_city"));
        memory.set(Slots.TECH_DUE, (s.h.tick() + 1) << 16 | (city.netIndex() + 1L));
        StrategyScript script = script(DEPTH);
        assertThat(s.think((v, b, sink) -> new ProductionManager(script, memory).think(v, b, sink)))
                .as("an army never sinks to nothing: the infantry passes the phase's wood")
                .anyMatch(Train.class::isInstance);
        s.spawn(DepthScene.INFANTRY, DepthScene.OWN, 70, 60, 10); // 12 of 24: not under half
        assertThat(s.think((v, b, sink) -> new ProductionManager(script, memory).think(v, b, sink)))
                .as("from half the wave on the rule holds")
                .noneMatch(Train.class::isInstance);
    }

    @Test
    void aSkippedStepIsMeasuredFromWhatStoodWhenItWasPassedOver() {
        String steps = "{\"building\": \"strategos:building/barracks\", \"population\": 0}";
        StrategyScript script = DepthScene.script(DEPTH, steps);
        DepthScene s = new DepthScene();
        s.spawn(DepthScene.WORKER, DepthScene.OWN, 30, 30, 5);
        s.building(DepthScene.BARRACKS, DepthScene.OWN, 90, 90); // the start kit's barracks
        s.grant(1_000, 1_000, 1_000, 60);
        AiMemory memory = new AiMemory();
        var city = s.h.core().queries().templates().tech(new dev.strategos.core.shared.TechId("strategos:phase_city"));
        memory.set(Slots.TECH_DUE, (s.h.tick() + 1) << 16 | (city.netIndex() + 1L));
        s.think((v, b, sink) -> new BuildManager(script, memory).think(v, b, sink)); // passed over
        memory.set(Slots.TECH_DUE, 0);
        List<Command> sent = s.think((v, b, sink) -> new BuildManager(script, memory).think(v, b, sink));
        assertThat(sent)
                .as("the kit's barracks does not satisfy the step it was skipped for")
                .anyMatch(c -> c instanceof PlaceBuilding p && p.template().equals(DepthScene.BARRACKS));
    }

    @Test
    void aBlockedStepLateInTheOrderIsSkippedAndCaughtUpToo() {
        // the shipped order's shape: a second barracks at index 11, then a Town building
        String filler = "{\"building\": \"strategos:building/house\", \"population\": 99},".repeat(11);
        String steps = filler + "{\"building\": \"strategos:building/barracks\", \"population\": 0},"
                + "{\"building\": \"strategos:building/tower\", \"population\": 0}";
        StrategyScript script = DepthScene.script(DEPTH, steps);
        DepthScene s = new DepthScene();
        s.spawn(DepthScene.WORKER, DepthScene.OWN, 30, 30, 5);
        s.grant(1_000, 1_000, 1_000, 60);
        AiMemory memory = new AiMemory();
        var city = s.h.core().queries().templates().tech(new dev.strategos.core.shared.TechId("strategos:phase_city"));
        memory.set(Slots.TECH_DUE, (s.h.tick() + 1) << 16 | (city.netIndex() + 1L));
        memory.set(Slots.STEP, 11);
        s.think((v, b, sink) -> new BuildManager(script, memory).think(v, b, sink));
        assertThat(memory.get(Slots.STEP)).as("passed over to the tower").isEqualTo(12);
        List<Command> tower = s.think((v, b, sink) -> new BuildManager(script, memory).think(v, b, sink));
        assertThat(tower)
                .anyMatch(c -> c instanceof PlaceBuilding p && p.template().equals(TOWER));
        s.building(TOWER, DepthScene.OWN, 90, 90);
        memory.set(Slots.STEP, 13);
        memory.set(Slots.PLACED, 0);
        memory.set(Slots.TECH_DUE, 0);
        assertThat(s.think((v, b, sink) -> new BuildManager(script, memory).think(v, b, sink)))
                .as("caught up once nothing is saved")
                .anyMatch(c -> c instanceof PlaceBuilding p && p.template().equals(DepthScene.BARRACKS));
    }

    @Test
    void aDepthScriptWithALongerOrderThanItCanSkipIsRefused() {
        String steps =
                "{\"building\": \"strategos:building/house\", \"population\": 1},".repeat(BuildManager.SKIPPABLE + 1);
        steps = steps.substring(0, steps.length() - 1);
        String long17 = steps;
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> DepthScene.script(DEPTH, long17))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at most " + BuildManager.SKIPPABLE);
        assertThat(DepthScene.script("", long17).build())
                .as("no depth block: any length")
                .hasSize(17);
    }

    @Test
    void anOldSaveWithAShorterMemoryStillRestores() {
        AiMemory old = new AiMemory();
        old.restore(java.util.Collections.nCopies(AiMemory.SIZE - 1, 7L));
        assertThat(old.get(Slots.TECH_DUE)).isEqualTo(7);
        assertThat(old.get(Slots.SKIPPED)).as("the new slot starts empty").isZero();
    }
}
