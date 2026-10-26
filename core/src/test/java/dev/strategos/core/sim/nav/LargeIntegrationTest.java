package dev.strategos.core.sim.nav;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.Footprint;
import dev.strategos.core.content.RawTemplate;
import dev.strategos.core.content.TemplateCompiler;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.Construction;
import dev.strategos.core.sim.move.MotionSystem;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.unitai.UnitAi;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LargeIntegrationTest {
    private static final TemplateId LARGE = new TemplateId("strategos:unit/large_test");
    private static final TemplateId HOUSE = new TemplateId("strategos:building/house");

    @ParameterizedTest
    @ValueSource(ints = {30, 29})
    void committingAdjacentAndDiagonalFootprintsEvictsLargeUnitsOnly(int z) {
        eviction(true, z);
    }

    @ParameterizedTest
    @ValueSource(ints = {30, 29})
    void theWorldPassEvictsLargeUnitsBesideAdjacentAndDiagonalFootprints(int z) {
        eviction(false, z);
    }

    private static void eviction(boolean commit, int z) {
        SimHarness h = harness();
        int large = spawn(h, LARGE, 29.5, z + 0.5);
        int foot = spawn(h, TemplateSet.DEBUG_WALKER, 29.5, 34.5);
        MatchState s = KernelAccess.state(h.core());
        int template = Objects.requireNonNull(s.templates().byId(HOUSE)).netIndex();
        var blueprint = Objects.requireNonNull(s.templates().blueprint(template));
        Footprint at = Footprint.of(blueprint, 30, 30, 0);
        int house = s.buildings().create(template, 0, at, 1, BuildingStore.FOUNDATION, 0);
        if (commit) {
            Construction.commit(s, house);
        } else {
            for (int cell : at.columns(blueprint)) s.blockColumn(cell >>> 16, cell & 0xFFFF);
            Construction.evictBlocked(s);
        }
        int i = Handle.index(large);
        int cx = (int) Math.floor(s.motion().x(i));
        int cz = (int) Math.floor(s.motion().z(i));
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                assertThat(s.footprintAt(cx + dx, cz + dz))
                        .as("large clearance, z=%s", z)
                        .isFalse();
        assertThat(s.paths().repath(i)).isTrue();
        assertThat(s.motion().x(Handle.index(foot))).isEqualTo(29.5);
        assertThat(s.motion().z(Handle.index(foot))).isEqualTo(34.5);
        assertThat(s.footprintAt(30, 34))
                .as("infantry stands beside an obstructed column")
                .isTrue();
    }

    @Test
    void stuckMixedMembersKeepTheirOwnDestinations() {
        assertThat(trade(TemplateSet.DEBUG_WALKER)).isEqualTo(11.5);
    }

    @Test
    void stuckMembersOfTheSameClassStillTradePlaces() {
        assertThat(trade(LARGE)).isEqualTo(20.5);
    }

    private static double trade(TemplateId other) {
        SimHarness h = harness();
        int first = Handle.index(spawn(h, LARGE, 10.5, 10.5));
        int second = Handle.index(spawn(h, other, 14.5, 10.5));
        MatchState s = KernelAccess.state(h.core());
        for (int i : new int[] {first, second}) {
            double target = i == first ? 20.5 : 11.5;
            s.orders().replace(i, OrderKind.MOVE, target, 10.5, 1, i);
            s.ai().set(i, UnitAi.WALKING);
            s.paths().follow(i, PathStore.Waypoints.of(target, 10.5, 16), PathStatus.OK);
            // The next movement pass reaches the first crowded trading interval, without progress.
            s.motion().setProgress(i, target, 10.5, 0, 9);
        }
        new MotionSystem(s).run(s);
        assertThat(s.motion().stuck(first)).isEqualTo(10);
        return s.orders().targetX(second);
    }

    private static SimHarness harness() {
        UnitTemplate large = new UnitTemplate(
                LARGE,
                1,
                "Large test",
                List.of("Cavalry"),
                0.1875,
                45,
                "large",
                "strategos:unit_mount",
                "horse",
                "",
                1.4f,
                2f,
                0.7);
        TemplateCompiler.Result compiled = TemplateCompiler.compile(List.of(
                new RawTemplate(LARGE, "large.json", large.toJson()),
                new RawTemplate(
                        HOUSE,
                        "house.json",
                        Objects.requireNonNull(TemplateSet.builtin().flattened(HOUSE))),
                new RawTemplate(
                        TemplateSet.DEBUG_WALKER,
                        "walker.json",
                        Objects.requireNonNull(TemplateSet.builtin().flattened(TemplateSet.DEBUG_WALKER)))));
        assertThat(compiled.problems()).isEmpty();
        SimHarness h = SimHarness.builder()
                .size(64, 64)
                .yBand(0, 8, 1)
                .templates(compiled.set())
                .build();
        h.publishAll(VoxelFixtures.flat(64, 64, 8, 1));
        h.step();
        return h;
    }

    private static int spawn(SimHarness h, TemplateId template, double x, double z) {
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(template, new FactionSlot(0), new Vec2d(x, z), 1, 0));
        return h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .map(UnitCreated.class::cast)
                .findFirst()
                .orElseThrow()
                .handle();
    }
}
