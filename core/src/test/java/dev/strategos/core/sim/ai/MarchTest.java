package dev.strategos.core.sim.ai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.AttackMove;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.FormationMove;
import dev.strategos.core.protocol.FormationShape;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * AI-09 (M10 §2.19): a wave farther than {@code march} blocks from its target marches in formation, and leaves the
 * formation when the fight starts.
 */
class MarchTest {
    private final DepthScene s = new DepthScene();
    private final StrategyScript script = DepthScene.script(DepthScene.depth("{}"));

    /** The wave's departure: its order, thought once the muster's warning has run. */
    private List<Command> departure(double x, double z, int n) {
        int[] army = s.spawn(DepthScene.INFANTRY, DepthScene.OWN, x, z, n);
        assertThat(army).hasSize(n);
        AiMemory memory = new AiMemory();
        memory.set(Slots.MUSTER, 1);
        s.ticks(20 * script.attack().announce() + 5);
        return s.think((v, b, sink) -> new MilitaryManager(script, memory).think(v, b, sink));
    }

    private static boolean formation(List<Command> sent) {
        return sent.stream().anyMatch(c -> c instanceof FormationMove f && f.shape() == FormationShape.BOX);
    }

    private static boolean attackMove(List<Command> sent) {
        return sent.stream().anyMatch(AttackMove.class::isInstance);
    }

    @Test
    void aWaveFarFromItsTargetLeavesAsAFormation() {
        List<Command> sent = departure(30, 30, 6); // about 130 blocks from the enemy's front
        assertThat(formation(sent)).isTrue();
        assertThat(attackMove(sent)).isFalse();
    }

    @Test
    void aWaveNearItsTargetAttackMoves() {
        List<Command> sent = departure(95, 95, 6); // inside the march's 70 blocks of the front
        assertThat(formation(sent)).isFalse();
        assertThat(attackMove(sent)).isTrue();
    }

    @Test
    void aHandfulWalksAsItIs() {
        List<Command> sent = departure(30, 30, March.FEWEST - 1);
        assertThat(formation(sent)).isFalse();
        assertThat(attackMove(sent)).isTrue();
    }

    @Test
    void theFormationIsLeftToWalkUntilTheFightStartsThenDissolved() {
        int[] army = s.spawn(DepthScene.INFANTRY, DepthScene.OWN, 80, 80, 6);
        s.h.submit(new PlayerSlot(0), FormationMove.replace(UnitSet.of(army), new Vec2d(100, 100), FormationShape.BOX));
        s.ticks(3);
        AiMemory memory = new AiMemory();
        memory.set(Slots.FIGHT, 1);
        boolean[] inFormation = new boolean[1];

        List<Command> marching = s.think((v, b, sink) -> {
            inFormation[0] = v.units().stream().anyMatch(u -> v.formation(u) != 0);
            Tactics.fight(v, b, sink, memory, Base.of(v), script, v.units());
        });
        assertThat(inFormation[0]).as("the wave keeps a formation").isTrue();
        assertThat(marching)
                .as("no enemy in reach: the formation is left to walk")
                .isEmpty();

        s.spawn(DepthScene.INFANTRY, DepthScene.ENEMY, 95, 80, 2); // the fight starts
        List<Command> fighting =
                s.think((v, b, sink) -> Tactics.fight(v, b, sink, memory, Base.of(v), script, v.units()));
        assertThat(fighting)
                .as("the enemy in reach: the wave is given its attack orders")
                .anyMatch(c -> c instanceof Attack a && a.units().contains(army[0]));
    }
}
