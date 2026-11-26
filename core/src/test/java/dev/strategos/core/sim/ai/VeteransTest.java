package dev.strategos.core.sim.ai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.Garrison;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.UnitView;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * AI-09 (M10 §2.19): the AI retreats units by rank, veterans first; a veteran under {@code below} of its health leaves
 * the fight and comes back at {@code resume}.
 */
class VeteransTest {
    private final DepthScene s = new DepthScene();
    private final StrategyScript script = DepthScene.script(DepthScene.depth("{}"));

    private boolean resting(int unit, Map<Integer, Integer> ranks, Map<Integer, Double> health) {
        boolean[] out = new boolean[1];
        s.think(
                (v, b, sink) -> {
                    UnitView u = v.units().stream()
                            .filter(x -> x.handle() == unit)
                            .findFirst()
                            .orElseThrow();
                    out[0] = HealerManager.resting(v, u, script.depth().veterans());
                },
                ranks,
                health);
        return out[0];
    }

    private static boolean orders(List<Command> sent, int unit) {
        return sent.stream().anyMatch(c -> c instanceof Move m && m.units().contains(unit));
    }

    @Test
    void aWaveFallingBackPullsItsVeteransBeforeTheRankedOutOnes() {
        int[] own = s.spawn(DepthScene.INFANTRY, DepthScene.OWN, 80, 80, 2);
        s.spawn(DepthScene.INFANTRY, DepthScene.ENEMY, 95, 80, 6); // the enemy, strong and in reach
        Map<Integer, Integer> ranks = DepthScene.ranked(1, own[0]); // own[1] is rank 0
        Map<Integer, Double> health = DepthScene.healthy(0.3, own);
        AiMemory memory = new AiMemory();
        memory.set(Slots.FIGHT, 1);
        Base[] base = new Base[1];

        List<Command> first = s.think(
                (v, b, sink) -> {
                    base[0] = Base.of(v);
                    Tactics.fight(v, b, sink, memory, base[0], script, v.units());
                },
                ranks,
                health);
        assertThat(memory.get(Slots.FIGHT)).as("the wave falls back").isEqualTo(2);
        assertThat(orders(first, own[0])).as("the veteran goes first").isTrue();
        assertThat(orders(first, own[1]))
                .as("the rank-0 soldier holds the line")
                .isFalse();

        List<Command> second =
                s.think((v, b, sink) -> Tactics.fight(v, b, sink, memory, base[0], script, v.units()), ranks, health);
        assertThat(orders(second, own[1])).as("then the rest follow").isTrue();
    }

    @Test
    void aVeteranThatFellUnderBelowLeavesTheFightAndARankZeroOneDoesNot() {
        s.spawn(DepthScene.HEALER, DepthScene.OWN, 28, 28, 1); // someone to heal it
        int[] own = s.spawn(DepthScene.INFANTRY, DepthScene.OWN, 80, 80, 2);
        Map<Integer, Integer> ranks = DepthScene.ranked(1, own[0]);
        Map<Integer, Double> health = DepthScene.healthy(0.2, own);

        List<Command> sent = s.think((v, b, sink) -> new HealerManager(script).think(v, b, sink), ranks, health);

        assertThat(orders(sent, own[0])).as("the veteran at 20% falls back").isTrue();
        assertThat(orders(sent, own[1])).as("the rank-0 soldier at 20% stays").isFalse();
        assertThat(sent).noneMatch(c -> c instanceof Garrison g && g.units().contains(own[1]));
    }

    @Test
    void aVeteranHealedToResumeReturns() {
        int[] own = s.spawn(DepthScene.INFANTRY, DepthScene.OWN, 80, 80, 1);
        Map<Integer, Integer> ranks = DepthScene.ranked(1, own);

        assertThat(resting(own[0], ranks, DepthScene.healthy(0.2, own)))
                .as("under below")
                .isTrue();
        assertThat(resting(own[0], ranks, DepthScene.healthy(0.5, own)))
                .as("between below and resume, idle: still healing")
                .isTrue();
        assertThat(resting(own[0], ranks, DepthScene.healthy(0.85, own)))
                .as("at resume or more: back in the army")
                .isFalse();
        assertThat(resting(own[0], Map.of(), DepthScene.healthy(0.2, own)))
                .as("a rank-0 soldier never rests")
                .isFalse();

        s.h.submit(new PlayerSlot(0), Move.replace(UnitSet.of(own), new Vec2d(100, 100)));
        s.h.step();
        assertThat(resting(own[0], ranks, DepthScene.healthy(0.5, own)))
                .as("between below and resume but fighting on: not pulled out")
                .isFalse();
    }
}
