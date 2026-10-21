package dev.strategos.core.sim;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StaggerTest {

    @Test
    void selectsATenthOfTheIndicesEveryTickAndEachIndexOnceInTenTicks() {
        int[] timesSelected = new int[1000];
        for (long tick = 0; tick < 10; tick++) {
            int selected = 0;
            for (int index = 0; index < 1000; index++) {
                if (Stagger.selects(tick, index, 10)) {
                    selected++;
                    timesSelected[index]++;
                }
            }
            assertThat(selected).as("tick %d", tick).isEqualTo(100);
        }
        assertThat(timesSelected).containsOnly(1);
    }
}
