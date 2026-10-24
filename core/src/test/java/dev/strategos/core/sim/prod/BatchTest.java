package dev.strategos.core.sim.prod;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.CancelTrain;
import dev.strategos.core.protocol.TrainBatch;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.sim.OutputEvent;
import dev.strategos.core.sim.UnitCreated;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * PROD-02.1 in L1: a batch of five workers is paid in full at once, takes five of the population, and all five step out
 * on one tick, exactly round(200 × 5^0.8) ticks after it began; cancelled, it gives the five costs back.
 */
class BatchTest {
    @Test
    void aBatchOfFiveTakesTheUnitTimeTimesFiveToThePointEight() {
        ProdScene p = ProdScene.of(1_000, 0);
        long food = p.s.treasury().stock(0, 0);
        p.h.submit(ProdScene.P0, new TrainBatch(p.centre, TemplateSet.WORKER, 5));
        p.h.step();
        assertThat(p.s.treasury().stock(0, 0)).as("five costs at once").isEqualTo(food - 5 * 60_000);
        assertThat(p.s.buildings().queueSize(p.centre)).as("one entry").isOne();
        assertThat(p.s.buildings().batch(p.centre, 0)).isEqualTo(5);
        long expected = Math.round(200 * DetMath.pow(5, 0.8));
        List<UnitCreated> made = new ArrayList<>();
        long first = -1;
        for (int t = 1; t <= expected + 5 && made.isEmpty(); t++) {
            for (OutputEvent e : p.h.step().events()) {
                if (e instanceof UnitCreated c) {
                    made.add(c);
                    first = t;
                }
            }
        }
        assertThat(made).as("all five on one tick").hasSize(5);
        assertThat(first + 1)
                .as("ticks of training, the tick it joined the first of them")
                .isEqualTo(expected);
        assertThat(p.s.population().used(0))
                .as("five of the population, now the units'")
                .isGreaterThanOrEqualTo(5);
    }

    @Test
    void aCancelledBatchGivesTheFiveCostsBack() {
        ProdScene p = ProdScene.of(1_000, 0);
        long food = p.s.treasury().stock(0, 0);
        p.h.submit(ProdScene.P0, new TrainBatch(p.centre, TemplateSet.WORKER, 5));
        p.h.step();
        p.h.step();
        int used = p.s.population().used(0);
        p.h.submit(ProdScene.P0, new CancelTrain(p.centre, 0));
        p.h.step();
        assertThat(p.s.treasury().stock(0, 0)).isEqualTo(food);
        assertThat(p.s.population().used(0)).as("its reservation freed").isEqualTo(used - 5);
    }
}
