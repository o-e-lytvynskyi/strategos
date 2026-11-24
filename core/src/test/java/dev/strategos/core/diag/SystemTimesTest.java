package dev.strategos.core.diag;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.testkit.ManualClock;
import org.junit.jupiter.api.Test;

/** PERF-16's system times: each system's run and the territory job's join add up apart, from the core's clock. */
final class SystemTimesTest {
    @Test
    void eachTimeAddsUpApartFromTheClock() {
        ManualClock clock = new ManualClock();
        SystemTimes times = new SystemTimes();
        times.clock(clock);
        long start = times.start();
        clock.advance(300);
        long joinStart = times.start();
        clock.advance(50);
        times.joined(joinStart);
        times.territory(start);
        start = times.start();
        clock.advance(70);
        times.garrison(start);
        start = times.start();
        clock.advance(9);
        times.capture(start);
        assertThat(times.stats()).isEqualTo(new SystemTimes.Stats(350, 50, 70, 9));
    }

    @Test
    void untilAClockIsSetEverythingReadsZero() {
        SystemTimes times = new SystemTimes();
        times.territory(times.start());
        assertThat(times.stats()).isEqualTo(new SystemTimes.Stats(0, 0, 0, 0));
    }
}
