package dev.strategos.core.sim;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.sim.DayClock.DayPhase;
import org.junit.jupiter.api.Test;

/** MATCH-16: the day clock is a pure function of the tick; the phases and their boundaries are exact. */
class DayClockTest {
    @Test
    void theMatchStartsAtNoonAndTheDayWrapsAtTheCycle() {
        assertThat(DayClock.timeOfDay(0)).isEqualTo(6_000);
        assertThat(DayClock.timeOfDay(DayClock.CYCLE - DayClock.START)).isZero();
        assertThat(DayClock.timeOfDay(DayClock.CYCLE)).isEqualTo(6_000);
        assertThat(DayClock.timeOfDay(5L * DayClock.CYCLE + 17)).isEqualTo(6_017);
    }

    @Test
    void thePhasesChangeExactlyAtTheirBoundaries() {
        long dusk = DayClock.DUSK_FROM - DayClock.START;
        long night = DayClock.NIGHT_FROM - DayClock.START;
        long dawn = DayClock.DAWN_FROM - DayClock.START;
        assertThat(DayClock.phase(0)).isEqualTo(DayPhase.DAY);
        assertThat(DayClock.phase(dusk - 1)).isEqualTo(DayPhase.DAY);
        assertThat(DayClock.phase(dusk)).isEqualTo(DayPhase.DUSK);
        assertThat(DayClock.phase(night - 1)).isEqualTo(DayPhase.DUSK);
        assertThat(DayClock.phase(night)).isEqualTo(DayPhase.NIGHT);
        assertThat(DayClock.phase(dawn - 1)).isEqualTo(DayPhase.NIGHT);
        assertThat(DayClock.phase(dawn)).isEqualTo(DayPhase.DAWN);
        assertThat(DayClock.phase(DayClock.CYCLE - DayClock.START - 1)).isEqualTo(DayPhase.DAWN);
        assertThat(DayClock.phase(DayClock.CYCLE - DayClock.START)).isEqualTo(DayPhase.DAY);
        assertThat(DayClock.night(night - 1)).isFalse();
        assertThat(DayClock.night(night)).isTrue();
        assertThat(DayClock.night(dawn - 1)).isTrue();
        assertThat(DayClock.night(dawn)).isFalse();
        assertThat(DayClock.night(night + DayClock.CYCLE)).as("the next night").isTrue();
    }

    @Test
    void ticksToChangeCountsToTheNextBoundary() {
        long night = DayClock.NIGHT_FROM - DayClock.START;
        assertThat(DayClock.ticksToChange(night - 5)).isEqualTo(5);
        assertThat(DayClock.ticksToChange(night)).isEqualTo(DayClock.DAWN_FROM - DayClock.NIGHT_FROM);
        assertThat(DayClock.ticksToChange(DayClock.CYCLE - DayClock.START - 1)).isEqualTo(1);
    }

    @Test
    void darknessRampsThroughDuskAndDawnAndHoldsAtNight() {
        long dusk = DayClock.DUSK_FROM - DayClock.START;
        long night = DayClock.NIGHT_FROM - DayClock.START;
        long dawn = DayClock.DAWN_FROM - DayClock.START;
        assertThat(DayClock.darkness(0)).isZero();
        assertThat(DayClock.darkness(dusk)).isZero();
        assertThat(DayClock.darkness(dusk + (night - dusk) / 2)).isEqualTo(0.5);
        assertThat(DayClock.darkness(night)).isEqualTo(1);
        assertThat(DayClock.darkness(dawn - 1)).isEqualTo(1);
        assertThat(DayClock.darkness(dawn)).isEqualTo(1);
        assertThat(DayClock.darkness(dawn + (DayClock.CYCLE - DayClock.DAWN_FROM) / 2))
                .isEqualTo(0.5);
        assertThat(DayClock.darkness(DayClock.CYCLE - DayClock.START)).isZero();
    }
}
