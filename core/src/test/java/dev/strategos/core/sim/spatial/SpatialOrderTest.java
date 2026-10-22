package dev.strategos.core.sim.spatial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.testkit.SimHarness;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * CORE-08.2 (spec §7.2): results come in unit index order, the handle's slot, across cells and whatever a slot's
 * generation; a query may not start inside another's consumer; in a match the grid holds where units stood after the
 * last movement, and a restored match's grid answers as the original's did.
 */
class SpatialOrderTest {
    @Test
    void resultsComeByIndexAcrossCells() {
        SpatialWorld w = new SpatialWorld();
        Random r = new Random(5);
        for (int k = 0; k < 300; k++) { // scattered over many cells, added in no spatial order
            w.add(r.nextDouble() * 128, r.nextDouble() * 128, r.nextInt(4), r.nextInt(2));
        }
        UnitGrid grid = w.grid(128, 128);
        List<Integer> found = new ArrayList<>();

        grid.forEachInRadius(64, 64, 60, UnitFilter.ANY, found::add);

        assertThat(found).hasSizeGreaterThan(150).isSorted().doesNotHaveDuplicates();
    }

    @Test
    void aReusedSlotKeepsItsPlace() {
        SpatialWorld w = new SpatialWorld();
        int first = w.add(10, 10, 0, 0);
        int second = w.add(40, 40, 0, 0);
        w.entities.destroy(first);
        int reused = w.add(41, 41, 0, 0); // slot 0, generation 2: a larger handle than slot 1's
        assertThat(Handle.index(reused)).isZero();
        assertThat(reused).isGreaterThan(second);
        List<Integer> found = new ArrayList<>();

        w.grid(64, 64).forEachInRadius(40, 40, 5, UnitFilter.ANY, found::add);

        assertThat(found).containsExactly(0, 1);
    }

    @Test
    void aQueryCannotStartInsideAnother() {
        SpatialWorld w = new SpatialWorld();
        w.add(5, 5, 0, 0);
        UnitGrid grid = w.grid(16, 16);

        assertThatThrownBy(() -> grid.forEachInRadius(
                        5, 5, 2, UnitFilter.ANY, i -> grid.forEachInRadius(5, 5, 2, UnitFilter.ANY, j -> {})))
                .isInstanceOf(IllegalStateException.class);
        assertThat(grid.countWithin(5, 5, 2, UnitFilter.ANY)).as("usable after").isOne();
    }

    @Test
    void theMatchGridFollowsMovementAndSurvivesARestore() {
        SimHarness h = SimHarness.builder().build();
        int[] units = h.spawn(new FactionSlot(0), new Vec2d(8, 8), 6, 2);
        h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(units), new Vec2d(40, 30)));
        h.runTicks(30);
        MatchState state = KernelAccess.state(h.core());
        List<Integer> moved = new ArrayList<>();
        for (int unit : units) {
            moved.add(state.spatial()
                    .nearest(
                            state.motion().x(Handle.index(unit)),
                            state.motion().z(Handle.index(unit)),
                            UnitFilter.ANY));
        }

        SimHarness restored = SimHarness.restore(h.core().snapshot(), TemplateSet.builtin());
        MatchState copy = KernelAccess.state(restored.core());
        List<Integer> after = new ArrayList<>();
        for (int unit : units) {
            after.add(copy.spatial()
                    .nearest(copy.motion().x(Handle.index(unit)), copy.motion().z(Handle.index(unit)), UnitFilter.ANY));
        }

        List<Integer> indices = new ArrayList<>();
        for (int unit : units) {
            indices.add(Handle.index(unit));
        }
        assertThat(moved).as("each unit is nearest its own point").isEqualTo(indices);
        assertThat(after).isEqualTo(moved);
        assertThat(copy.spatial().units().count()).isEqualTo(units.length);
    }
}
