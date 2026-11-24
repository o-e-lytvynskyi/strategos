package dev.strategos.core.sim.nav;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.AttackMove;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.testkit.FakeAllies;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import org.junit.jupiter.api.Test;

/**
 * PATH-12 acceptance 2 in L1 (M9 spec §2.9, T14): a unit walks through its own gate, and its allies' for as long as
 * they stand, without stopping at it; an enemy never gets through, and a wall with no way round leaves it a partial
 * path to the foot of the palisade.
 */
class GateMovementTest {
    static final TemplateId PALISADE = new TemplateId("strategos:building/palisade");
    static final TemplateId GATE = new TemplateId("strategos:building/palisade_gate");
    static final int SIZE = 96;

    final SimHarness h =
            SimHarness.builder().size(SIZE, SIZE).yBand(0, 31, 1).factions(3).build();
    final MatchState s;

    GateMovementTest() {
        h.publishAll(VoxelFixtures.flat(SIZE, SIZE, 32, 1));
        h.step();
        s = KernelAccess.state(h.core());
        s.setAllies(FakeAllies.twoAgainstRest(3)); // 0 and 1 allied, 2 against them
        for (int z = 0;
                z < SIZE;
                z += 4) { // a wall at x 40 across the whole map, a gate over z 48..51 (columns 49, 50)
            h.submit(PlayerSlot.ADMIN, new SpawnBuilding(z == 48 ? GATE : PALISADE, new FactionSlot(0), 40, z, 1));
        }
        h.runTicks(10);
    }

    UnitView unit(int handle) {
        UnitView v = h.queries().unit(handle);
        assertThat(v).isNotNull();
        return v;
    }

    int soldier(int faction, double x, double z) {
        return h.spawn(new FactionSlot(faction), new Vec2d(x, z), 1, 0)[0];
    }

