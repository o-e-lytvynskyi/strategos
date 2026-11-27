package dev.strategos.core.sim.ai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.Gather;
import dev.strategos.core.protocol.SpawnDeposit;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * QA-12: the economy sends no worker to a node it cannot bring a load home from in reasonable time. With the quarry's
 * three slots held, DifficultyTest's seed 3 sent a fourth and fifth stone gatherer 300 blocks across the map, and they
 * kept their near slots' claims on the way back: stone stood still and Town came late.
 */
class FarNodeTest {
    private static final TemplateId QUARRY = new TemplateId("strategos:building/quarry_large");

    private final DepthScene s = new DepthScene();
    private final StrategyScript script = StrategyScript.parse(
            "{ \"maxWorkers\": 8, \"ratio\": {\"stone\": 1}, \"build\": [], \"housingMargin\": 0, \"soldierQueue\": 0,"
                    + " \"attack\": { \"first\": 100000, \"every\": 600, \"min\": 1, \"muster\": 24, \"announce\": 30 },"
                    + " \"sites\": [] }");

    private List<Command> economy() {
        return s.think((v, b, sink) -> new EconomyManager(script, new AiMemory()).think(v, b, sink));
    }

    private void deposit(int x, int z) {
        s.h.submit(PlayerSlot.ADMIN, new SpawnDeposit(QUARRY, x, z));
        s.h.step();
    }

    @Test
    void anIdleWorkerGoesToTheNearQuarry() {
        deposit(40, 40);
        s.spawn(DepthScene.WORKER, DepthScene.OWN, 24, 24, 1);
        assertThat(economy()).anyMatch(Gather.class::isInstance);
    }

    @Test
    void anIdleWorkerStaysHomeRatherThanWalkAcrossTheMap() {
        deposit(150, 150); // about 180 blocks from the centre: the only quarry there is
        s.spawn(DepthScene.WORKER, DepthScene.OWN, 24, 24, 1);
        assertThat(economy()).noneMatch(Gather.class::isInstance);
    }

    @Test
    void aNodeNearTheSecondCentreIsGatheredAndOneFarFromBothIsNot() {
        s.building(DepthScene.CENTRE, DepthScene.OWN, 140, 20); // the second centre, 120 blocks from the first
        s.spawn(DepthScene.WORKER, DepthScene.OWN, 24, 24, 1);
        deposit(150, 60); // about 40 from the second, about 136 from the first
        assertThat(economy()).anyMatch(Gather.class::isInstance);
    }

    @Test
    void aNodeOverReachOfBothCentresIsNotGathered() {
        s.building(DepthScene.CENTRE, DepthScene.OWN, 140, 20);
        s.spawn(DepthScene.WORKER, DepthScene.OWN, 24, 24, 1);
        deposit(80, 150); // about 143 from both
        assertThat(economy()).noneMatch(Gather.class::isInstance);
    }
}
