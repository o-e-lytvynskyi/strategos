package dev.strategos.core.client.audio;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.FxKind;
import org.junit.jupiter.api.Test;

/** AUD-01.1: every order outcome and combat event has its sound, carried as far as the camera's height needs. */
class FxMappingTest {
    @Test
    void everyOrderOutcomeAndCombatEventMapsToItsSound() {
        for (FxKind kind : FxKind.values()) {
            if (kind.carriesData()) {
                continue; // a block's own sound type (AUD-04)
            }
            assertThat(Sounds.of(kind).sound()).startsWith("minecraft:");
            assertThat(Sounds.of(kind).category()).isEqualTo(Sounds.Category.UNITS);
        }
        assertThat(Sounds.of(FxKind.UNIT_HIT)).isNotEqualTo(Sounds.of(FxKind.UNIT_DIED));
        assertThat(Sounds.of(CommandOutcome.ACCEPTED)).isEqualTo(Sounds.ORDER_ACCEPTED);
        assertThat(Sounds.of(CommandOutcome.PARTIAL)).isEqualTo(Sounds.ORDER_ACCEPTED);
        assertThat(Sounds.of(CommandOutcome.REJECTED)).isEqualTo(Sounds.ORDER_REFUSED);
        for (CommandOutcome outcome : CommandOutcome.values()) {
            assertThat(Sounds.of(outcome).category()).isEqualTo(Sounds.Category.UI);
        }
    }

    @Test
    void aUnitSoundCarriesFromTheCameraToPastTheGroundInView() {
        Sounds.Cue hit = Sounds.UNIT_HIT;
        assertThat(Sounds.volume(hit, 1.6, 20)).isEqualTo(hit.volume()); // first person: vanilla's own
        float atDefault = Sounds.volume(hit, 24, 55);
        double focus = 24 / Math.sin(Math.toRadians(55));
        double range = Math.max(1, atDefault) * Sounds.ATTENUATION;
        assertThat(1 - focus / range).isCloseTo(0.6, org.assertj.core.data.Offset.offset(1e-6)); // the focus at 60 %
        assertThat(Sounds.volume(hit, 64, 55)).isGreaterThan(atDefault);
        assertThat(Sounds.volume(hit, 24, 90)).isLessThan(atDefault);
    }

    @Test
    void aTickStartsAtMostSixSoundsOfAKind() {
        SoundBudget budget = new SoundBudget();
        int hits = 0;
        for (int k = 0; k < 20; k++) {
            hits += budget.take(FxKind.UNIT_HIT, 5) ? 1 : 0;
        }
        assertThat(hits).isEqualTo(SoundBudget.PER_TICK);
        assertThat(budget.take(FxKind.UNIT_DIED, 5)).isTrue(); // its own budget
        assertThat(budget.take(FxKind.UNIT_HIT, 6)).isTrue(); // the next tick starts afresh
    }
}
