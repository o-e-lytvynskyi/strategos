package dev.strategos.core.sim.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.Train;
import dev.strategos.core.shared.TechId;
import dev.strategos.core.sim.KernelAccess;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** AI-09: the worker cap grows with the phase, by the script's {@code depth.workers}, on top of the opening's. */
class DepthWorkersTest {
    private static final String TOWN = "strategos:phase_town";
    private static final String CITY = "strategos:phase_city";
    private static final String BASE =
            "{\"maxWorkers\":24,\"ratio\":{},\"build\":[],\"housingMargin\":0,\"soldierQueue\":0,"
                    + "\"attack\":{\"first\":1,\"every\":600,\"min\":1,\"muster\":24,\"announce\":30},\"sites\":[]";

    private static StrategyScript script(String workers) {
        return StrategyScript.parse(BASE + ",\"depth\":{\"workers\":" + workers + "}}");
    }

    private static int cap(StrategyScript s, Set<String> researched) {
        return s.maxWorkers() + s.depth().extraWorkers(researched::contains);
    }

    @Test
    void theCapGrowsByEachPhaseResearched() {
        StrategyScript s = script("{\"" + TOWN + "\":12,\"" + CITY + "\":8}");
        assertThat(cap(s, Set.of())).as("Village").isEqualTo(24);
        assertThat(cap(s, Set.of(TOWN))).as("Town").isEqualTo(36);
        assertThat(cap(s, Set.of(TOWN, CITY))).as("City").isEqualTo(44);
    }

    @Test
    void aScriptWithoutTheFieldKeepsItsCap() {
        StrategyScript s = StrategyScript.parse(BASE + "}");
        assertThat(cap(s, Set.of(TOWN, CITY))).isEqualTo(24);
        assertThat(Depth.NONE.extraWorkers(id -> true)).isZero();
    }

    @Test
    void aNegativeIncrementIsRefused() {
        assertThatThrownBy(() -> script("{\"" + TOWN + "\":-1}")).isInstanceOf(IllegalArgumentException.class);
    }

    /** The production manager, thought over a scene with {@code maxWorkers} (3) workers, Town researched or not. */
    private static boolean trainsAWorker(boolean town) {
        StrategyScript s = StrategyScript.parse(
                "{\"maxWorkers\":3,\"ratio\":{},\"build\":[],\"housingMargin\":0,\"soldierQueue\":0,"
                        + "\"attack\":{\"first\":1,\"every\":600,\"min\":1,\"muster\":24,\"announce\":30},\"sites\":[],"
                        + "\"depth\":{\"workers\":{\"" + TOWN + "\":1}}}");
        DepthScene scene = new DepthScene();
        scene.spawn(DepthScene.WORKER, DepthScene.OWN, 30, 30, 3);
        scene.grant(5_000, 5_000, 5_000, 5_000);
        if (town) {
            KernelAccess.state(scene.h.core()).progress().complete(0, new TechId(TOWN));
        }
        AiMemory memory = new AiMemory();
        List<Command> sent = scene.think((v, b, sink) -> new ProductionManager(s, memory).think(v, b, sink));
        return sent.stream().anyMatch(c -> c instanceof Train t && t.template().equals(DepthScene.WORKER));
    }

    @Test
    void theProductionManagerTrainsOneMoreWorkerOnceTownIsResearched() {
        assertThat(trainsAWorker(false)).as("Village: at the cap").isFalse();
        assertThat(trainsAWorker(true)).as("Town: one above the cap").isTrue();
    }
}
