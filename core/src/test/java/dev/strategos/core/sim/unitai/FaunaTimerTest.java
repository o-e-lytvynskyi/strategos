package dev.strategos.core.sim.unitai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.testkit.SimHarness;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** PERF-17: the wild animals' time costs a clock read pair per herd of a tick, never per animal. */
class FaunaTimerTest {
    private static final TemplateId BOAR = new TemplateId("strategos:unit/wild_boar");

    /** The clock reads one tick makes with {@code boars} animals in one herd (Gaia is the slot after the factions). */
    private static long readsPerTick(int boars) {
        AtomicLong reads = new AtomicLong();
        SimHarness h = SimHarness.builder()
                .size(128, 128)
                .clock(() -> reads.incrementAndGet())
                .build();
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(BOAR, new FactionSlot(2), new Vec2d(64, 64), boars, 3));
        h.step();
        long before = reads.get();
        h.step();
        return reads.get() - before;
    }

    @Test
    void aBiggerHerdCostsFewerReadsThanOnePerAnimal() {
        // other readers of the clock (the animals' path requests) add a few reads as a herd grows, so the bound is
        // loose: the per-animal timer read twice for each of the 35 more animals
        assertThat(readsPerTick(40) - readsPerTick(5)).isLessThan(35);
    }
}