    void order(int unit, double x, double z) {
        h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(unit), new Vec2d(x, z)));
        h.step();
    }

    /** Walks a unit to (x, z); the most ticks it stood still in a row on the way, and whether it arrived. */
    private record Walk(int stillest, boolean arrived, double endX) {}

    Walk walk(int unit, double x, double z, int ticks) {
        order(unit, x, z);
        double lastX = unit(unit).x();
        double lastZ = unit(unit).z();
        int still = 0;
        int stillest = 0;
        for (int t = 0; t < ticks; t++) {
            h.step();
            UnitView v = unit(unit);
            if (v.state() == UnitState.IDLE) {
                return new Walk(stillest, true, v.x());
            }
            still = v.x() == lastX && v.z() == lastZ ? still + 1 : 0;
            stillest = Math.max(stillest, still);
            lastX = v.x();
            lastZ = v.z();
        }
        return new Walk(stillest, false, unit(unit).x());
    }

    @Test
    void anOwnUnitWalksThroughItsGateWithoutStopping() {
        int u = soldier(0, 30.5, 49.5);
        Walk there = walk(u, 50.5, 49.5, 200);
        assertThat(there.arrived()).isTrue();
        assertThat(there.endX()).isEqualTo(50.5);
        assertThat(there.stillest()).as("never still for a tick at the gate").isZero();
        Walk back = walk(u, 30.5, 49.5, 200);
        assertThat(back.arrived()).as("and back").isTrue();
        assertThat(back.stillest()).isZero();
    }

    @Test
    void anAlliedUnitWalksThroughToo() {
        int u = soldier(1, 30.5, 50.5);
        Walk there = walk(u, 50.5, 49.5, 200);
        assertThat(there.arrived()).isTrue();
        assertThat(there.endX()).isEqualTo(50.5);
        assertThat(there.stillest()).isZero();
    }

    @Test
    void aCrowdOfOwnUnitsAllGetThrough() {
        int[] crowd = h.spawn(new FactionSlot(0), new Vec2d(10.5, 49.5), 8, 2);
        h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(crowd), new Vec2d(52.5, 49.5)));
        h.runTicks(9);
        assertThat(h.queries().pathStats().groupRequests())
                .as("one group search")
                .isEqualTo(1);
        for (int u : crowd) {
            assertThat(h.queries().path(u).status())
                    .as("a path across the gate for every member")
                    .isEqualTo(PathStatus.OK);
            assertThat(h.queries().path(u).waypoints().truncated()).isFalse();
        }
        assertThat(h.runUntil(
                        () -> {
                            for (int u : crowd) {
                                if (unit(u).state() != UnitState.IDLE) {
                                    return false;
                                }
                            }
                            return true;
                        },
                        1_500))
                .isTrue();
        for (int u : crowd) {
            assertThat(unit(u).x()).as("on the far side").isGreaterThan(42);
        }
    }

    @Test
    void anEnemyNeverGetsThroughAndWalksToTheFootOfTheWall() {
        int u = soldier(2, 30.5, 49.5);
        Walk there = walk(u, 50.5, 49.5, 300);
        assertThat(unit(u).x()).as("still on its side of the palisade").isLessThan(40);
        assertThat(there.arrived())
                .as("it ends its move at the foot of the wall")
                .isTrue();
        assertThat(there.endX()).isGreaterThan(37);
    }

    @Test
    void aGateThatFallsWhileAUnitIsInItDoesNotTrapIt() {
        int u = soldier(0, 36.5, 49.5);
        order(u, 50.5, 49.5);
        assertThat(h.runUntil(() -> unit(u).x() > 40.2, 100)).isTrue();
        int gate = 0;
        for (int handle : s.buildings().handles()) {
            if (s.buildings().template(handle) == s.templates().byId(GATE).netIndex()) {
                gate = handle;
            }
        }
        s.destroyBuilding(gate);
        assertThat(h.runUntil(() -> unit(u).state() == UnitState.IDLE, 200)).isTrue();
        assertThat(unit(u).x()).isEqualTo(50.5);
    }

    int segments() {
        int n = 0;
        for (int handle : s.buildings().handles()) {
            n += s.buildings().faction(handle) == 0 ? 1 : 0;
        }
        return n;
    }

    @Test
    void anEnemyAttackMoveWithTheWallInItsWayBreachesItAndGoesOn() {
        int before = segments();
        h.submit(
                PlayerSlot.ADMIN,
                new SpawnUnits(TemplateSet.INFANTRY, new FactionSlot(2), new Vec2d(34.5, 20.5), 3, 1));
        int[] squad = h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .mapToInt(e -> ((UnitCreated) e).handle())
                .toArray();
        h.submit(PlayerSlot.ADMIN, AttackMove.replace(UnitSet.of(squad), new Vec2d(52.5, 20.5)));
        assertThat(h.runUntil(() -> segments() < before, 3_000))
                .as("a segment of the wall falls")
                .isTrue();
        assertThat(h.runUntil(
                        () -> {
                            for (int u : squad) {
                                if (unit(u).x() < 45) {
                                    return false;
                                }
                            }
                            return true;
                        },
                        3_000))
                .as("and the squad goes through the gap on its way")
                .isTrue();
    }

    @Test
    void anEnemyPlainMoveDoesNotAttackTheWall() {
        int before = segments();
        int u = soldier(2, 34.5, 20.5);
        order(u, 52.5, 20.5);
        h.runTicks(600);
        assertThat(segments()).as("a move only walks to the nearest point").isEqualTo(before);
        assertThat(unit(u).x()).isLessThan(40);
    }

    @Test
    void anEnemyAttackMoveWalksThroughAGapWithoutAttackingTheWall() {
        for (int handle : s.buildings().handles()) {
            if (s.footprintOf(handle).anchorZ() == 20) {
                s.destroyBuilding(handle); // a gap at z 20..23
            }
        }
        h.runTicks(5);
        int before = segments();
        h.submit(
                PlayerSlot.ADMIN,
                new SpawnUnits(TemplateSet.INFANTRY, new FactionSlot(2), new Vec2d(34.5, 21.5), 1, 0));
        int unit = h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .mapToInt(e -> ((UnitCreated) e).handle())
                .findFirst()
                .orElseThrow();
        h.submit(PlayerSlot.ADMIN, AttackMove.replace(UnitSet.of(unit), new Vec2d(52.5, 21.5)));
        h.step();
        assertThat(h.runUntil(() -> unit(unit).state() == UnitState.IDLE, 600)).isTrue();
        assertThat(unit(unit).x()).isEqualTo(52.5);
        assertThat(segments()).as("the wall is left alone").isEqualTo(before);
    }
}
