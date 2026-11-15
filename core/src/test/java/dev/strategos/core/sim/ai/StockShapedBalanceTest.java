package dev.strategos.core.sim.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** QA-12: with the skirmish's finish knob, the balance's weights follow the stock; without it they are the ratio's. */
class StockShapedBalanceTest {
    @Test
    void aHoardHalvesItsWeightSoItsGatherersGoToTheScarceResource() {
        double food = EconomyManager.shape(24, EconomyManager.HIGH + 1, true);
        double wood = EconomyManager.shape(36, EconomyManager.LOW - 1, true);
        assertThat(food).isEqualTo(12);
        assertThat(wood).isEqualTo(72);
        assertThat(food / (food + wood)).as("food's share falls from 0.4").isLessThan(0.4);
    }

    @Test
    void aStockBetweenTheBoundsKeepsTheRatio() {
        assertThat(EconomyManager.shape(24, EconomyManager.LOW, true)).isEqualTo(24);
        assertThat(EconomyManager.shape(24, EconomyManager.HIGH, true)).isEqualTo(24);
    }

    @Test
    void withoutTheKnobTheRatioHoldsWhateverTheStock() {
        assertThat(EconomyManager.shape(24, EconomyManager.HIGH * 10, false)).isEqualTo(24);
        assertThat(EconomyManager.shape(24, 0, false)).isEqualTo(24);
        assertThat(EconomyManager.shape(0, 0, true)).isZero();
    }
}
