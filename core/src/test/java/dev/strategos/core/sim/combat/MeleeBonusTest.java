package dev.strategos.core.sim.combat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.UnitHurt;
import dev.strategos.core.testkit.SimHarness;
import org.junit.jupiter.api.Test;

/** UNIT-07: a spearman's blow is multiplied 2.5 times against a class the template names, cavalry, and no other. */
class MeleeBonusTest {
    private static final TemplateId SPEARMAN = new TemplateId("strategos:unit/infantry");
    private static final TemplateId HORSEMAN = new TemplateId("strategos:unit/horseman");

    @Test
    void aSpearmansFirstBlowOnAHorsemanIsTwoAndAHalfTimesTheFormula() {
        // 3 hack at resistance 3 and 3 pierce at 2, then the bonus
        double expected = (3 * Math.pow(0.9, 3) + 3 * Math.pow(0.9, 2)) * 2.5;
        assertThat(firstBlow(HORSEMAN)).isCloseTo(expected, within(1e-4));
    }

    @Test
    void aSpearmansBlowOnAnotherSpearmanHasNoBonus() {
        double expected = 6 * Math.pow(0.9, 5);
        assertThat(firstBlow(SPEARMAN)).isCloseTo(expected, within(1e-4));
    }

    private static double firstBlow(TemplateId target) {
        SimHarness h = SimHarness.builder().size(32, 32).build();
        int attacker = spawn(h, SPEARMAN, 0, 10);
        int victim = spawn(h, target, 1, 11);
        h.submit(new PlayerSlot(0), Attack.replace(UnitSet.of(attacker), victim));
        assertThat(h.runUntil(() -> !h.events(UnitHurt.class).isEmpty(), 200)).isTrue();
        return h.events(UnitHurt.class).get(0).amount();
    }

    private static int spawn(SimHarness h, TemplateId template, int faction, double x) {
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(template, new FactionSlot(faction), new Vec2d(x, 10.5), 1, 0));
        return h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .map(UnitCreated.class::cast)
                .mapToInt(UnitCreated::handle)
                .findFirst()
                .orElseThrow();
    }
}
