package dev.strategos.core.sim.stats;

import static dev.strategos.core.sim.stats.StatsFixture.HACK;
import static dev.strategos.core.sim.stats.StatsFixture.HEALTH;
import static dev.strategos.core.sim.stats.StatsFixture.template;
import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.IdentityStore;
import dev.strategos.core.sim.MatchBus;
import org.junit.jupiter.api.Test;

/**
 * DATA-02.5 (spec §7.2): the breakdown gives the base value, each modifier that takes part with its source and stage in
 * the order evaluation applies them, and the result a read returns.
 */
class StatBreakdownTest {
    private static final FactionSlot F0 = new FactionSlot(0);

    @Test
    void baseEachModifierAndResult() {
        IdentityStore identity = new IdentityStore();
        identity.ensureCapacity(1);
        identity.set(0, template("spearman"), 0);
        StatService stats = StatsFixture.service(new MatchBus(), identity);
        Modifier stage1 = Modifier.mul(HEALTH, 1.5, 4).atStage(1);
        Modifier add = Modifier.add(HEALTH, 10, 1);
        Modifier mul = Modifier.mul(HEALTH, 2, 2);
        Modifier replace = Modifier.replace(HEALTH, 50, 3);
        Modifier losing = Modifier.replace(HEALTH, 40, 9);
        stats.add(F0, stage1);
        stats.add(F0, add);
        stats.add(F0, mul);
        stats.add(F0, replace);
        stats.add(F0, losing);
        stats.add(F0, Modifier.mul(HEALTH, 3, 5).withFilter("Cavalry"));
        stats.add(F0, Modifier.mul(HACK, 3, 6));
        Modifier own = Modifier.add(HEALTH, 7, 8).atStage(1);
        stats.addPersonal(Handle.of(0, 1), own);

        StatBreakdown block = stats.blockBreakdown(template("spearman"), 0, HEALTH);
        StatBreakdown unit = stats.breakdown(0, HEALTH);

        assertThat(block).isNotNull();
        assertThat(block.base()).isEqualTo(100);
        assertThat(block.modifiers()).containsExactly(replace, mul, add, stage1);
        assertThat(block.result()).isEqualTo((50 * 2 + 10) * 1.5);
        assertThat(block.result()).isEqualTo(stats.blockValue(template("spearman"), 0, stats.path(HEALTH)));
        assertThat(unit).isNotNull();
        assertThat(unit.modifiers()).containsExactly(replace, mul, add, stage1, own);
        assertThat(unit.result()).isEqualTo((50 * 2 + 10) * 1.5 + 7).isEqualTo(stats.read(0, stats.path(HEALTH)));
    }

    @Test
    void aPathTheTemplateLacksHasNoBaseAndNoResult() {
        StatService stats = StatsFixture.service();
        stats.add(F0, Modifier.add(HACK, 3, 1));

        StatBreakdown b = stats.blockBreakdown(template("archer"), 0, HACK);

        assertThat(b).isNotNull();
        assertThat(b.base()).isNaN();
        assertThat(b.modifiers()).isEmpty();
        assertThat(b.result()).isNaN();
        assertThat(stats.blockBreakdown(template("archer"), 0, "Nothing/here")).isNull();
    }
}
