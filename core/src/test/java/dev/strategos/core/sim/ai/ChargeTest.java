package dev.strategos.core.sim.ai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.Target;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.protocol.UseAbility;
import dev.strategos.core.shared.AbilityId;
import dev.strategos.core.shared.PlayerSlot;
import java.util.List;
import org.junit.jupiter.api.Test;

/** AI-09 (M10 §2.19): a horseman uses {@code charge} on the approach: within range of an enemy, not in melee, not on cooldown. */
class ChargeTest {
    private final DepthScene s = new DepthScene();
    private final StrategyScript script = DepthScene.script(DepthScene.depth("{}"));
    private final int[] horses = s.spawn(DepthScene.HORSEMAN, DepthScene.OWN, 80, 80, 3);

    private List<Command> fight() {
        AiMemory memory = new AiMemory();
        memory.set(Slots.FIGHT, 1);
        return s.think((v, b, sink) -> Tactics.fight(v, b, sink, memory, Base.of(v), script, v.units()));
    }

    private boolean charges(List<Command> sent) {
        return sent.stream()
                .anyMatch(c -> c instanceof UseAbility u
                        && u.ability().equals(new AbilityId("charge"))
                        && u.units().contains(horses[0]));
    }

    @Test
    void theHorsemenChargeWithinRangeOfAnEnemy() {
        assertThat(script.depth().charge().range()).isEqualTo(14);
        s.spawn(DepthScene.INFANTRY, DepthScene.ENEMY, 92, 80, 1); // about 12 blocks off
        assertThat(charges(fight())).isTrue();
    }

    @Test
    void notFromOutOfRange() {
        s.spawn(DepthScene.INFANTRY, DepthScene.ENEMY, 100, 80, 1); // 20 blocks: in the fight's reach, not the charge's
        assertThat(charges(fight())).isFalse();
    }

    @Test
    void notOnceInMelee() {
        s.spawn(DepthScene.INFANTRY, DepthScene.ENEMY, 82, 80, 1);
        assertThat(charges(fight())).isFalse();
    }

    @Test
    void notWhileItCoolsDown() {
        s.spawn(DepthScene.INFANTRY, DepthScene.ENEMY, 92, 80, 1);
        s.h.submit(new PlayerSlot(0), UseAbility.replace(UnitSet.of(horses), new AbilityId("charge"), Target.none()));
        s.h.step();
        assertThat(charges(fight())).isFalse();
    }
}
