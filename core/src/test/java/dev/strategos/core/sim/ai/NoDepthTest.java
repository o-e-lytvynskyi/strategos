package dev.strategos.core.sim.ai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.FormationMove;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.Research;
import dev.strategos.core.protocol.Train;
import dev.strategos.core.protocol.UseAbility;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * AI-09: a script without a {@code depth} block (the shipped one, for now) plays as it did: no research beyond its build
 * order, no healers, no charge, no march in formation, no veterans spared.
 */
class NoDepthTest {
    private final DepthScene s = new DepthScene();
    private final StrategyScript script = DepthScene.plain();

    @Test
    void theBlockIsOff() {
        assertThat(script.depth()).isEqualTo(Depth.NONE);
    }

    @Test
    void nothingIsResearchedAndNoHealerTrained() {
        s.building(DepthScene.STOREHOUSE, DepthScene.OWN, 40, 20);
        s.building(DepthScene.TEMPLE, DepthScene.OWN, 40, 40);
        s.grant(5_000, 5_000, 5_000, 5_000);
        s.ticks(20 * 2_000);
        AiMemory memory = new AiMemory();

        List<Command> sent = s.think((v, b, sink) -> {
            new TechManager(script, memory).think(v, b, sink);
            new ProductionManager(script, memory).think(v, b, sink);
        });

        assertThat(sent).noneMatch(Research.class::isInstance);
        assertThat(sent).noneMatch(c -> c instanceof Train t && t.template().equals(DepthScene.HEALER));
    }

    @Test
    void noChargeAndNoVeteransFirst() {
        int[] horses = s.spawn(DepthScene.HORSEMAN, DepthScene.OWN, 80, 80, 2);
        s.spawn(DepthScene.INFANTRY, DepthScene.ENEMY, 92, 80, 1);
        AiMemory memory = new AiMemory();
        memory.set(Slots.FIGHT, 1);
        assertThat(s.think((v, b, sink) -> Tactics.fight(v, b, sink, memory, Base.of(v), script, v.units())))
                .noneMatch(UseAbility.class::isInstance);

        // a falling back wave: every soldier at once, whatever its rank
        s.spawn(DepthScene.INFANTRY, DepthScene.ENEMY, 95, 82, 6);
        AiMemory retreat = new AiMemory();
        retreat.set(Slots.FIGHT, 1);
        List<Command> sent = s.think(
                (v, b, sink) -> Tactics.fight(v, b, sink, retreat, Base.of(v), script, v.units()),
                DepthScene.ranked(1, horses[0]),
                DepthScene.healthy(0.3, horses));
        assertThat(retreat.get(Slots.FIGHT)).isEqualTo(2);
        assertThat(sent)
                .anyMatch(c -> c instanceof Move m
                        && m.units().contains(horses[0])
                        && m.units().contains(horses[1]));
    }

    @Test
    void aFarWaveAttackMovesRatherThanMarchesInFormation() {
        s.spawn(DepthScene.INFANTRY, DepthScene.OWN, 30, 30, 6);
        AiMemory memory = new AiMemory();
        memory.set(Slots.MUSTER, 1);
        s.ticks(20 * script.attack().announce() + 5);

        List<Command> sent = s.think((v, b, sink) -> new MilitaryManager(script, memory).think(v, b, sink));

        assertThat(sent).noneMatch(FormationMove.class::isInstance);
        assertThat(sent).anyMatch(c -> c instanceof dev.strategos.core.protocol.AttackMove);
    }

    @Test
    void aHealerOnTheFieldIsLeftAlone() {
        int[] healers = s.spawn(DepthScene.HEALER, DepthScene.OWN, 60, 60, 2);
        s.spawn(DepthScene.INFANTRY, DepthScene.OWN, 90, 90, 4);

        List<Command> sent = s.think((v, b, sink) -> new HealerManager(script).think(v, b, sink));

        assertThat(sent).noneMatch(c -> c instanceof Move m && m.units().contains(healers[0]));
        assertThat(sent).isEmpty();
    }
}
