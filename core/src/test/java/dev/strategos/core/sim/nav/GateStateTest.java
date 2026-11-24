package dev.strategos.core.sim.nav;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import org.junit.jupiter.api.Test;

/** PATH-12 in a match (M9 spec §2.9): the gate's footprint, its save and hash, and its door. */
class GateStateTest {
    private static final int[] GATE = {40 << 16 | 40, 40 << 16 | 41};

    private static SimHarness match() {
        SimHarness h =
                SimHarness.builder().size(96, 96).yBand(0, 31, 1).factions(2).build();
        h.publishAll(VoxelFixtures.flat(96, 96, 32, 1));
        h.step();
        return h;
    }

    @Test
    void aGateBlocksItsColumnsIsHashedWhileItStandsAndIsSaved() {
        SimHarness h = match();
        MatchState s = KernelAccess.state(h.core());
        long bare = h.stateHash();

        int id = s.addGate(0, GATE);
        h.runTicks(5);
        assertThat(s.footprintAt(40, 40)).isTrue();
        assertThat(s.surfaces().count(40, 40)).as("no surface in a gate column").isZero();
        long withGate = h.stateHash();
        SimHarness twin = SimHarness.restore(h.core().snapshot(), TemplateSet.builtin());
        MatchState restored = KernelAccess.state(twin.core());

        assertThat(withGate).isNotEqualTo(bare);
        assertThat(restored.gates().ids()).containsExactly(id);
        assertThat(restored.gates().owner(id)).isZero();
        assertThat(restored.gates().columns(id)).containsExactly(GATE);
        assertThat(restored.footprintAt(40, 41)).isTrue();
        assertThat(twin.stateHash())
                .as("the restored state hashes as the saved one")
                .isEqualTo(withGate);

        s.removeGate(id);
        h.runTicks(5);
        assertThat(s.footprintAt(40, 40)).isFalse();
        assertThat(s.gates().empty()).isTrue();
        assertThat(s.surfaces().count(40, 40)).isEqualTo(1);
        assertThat(s.gates().add(1, GATE)).as("the lowest free id again").isZero();
    }

    @Test
    void aGateIsOpenWhileAnAlliedUnitIsWithinThreeBlocksAndNeverForAnEnemy() {
        SimHarness h = match();
        MatchState s = KernelAccess.state(h.core());
        int id = s.addGate(0, GATE);
        h.runTicks(3);
        assertThat(GateDoors.open(s, id)).as("nobody near").isFalse();

        h.spawn(new FactionSlot(1), new Vec2d(40.5, 46), 1, 2.0); // 4.5 from the nearer column
        h.runTicks(3);
        assertThat(GateDoors.open(s, id)).as("an enemy, near or far").isFalse();

        h.spawn(new FactionSlot(0), new Vec2d(40.5, 47.5), 1, 2.0);
        h.runTicks(3);
        assertThat(GateDoors.open(s, id)).as("the owner's, 6 away").isFalse();

        h.spawn(new FactionSlot(0), new Vec2d(43, 42), 1, 2.0); // within 3 of (40, 41)
        h.runTicks(3);
        assertThat(GateDoors.open(s, id)).as("the owner's, near").isTrue();
    }
}
