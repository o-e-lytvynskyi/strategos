package dev.strategos.core.testkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.MatchId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.MatchBounds;
import dev.strategos.core.sim.MatchConfig;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.MatchRules;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.MotionStore;
import dev.strategos.core.sim.PageRef;
import dev.strategos.core.sim.UnitRecord;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.nav.MobilityClass;
import dev.strategos.core.sim.nav.PathService;
import dev.strategos.core.sim.nav.PathStore;
import dev.strategos.core.sim.nav.SurfaceGrid;
import dev.strategos.core.sim.order.OrderQueues;
import dev.strategos.core.sim.stats.StatBreakdown;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** QA-02.3: each invariant catches its seeded defect, fed through a hand-built {@link MatchQueries}. */
class InvariantsTest {
    private static final Vec2d TARGET = new Vec2d(20.5, 8.5);

    private static UnitView unit(int index, double x, double y, double z, UnitState state, @Nullable Vec2d target) {
        return new UnitView(
                Handle.of(index, 1), TemplateSet.DEBUG_WALKER, 0, new FactionSlot(0), x, y, z, 0f, state, target);
    }

    @Test
    void aSoundMatchBreaksNothing() {
        FakeQueries q = new FakeQueries(4)
                .with(unit(0, 4.5, 1.0, 8.5, UnitState.IDLE, null))
                .with(unit(1, 0.0, 0.0, 31.999, UnitState.WALKING, TARGET));

        assertThat(new Invariants().check(q, 1)).isEmpty();
    }

    @Test
    void catchesAUnitOutsideTheMatchSpace() {
        Invariants invariants = new Invariants();
        for (UnitView outside : List.of(
                unit(0, 32.0, 1.0, 8.5, UnitState.IDLE, null),
                unit(0, 4.5, 1.0, -0.001, UnitState.IDLE, null),
                unit(0, 4.5, 16.5, 8.5, UnitState.IDLE, null),
                unit(0, 4.5, -1.0, 8.5, UnitState.IDLE, null),
                unit(0, Double.NaN, 1.0, 8.5, UnitState.IDLE, null))) {
            assertThat(invariants.check(new FakeQueries(4).with(outside), 1))
                    .as("%s", outside)
                    .singleElement()
                    .asString()
                    .startsWith("unit #0.1 at (")
                    .contains("outside the match space 32 x 32, y 0..16");
        }
    }

    @Test
    void catchesAWalkingUnitWithoutATarget() {
        FakeQueries q = new FakeQueries(4).with(unit(3, 4.5, 1.0, 8.5, UnitState.WALKING, null));

        assertThat(new Invariants().check(q, 1)).containsExactly("unit #3.1 is WALKING without a target");
    }

    @Test
    void catchesAWalkingUnitWithoutProgressForMoreThan400Ticks() {
        Invariants invariants = new Invariants();
        FakeQueries q = new FakeQueries(4).with(unit(2, 4.5, 1.0, 8.5, UnitState.WALKING, TARGET));

        for (long tick = 0; tick <= Invariants.STUCK_TICKS; tick++) {
            assertThat(invariants.check(q, tick)).as("tick %d", tick).isEmpty();
        }
        assertThat(invariants.check(q, Invariants.STUCK_TICKS + 1))
                .containsExactly("unit #2.1 is WALKING without progress toward (20.5, 8.5) for 401 ticks");
    }

    @Test
    void progressOrANewTargetRestartsTheStuckClock() {
        Invariants invariants = new Invariants();
        for (long tick = 0; tick <= 1_000; tick++) {
            double x = 4.5 + Math.floorDiv(tick, 300) * 0.25; // a step closer every 300 ticks
            Vec2d target = tick < 900 ? TARGET : new Vec2d(30.5, 8.5);
            FakeQueries q = new FakeQueries(4).with(unit(2, x, 1.0, 8.5, UnitState.WALKING, target));
            assertThat(invariants.check(q, tick)).as("tick %d", tick).isEmpty();
        }
    }

    @Test
    void aReusedIndexIsANewWalk() {
        Invariants invariants = new Invariants();
        UnitView first = unit(2, 4.5, 1.0, 8.5, UnitState.WALKING, TARGET);
        UnitView reused = new UnitView(
                Handle.of(2, 2),
                TemplateSet.DEBUG_WALKER,
                0,
                new FactionSlot(0),
                4.5,
                1.0,
                8.5,
                0f,
                UnitState.WALKING,
                TARGET);

        assertThat(invariants.check(new FakeQueries(4).with(first), 0)).isEmpty();
        assertThat(invariants.check(new FakeQueries(4).with(reused), 300)).isEmpty();
        assertThat(invariants.check(new FakeQueries(4).with(reused), 700)).isEmpty();
    }

