package dev.strategos.core.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class StepPacerTest {
    @Test
    void firstDeliveryTickGetsItsFullInterval() {
        AtomicLong now = new AtomicLong(1_000);
        List<Long> waits = new ArrayList<>();
        StepPacer pacer = new StepPacer(50, now::get, nanos -> {
            waits.add(nanos);
            now.addAndGet(nanos);
        });
        pacer.awaitTick(); // first tick after submitting the scattered order
        assertThat(now.get()).isEqualTo(1_050);
        now.addAndGet(7); // work inside the group order's tick
        pacer.awaitTick(); // STRICT K=2 delivery of the scattered order
        assertThat(now.get()).isEqualTo(1_100);
        assertThat(waits).containsExactly(50L, 43L);
    }

    @Test
    void slowTicksDoNotCreateCatchUpBursts() {
        AtomicLong now = new AtomicLong();
        List<Long> waits = new ArrayList<>();
        StepPacer pacer = new StepPacer(50, now::get, nanos -> {
            waits.add(nanos);
            now.addAndGet(nanos);
        });
        pacer.awaitTick();
        now.addAndGet(120);
        pacer.awaitTick();
        pacer.awaitTick();
        assertThat(waits).containsExactly(50L, 50L);
        assertThat(now.get()).isEqualTo(220);
    }

    @Test
    void earlyWakeupsWaitForTheRemainingInterval() {
        AtomicLong now = new AtomicLong();
        StepPacer pacer = new StepPacer(50, now::get, nanos -> now.addAndGet(Math.min(nanos, 10)));
        pacer.awaitTick();
        assertThat(now.get()).isEqualTo(50);
    }
}
