package dev.strategos.core.sim.ai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.Train;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.shared.Vec2d;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * AI-09 (M10 §2.19): the AI trains healers, up to the script's {@code healers}, and keeps them behind the army, between
 * the army and home.
 */
class HealerManagerTest {
    private final DepthScene s = new DepthScene();
    private final StrategyScript script = DepthScene.script(DepthScene.depth("{}"));
    private final AiMemory memory = new AiMemory();

    private List<Command> train() {
        return s.think((v, b, sink) -> new ProductionManager(script, memory).think(v, b, sink));
    }

    private static boolean trainsHealer(List<Command> sent) {
        return sent.stream().anyMatch(c -> c instanceof Train t && t.template().equals(DepthScene.HEALER));
    }

    @Test
    void healersAreTrainedUpToTheScriptsNumber() {
        s.building(DepthScene.TEMPLE, DepthScene.OWN, 40, 20);
        s.grant(2_000, 2_000, 2_000, 2_000);
        assertThat(script.depth().healers()).isEqualTo(3);

        assertThat(trainsHealer(train())).as("none yet").isTrue();
        s.spawn(DepthScene.HEALER, DepthScene.OWN, 30, 30, 2);
        assertThat(trainsHealer(train())).as("two of three").isTrue();
        s.spawn(DepthScene.HEALER, DepthScene.OWN, 34, 34, 1);
        assertThat(trainsHealer(train())).as("three of three: no more").isFalse();
    }

    @Test
    void theHealersStandBehindTheArmyBetweenItAndHome() {
        int[] healers = s.spawn(DepthScene.HEALER, DepthScene.OWN, 28, 28, 2);
        s.spawn(DepthScene.INFANTRY, DepthScene.OWN, 80, 80, 6); // the army, far out
        Vec2d home = new Vec2d(20.5, 20.5);

        List<Command> sent = s.think((v, b, sink) -> new HealerManager(script).think(v, b, sink));

        Move move = (Move) sent.stream()
                .filter(c -> c instanceof Move m && m.units().contains(healers[0]))
                .findFirst()
                .orElseThrow();
        assertThat(move.units().contains(healers[1])).isTrue();
        double armyToHome = DetMath.length(80 - home.x(), 80 - home.z());
        double stationToHome =
                DetMath.length(move.target().x() - home.x(), move.target().z() - home.z());
        double stationToArmy =
                DetMath.length(move.target().x() - 80, move.target().z() - 80);
        assertThat(stationToHome).as("nearer home than the army").isLessThan(armyToHome - 5);
        assertThat(stationToArmy).as("close behind it, not at home").isLessThan(armyToHome / 2);
        assertThat(stationToHome + stationToArmy)
                .as("on the way between them")
                .isCloseTo(armyToHome, org.assertj.core.data.Offset.offset(2.0));
    }
}
