package dev.strategos.core.sim;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.combat.Damage;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import org.junit.jupiter.api.Test;

/** M6 §1.6: what the end screen shows is what the counters hold, and they survive a save. */
class MatchStatsTest {
    static final TemplateId CIVIC_CENTRE = new TemplateId("strategos:building/civic_centre");

    @Test
    void theEndScreenEqualsTheCounters() {
        SimHarness h = SimHarness.builder()
                .size(96, 64)
                .yBand(0, 31, 1)
                .rules(MatchRules.DEFAULTS.withPlay(new PlayRules(PlayRules.Victory.CONQUEST, false, false)))
                .build();
        h.publishAll(VoxelFixtures.flat(96, 64, 32, 1));
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(CIVIC_CENTRE, new FactionSlot(0), 4, 4, 0));
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(CIVIC_CENTRE, new FactionSlot(1), 70, 40, 0));
        h.step();
        h.spawn(new FactionSlot(0), new Vec2d(12, 12), 2, 2);
        int[] doomed = h.spawn(new FactionSlot(1), new Vec2d(64, 36), 3, 2);
        MatchState s = KernelAccess.state(h.core());
        h.runTicks(20);
        assertThat(h.events(MatchEnded.class)).isEmpty();

        for (int unit : doomed) {
            s.queueDamage(Damage.lethal(unit, Handle.NONE));
        }
        for (int handle : s.buildings().handles()) {
            if (s.buildings().faction(handle) == 1) {
                s.queueDamage(Damage.lethal(handle, Handle.NONE));
            }
        }
        assertThat(h.runUntil(() -> !h.events(MatchEnded.class).isEmpty(), 40)).isTrue();

        MatchStats stats = s.matchStats();
        assertThat(stats.unitsTrained(0)).isEqualTo(2);
        assertThat(stats.unitsTrained(1)).isEqualTo(3);
        assertThat(stats.unitsLost(0)).isZero();
        assertThat(stats.unitsLost(1)).isEqualTo(3);
        assertThat(stats.buildingsBuilt(0)).isEqualTo(1);
        assertThat(stats.buildingsBuilt(1)).isEqualTo(1);
        assertThat(stats.buildingsLost(0)).isZero();
        assertThat(stats.buildingsLost(1)).isEqualTo(1);

        SimHarness back = SimHarness.restore(h.core().snapshot(), s.templates());
        assertThat(back.stateHash()).isEqualTo(h.stateHash());
        MatchStats restored = KernelAccess.state(back.core()).matchStats();
        assertThat(restored.unitsTrained(1)).isEqualTo(3);
        assertThat(restored.unitsLost(1)).isEqualTo(3);
        assertThat(restored.buildingsLost(1)).isEqualTo(1);
    }

    @Test
    void aPhaseKeepsTheTickItWasReached() {
        MatchStats stats = new MatchStats(2);
        assertThat(stats.phaseTick(0, 1)).isEqualTo(-1);
        stats.phaseReached(0, 1, 420);
        assertThat(stats.phaseTick(0, 1)).isEqualTo(420);
        assertThat(stats.phaseTick(0, 0)).as("village was never researched").isEqualTo(-1);
        assertThat(stats.phaseTick(1, 1)).isEqualTo(-1);

        MatchStats back = new MatchStats(2);
        back.restore(stats.save());
        assertThat(back.phaseTick(0, 1)).isEqualTo(420);
        assertThat(back.phaseTick(1, 1)).isEqualTo(-1);
        assertThat(back.mix(7)).isEqualTo(stats.mix(7)).isNotEqualTo(7);
    }
}