    @Test
    void catchesAliveUnitsAboveTheCap() {
        FakeQueries q = new FakeQueries(2);
        for (int i = 0; i < 3; i++) {
            q.with(unit(i, 4.5 + i, 1.0, 8.5, UnitState.IDLE, null));
        }

        assertThat(new Invariants().check(q, 1)).containsExactly("alive units 3 above the unit cap 2");
    }

    @Test
    void aHarnessWhoseStepBreaksAnInvariantThrowsNamingSeedAndTick() {
        SimHarness live = SimHarness.builder().build();
        int unit = live.spawn(new FactionSlot(0), new Vec2d(8.5, 8.5), 1, 0)[0];
        MatchSnapshot snapshot = live.core().snapshot();
        UnitRecord sound = snapshot.units().get(0);
        UnitRecord outside = new UnitRecord(
                unit, sound.template(), sound.faction(), 40.5, sound.y(), sound.z(), 0f, sound.ai(), null);
        SimHarness broken = SimHarness.restore(
                new MatchSnapshot(
                        snapshot.config(),
                        snapshot.tick(),
                        snapshot.templateSetHash(),
                        snapshot.slots(),
                        List.of(outside),
                        snapshot.pendingCommands()),
                TemplateSet.builtin());

        assertThatThrownBy(broken::step)
                .isInstanceOf(InvariantViolation.class)
                .isInstanceOf(AssertionError.class)
                .hasMessageStartingWith("seed=" + SimHarness.DEFAULT_SEED + " tick=" + (snapshot.tick() + 1) + ": ")
                .hasMessageContaining("unit #0.1 at (40.5, 1.0, 8.5) outside the match space");
        assertThat(broken.invariantViolations()).hasSize(1);
        assertThatThrownBy(broken::assertInvariants).isInstanceOf(InvariantViolation.class);
    }

    /** A 32 x 32 match, y 0..16, holding exactly the units it is given; {@code cap} is its unit cap. */
    private static final class FakeQueries implements MatchQueries {
        private final MatchConfig config;
        private final List<UnitView> units = new ArrayList<>();

        FakeQueries(int cap) {
            config = new MatchConfig(
                    new MatchId(1),
                    SimHarness.DEFAULT_SEED,
                    new MatchBounds(32, 32, 0, 16, 1.0),
                    2,
                    new MatchRules(MatchRules.DEFAULTS.commandsPerTick(), cap));
        }

        FakeQueries with(UnitView unit) {
            units.add(unit);
            return this;
        }

        @Override
        public long tick() {
            return 0;
        }

        @Override
        public MatchConfig config() {
            return config;
        }

        @Override
        public TemplateSet templates() {
            return TemplateSet.builtin();
        }

        @Override
        public int unitCount() {
            return units.size();
        }

        @Override
        public boolean isAlive(int handle) {
            return unit(handle) != null;
        }

        @Override
        public @Nullable UnitView unit(int handle) {
            return units.stream().filter(u -> u.handle() == handle).findFirst().orElse(null);
        }

        @Override
        public int[] unitHandles() {
            int[] handles = units.stream().mapToInt(UnitView::handle).toArray();
            Arrays.sort(handles);
            return handles;
        }

        @Override
        public int[] unitHandlesOf(FactionSlot faction) {
            return units.stream()
                    .filter(u -> u.faction().equals(faction))
                    .mapToInt(UnitView::handle)
                    .sorted()
                    .toArray();
        }

        @Override
        public SurfaceGrid surfaces() {
            return SurfaceGrid.empty(config.bounds().sizeX(), config.bounds().sizeZ(), MobilityClass.INFANTRY);
        }

        @Override
        public List<PageRef> pageRefs() {
            return List.of();
        }

        @Override
        public PathStore.@Nullable UnitPath path(int handle) {
            return null;
        }

        @Override
        public List<OrderQueues.Order> orders(int handle) {
            return List.of();
        }

        @Override
        public MotionStore.@Nullable Extra motion(int handle) {
            return null;
        }

        @Override
        public @Nullable StatBreakdown stats(int handle, String path) {
            return null;
        }

        @Override
        public PathService.Stats pathStats() {
            return new PathService.Stats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
    }
}
