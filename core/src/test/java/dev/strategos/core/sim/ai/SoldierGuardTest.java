package dev.strategos.core.sim.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** QA-12: the build order's reserve for Town does not hold a skirmish player's first soldiers back. */
class SoldierGuardTest {
    @Test
    void fewerSoldiersThanTheGuardAreTrainedPastTheReserve() {
        for (int soldiers = 0; soldiers < 6; soldiers++) {
            assertThat(ProductionManager.early(6, soldiers))
                    .as("%d soldiers", soldiers)
                    .isTrue();
        }
        assertThat(ProductionManager.early(6, 6)).isFalse();
        assertThat(ProductionManager.early(6, 20)).isFalse();
    }

    @Test
    void aGuardOfZeroLeavesTheReserveAlone() {
        assertThat(ProductionManager.early(0, 0)).isFalse();
    }

    @Test
    void theGuardIsAPerOpeningKnobDefaultingToTheScripts() {
        StrategyScript base = StrategyScript.parse(HeadlessMatch.read("ai/skirmish.json"));
        assertThat(base.early()).as("the base script's surplus-only rule").isZero();
        int rush = -1;
        for (int k = 0; k < base.openings().size(); k++) {
            if (base.openings().get(k).name().equals("rush")) {
                rush = k;
            }
        }
        assertThat(rush).isNotNegative();
        assertThat(base.opened(rush).early())
                .as("rush trains past Town's reserve")
                .isPositive();
        for (int k = 0; k < base.openings().size(); k++) {
            if (k != rush) {
                assertThat(base.opened(k).early())
                        .as(base.openings().get(k).name() + " keeps the script's")
                        .isEqualTo(base.early());
            }
        }
    }

    private static final long[] TOWN = {500_000, 600_000, 300_000, 200_000}; // food, wood, stone, iron
    private static final long[] SOLDIER = {60_000, 40_000, 0, 0};

    @Test
    void aStockThatCoversTownAloneTrainsNoEarlySoldier() {
        assertThat(ProductionManager.covers(TOWN, SOLDIER, TOWN)).isFalse();
    }

    @Test
    void aStockOfTownAndOneSoldierTrainsOne() {
        long[] stock = {TOWN[0] + SOLDIER[0], TOWN[1] + SOLDIER[1], TOWN[2], TOWN[3]};
        assertThat(ProductionManager.covers(stock, SOLDIER, TOWN)).isTrue();
        stock[1] -= 1; // a wood short of the soldier
        assertThat(ProductionManager.covers(stock, SOLDIER, TOWN)).isFalse();
    }
}
