package dev.strategos.core.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The breach cooldowns' save (M9 spec §1.6 row 23): only living units' records, and a corrupt list refused. */
class BreachLogTest {
    @Test
    void aDeadUnitsRecordIsNeitherSavedNorHashed() {
        BreachLog log = new BreachLog();
        log.put(7, 3, 500);
        log.put(9, 4, 600);
        assertThat(log.save(h -> h == 7)).containsExactly(7L, 3L, 500L);
        BreachLog back = new BreachLog();
        back.restore(log.save(h -> h == 7));
        assertThat(back.mix(1L, h -> true)).isEqualTo(log.mix(1L, h -> h == 7));
        assertThat(back.resting(7, 3, 499)).isTrue();
    }

    @Test
    void aRepeatedOrOversizedHandleOrAShortListIsRefused() {
        BreachLog log = new BreachLog();
        assertThatThrownBy(() -> log.restore(List.of(7L, 3L, 500L, 7L, 4L, 600L)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> log.restore(List.of(1L << 40, 3L, 500L))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> log.restore(List.of(7L, 3L))).isInstanceOf(IllegalArgumentException.class);
    }
}
