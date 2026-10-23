package dev.strategos.core.sim.combat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dev.strategos.core.shared.Handle;
import java.util.List;
import org.junit.jupiter.api.Test;

/** CMB-01.1: each type resisted by 0.9 to its level, summed, then the bonuses for the target's classes. */
class DamageFormulaTest {
    private static final double[] NONE = {0, 0, 0};

    @Test
    void aLevelTakesATenthOffPerStep() {
        double[][] table = { // level, dealt by 10 hack
            {0, 10}, {1, 9}, {2, 8.1}, {3, 7.29}, {-1, 10 / 0.9}, {20, 10 * StrictMath.pow(0.9, 20)}
        };
        for (double[] row : table) {
            assertThat(DamageFormula.dealt(
                            Damage.of(1, Handle.NONE, DamageType.HACK, 10), new double[] {row[0], 0, 0}, List.of()))
                    .as("level " + row[0])
                    .isCloseTo(row[1], within(1e-12));
        }
    }

    @Test
    void eachTypeMeetsItsOwnResistance() {
        Damage mixed = new Damage(1, 2, 10, 5, 2, List.of(), false);
        assertThat(DamageFormula.dealt(mixed, new double[] {1, 2, 0}, List.of()))
                .isCloseTo(9 + 4.05 + 2, within(1e-12));
        assertThat(DamageFormula.dealt(mixed, NONE, List.of())).isEqualTo(17);
        assertThat(DamageFormula.dealt(Damage.of(1, 2, DamageType.PIERCE, 10), new double[] {5, 0, 5}, List.of()))
                .as("hack and crush resistances do not touch pierce")
                .isEqualTo(10);
    }

    @Test
    void bonusesApplyForTheTargetsClassesOnly() {
        Damage spear = new Damage(
                1, 2, 10, 0, 0, List.of(new Damage.Bonus("Cavalry", 3), new Damage.Bonus("Elephant", 2)), false);
        assertThat(DamageFormula.dealt(spear, NONE, List.of("Infantry"))).isEqualTo(10);
        assertThat(DamageFormula.dealt(spear, NONE, List.of("Cavalry", "Organic")))
                .isEqualTo(30);
        assertThat(DamageFormula.dealt(spear, NONE, List.of("Elephant", "Cavalry")))
                .isEqualTo(60);
        assertThat(DamageFormula.dealt(spear, new double[] {1, 0, 0}, List.of("Cavalry")))
                .isCloseTo(27, within(1e-12));
    }

    @Test
    void amountsAndBonusesMustBeFinite() {
        assertThatThrownBy(() -> new Damage(1, 2, Float.NaN, 0, 0, List.of(), false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Damage(1, 2, 0, -1, 0, List.of(), false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Damage.Bonus("Cavalry", Float.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
