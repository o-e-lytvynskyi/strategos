package dev.strategos.core.sim.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.replicate.BuildingMemory;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScoutKnowledgeTest {
    private static final FactionSlot A = new FactionSlot(0);

    private static UnitView unit(int index, int faction, Vec2d order) {
        return new UnitView(
                Handle.of(index, 1),
                new TemplateId("strategos:worker"),
                0,
                new FactionSlot(faction),
                8,
                64,
                8,
                0,
                UnitState.IDLE,
                order);
    }

    private static BuildingMemory memory(long tick) {
        return BuildingMemory.empty()
                .observe(new BuildingMemory.Seen(
                        Handle.of(BuildingStore.BASE, 1), new TemplateId("custom:house"), 1, 24, 64, 24, 0, 100, tick));
    }

    @Test
    void targetsOnlyVisibleEnemiesAndNeverPromotesRememberedBuildings() {
        ScoutKnowledge knowledge = ScoutKnowledge.capture(
                20,
                A,
                List.of(
                        unit(1, 0, new Vec2d(1, 1)), unit(2, 1, new Vec2d(2, 2)),
                        unit(3, 1, new Vec2d(3, 3)), unit(4, 2, new Vec2d(4, 4))),
                (viewer, candidate) -> Handle.index(candidate.handle()) != 3,
                faction -> faction.value() == 1,
                memory(10),
                List.of(new Vec2d(24, 24)));
        assertThat(knowledge.targets(20))
                .extracting(ScoutKnowledge.Target::handle)
                .containsExactly(Handle.of(2, 1));
        assertThat(knowledge.buildings()).isEqualTo(memory(10));
        assertThat(knowledge.unscoutedStarts(point -> false)).containsExactly(new Vec2d(24, 24));
        assertThat(knowledge.observedAt()).isEqualTo(20);
        assertThat(knowledge.targets(21))
                .as("a later think must collect visibility again")
                .isEmpty();
    }

    @Test
    void privateEnemyIntentionsDoNotChangeObservedKnowledge() {
        ScoutKnowledge first = ScoutKnowledge.capture(
                20,
                A,
                List.of(unit(2, 1, new Vec2d(100, 100))),
                (viewer, candidate) -> true,
                faction -> true,
                memory(10),
                List.of());
        ScoutKnowledge changedPrivateOrder = ScoutKnowledge.capture(
                20,
                A,
                List.of(unit(2, 1, new Vec2d(200, 200))),
                (viewer, candidate) -> true,
                faction -> true,
                memory(10),
                List.of());
        assertThat(first.targets(20)).isEqualTo(changedPrivateOrder.targets(20));
        assertThat(first.targets(20))
                .extracting(ScoutKnowledge.Target::position)
                .containsExactly(new Vec2d(8, 8));
        ScoutKnowledge hidden = ScoutKnowledge.capture(
                21,
                A,
                List.of(unit(2, 1, new Vec2d(300, 300))),
                (viewer, candidate) -> false,
                faction -> true,
                memory(10),
                List.of());
        assertThat(hidden.targets(21)).isEmpty();
        assertThat(hidden.buildings()).isEqualTo(first.buildings());
    }

    @Test
    void copiesInputsAndPreservesPublicMapOrder() {
        List<UnitView> units = new ArrayList<>(List.of(unit(3, 1, new Vec2d(3, 3)), unit(2, 1, new Vec2d(2, 2))));
        List<Vec2d> starts = new ArrayList<>(List.of(new Vec2d(32, 16), new Vec2d(8, 24)));
        ScoutKnowledge knowledge = ScoutKnowledge.capture(
                20, A, units, (viewer, candidate) -> true, faction -> true, BuildingMemory.empty(), starts);
        units.clear();
        starts.clear();
        assertThat(knowledge.targets(20))
                .extracting(ScoutKnowledge.Target::handle)
                .containsExactly(Handle.of(2, 1), Handle.of(3, 1));
        assertThat(knowledge.unscoutedStarts(point -> point.x() == 32)).containsExactly(new Vec2d(8, 24));
        assertThat(knowledge.unscoutedStarts(point -> false)).containsExactly(new Vec2d(32, 16), new Vec2d(8, 24));
        assertThatThrownBy(() -> knowledge.targets(20).clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> knowledge.unscoutedStarts(point -> false).clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsFutureMemoryConflictingHandlesAndNonfiniteStarts() {
        assertThatThrownBy(() ->
                        ScoutKnowledge.capture(5, A, List.of(), (v, u) -> true, faction -> true, memory(10), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScoutKnowledge.capture(
                        -1, A, List.of(), (v, u) -> true, faction -> true, BuildingMemory.empty(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScoutKnowledge.capture(
                        20,
                        A,
                        List.of(unit(2, 1, new Vec2d(2, 2)), unit(2, 1, new Vec2d(3, 3))),
                        (v, u) -> true,
                        faction -> true,
                        BuildingMemory.empty(),
                        List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScoutKnowledge.capture(
                        20,
                        A,
                        List.of(),
                        (v, u) -> true,
                        faction -> true,
                        BuildingMemory.empty(),
                        List.of(new Vec2d(Double.NaN, 8))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
