package dev.strategos.core.sim.nav;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.PageRef;
import dev.strategos.core.sim.TerrainPage;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.journal.Journal;
import dev.strategos.core.sim.journal.JournalExport;
import dev.strategos.core.sim.journal.JournalFormat;
import dev.strategos.core.sim.journal.ReplayDriver;
import dev.strategos.core.sim.journal.ReplayResult;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import dev.strategos.core.testkit.VoxelWorld;
import org.junit.jupiter.api.Test;

/**
 * PATH-03.2 and .3 (spec §5.7), PATH-15.1's core half: a short move is decided at once; a longer one moves on the tick
 * its order applies and follows its result from {@code t + k}; a superseded result never steers; requests in flight
 * travel with a snapshot; changed terrain on a route, a truncated path and a dead end each lead where they should.
 */
class PathServiceTest {
    private static final FactionSlot F0 = new FactionSlot(0);
    private static final double SPEED = 0.25;

    @Test
    void aShortMoveIsDecidedAtOnce() {
        SimHarness h = harness(VoxelFixtures.flat(64, 64, 16, 1));
        int unit = h.spawn(F0, new Vec2d(10.5, 10.5), 1, 0)[0];

        order(h, unit, 20.5, 14.5);

        PathStore.UnitPath path = path(h, unit);
        assertThat(path.request()).as("no request id").isZero();
        assertThat(path.waypoints().count()).isOne();
        assertThat(path.waypoints().x(0)).isEqualTo(20.5);
        assertThat(path.waypoints().z(0)).isEqualTo(14.5);
        assertThat(h.queries().pathStats().decided()).isOne();
        assertThat(unit(h, unit).x()).as("moved on the order's tick").isGreaterThan(10.5);
    }

    @Test
    void movesAtOnceThenFollowsAtTPlusK() {
        SimHarness h = harness(wallWithGapAtTheSouth());
        int unit = h.spawn(F0, new Vec2d(10.5, 20.5), 1, 0)[0];

        order(h, unit, 30.5, 20.5); // 20 blocks, but the wall at x 20 hides the target
        long t = h.tick();

        assertThat(path(h, unit).request()).as("a request in flight at t").isEqualTo(1);
        assertThat(unit(h, unit).x()).as("provisional steering at t").isEqualTo(10.5 + SPEED);
        assertThat(unit(h, unit).z()).isEqualTo(20.5);
        h.step();
        assertThat(path(h, unit).request()).as("still in flight at t + 1").isEqualTo(1);
        assertThat(unit(h, unit).x()).isEqualTo(10.5 + 2 * SPEED);
        h.step();
        PathStore.UnitPath delivered = path(h, unit);
        assertThat(h.tick()).isEqualTo(t + 2);
        assertThat(delivered.request()).as("delivered at t + k").isZero();
        assertThat(delivered.status()).isEqualTo(PathStatus.OK);
        assertThat(delivered.waypoints().count()).isGreaterThanOrEqualTo(2);
        assertThat(delivered.waypoints().z(0)).as("toward the gap").isGreaterThan(40);
        assertThat(unit(h, unit).z()).as("following the path at t + k").isGreaterThan(20.5);

        assertThat(h.runUntil(() -> unit(h, unit).state() == UnitState.IDLE, 400))
                .isTrue();
        assertThat(unit(h, unit).x()).isEqualTo(30.5);
        assertThat(unit(h, unit).z()).isEqualTo(20.5);
    }

    /** PATH-10's panel: the latency percentile counts the ticks from a request to its delivery, k in STRICT. */
    @Test
    void latencyPercentileIsTheDeliveryDelay() {
        SimHarness h = harness(wallWithGapAtTheSouth());
        int unit = h.spawn(F0, new Vec2d(10.5, 20.5), 1, 0)[0];
        assertThat(h.queries().pathStats().latencyP95()).as("no result yet").isZero();

        order(h, unit, 30.5, 20.5);
        h.step();
        h.step();

        assertThat(path(h, unit).request()).as("delivered").isZero();
        assertThat(h.queries().pathStats().latencyP95()).isEqualTo(2);
    }

    @Test
    void supersededResultDropped() {
        SimHarness h = harness(wallWithGapAtTheSouth());
        int unit = h.spawn(F0, new Vec2d(10.5, 20.5), 1, 0)[0];

        order(h, unit, 30.5, 20.5); // request 1, due two ticks later
        order(h, unit, 40.5, 60.5); // a tick later: request 2
        assertThat(path(h, unit).request()).isEqualTo(2);
        h.step(); // request 1 falls due: the unit waits for 2
        assertThat(h.queries().pathStats().superseded()).isOne();
        assertThat(path(h, unit).request()).isEqualTo(2);
        h.step();

        PathStore.UnitPath second = path(h, unit);
        assertThat(second.request()).isZero();
        assertThat(second.waypoints().x(second.waypoints().count() - 1)).isEqualTo(40.5);
        assertThat(second.waypoints().z(second.waypoints().count() - 1)).isEqualTo(60.5);
    }

    @Test
    void aResultDueAsANewOrderAppliesNeverSteers() {
        SimHarness h = harness(wallWithGapAtTheSouth());
        int unit = h.spawn(F0, new Vec2d(10.5, 20.5), 1, 0)[0];

        order(h, unit, 30.5, 20.5); // request 1 at t, due at t + 2
        h.step();
        double x = unit(h, unit).x();
        double z = unit(h, unit).z();
        order(h, unit, 10.5, 2.5); // applies at t + 2, after request 1 was delivered in INGEST

        PathStore.UnitPath now = path(h, unit);
        PathStore.Waypoints w = now.waypoints();
        assertThat(w.count() > 0 && w.x(w.count() - 1) == 30.5)
                .as("the first path is gone")
                .isFalse();
        assertThat(unit(h, unit).z())
                .as("this tick's step heads for the new target")
                .isLessThan(z);
        assertThat(unit(h, unit).x()).isLessThanOrEqualTo(x);
    }

    @Test
    void requestsInFlightTravelWithTheSnapshot() {
        SimHarness h = harness(wallWithGapAtTheSouth());
        int[] units = h.spawn(F0, new Vec2d(10.5, 20.5), 5, 3.0);
        h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(units), new Vec2d(30.5, 20.5)));
        h.step();

        MatchSnapshot snapshot = h.core().snapshot();
        SimHarness restored = SimHarness.restore(
                snapshot, TemplateSet.builtin(), h.core().journal().segments().get(0)::slice);

        assertThat(snapshot.paths().deliveries()).as("quiesced results").hasSize(5);
        assertThat(snapshot.pages()).extracting(PageRef::slice).doesNotContainNull();
        assertThat(restored.stateHash()).isEqualTo(h.stateHash());
        for (int i = 0; i < 200; i++) {
            h.step();
            restored.step();
            assertThat(restored.stateHash()).as("tick %d", h.tick()).isEqualTo(h.stateHash());
        }
    }

    @Test
    void aJournalWithPathsReplaysFromAnySegment() {
        SimHarness h = harness(serpentine());
        int unit = h.spawn(F0, new Vec2d(0.5, 16.5), 1, 0)[0];
        order(h, unit, 159.5, 16.5);
        h.runTicks(3_000); // re-asks in flight across segment starts

        Journal journal = JournalFormat.decode(
                        JournalFormat.encode(new JournalExport(h.core().journal(), h.tick(), h.stateHash())))
                .journal();

        assertThat(journal.segments()).hasSizeGreaterThanOrEqualTo(3);
        for (int segment = 0; segment < journal.segments().size(); segment++) {
            Journal rest = new Journal(
                    journal.checkpointEvery(),
                    journal.segments().subList(segment, journal.segments().size()));
            ReplayResult replay = ReplayDriver.replay(rest, TemplateSet.builtin());
            assertThat(replay.matches())
                    .as("from segment %d: %s", segment, replay)
                    .isTrue();
            assertThat(replay.finalTick()).as("segment %d's last tick", segment).isEqualTo(h.tick());
            assertThat(replay.finalHash())
                    .as("from segment %d at tick %d", segment, h.tick())
                    .isEqualTo(h.stateHash());
        }
    }

    @Test
    void aChangedPageOnTheRouteMakesTheUnitAskAgain() {
        VoxelWorld world = VoxelFixtures.flat(64, 64, 16, 1);
        SimHarness h = harness(world);
        int unit = h.spawn(F0, new Vec2d(5.5, 30.5), 1, 0)[0];
        order(h, unit, 60.5, 30.5);
        long ordered = h.tick();
        h.runTicks(4);
        assertThat(path(h, unit).waypoints().count()).as("a straight path").isOne();

        world.fill(40, 1, 0, 40, 4, 55, VoxelFixtures.SOLID); // a wall across the route, open at z 56..63
        for (int pz = 0; pz < 4; pz++) {
            PageCoord coord = new PageCoord(2, pz);
            h.publish(TerrainPage.build(coord, world.slice(coord), new PageBuilder(MobilityClass.INFANTRY)));
        }
        h.step();
        assertThat(path(h, unit).repath())
                .as("the route crosses a changed page")
                .isTrue();
        h.runUntil(() -> path(h, unit).request() != 0, 40);
        assertThat(h.tick()).as("at most once in 20 ticks").isEqualTo(ordered + PathService.REPATH_EVERY);
        h.runTicks(2);

        PathStore.Waypoints around = path(h, unit).waypoints();
        boolean southOfTheWall = false;
        for (int i = 0; i < around.count(); i++) {
            southOfTheWall |= around.z(i) >= 55;
        }
        assertThat(southOfTheWall).as("the new path goes round the wall's end").isTrue();
        assertThat(h.runUntil(() -> unit(h, unit).state() == UnitState.IDLE, 2_000))
                .isTrue();
        assertThat(unit(h, unit).x()).isEqualTo(60.5);
    }

    @Test
    void aTruncatedPathAsksAgainAtItsFortyEighthWaypoint() {
        SimHarness h = harness(serpentine());
        int unit = h.spawn(F0, new Vec2d(0.5, 16.5), 1, 0)[0];

        order(h, unit, 159.5, 16.5);
        h.runTicks(2);
        PathStore.UnitPath first = path(h, unit);
        assertThat(first.waypoints().truncated()).isTrue();
        assertThat(first.waypoints().count()).isEqualTo(PathStore.Waypoints.MAX);
        assertThat(h.runUntil(() -> path(h, unit).request() != 0, 20_000)).isTrue();
        assertThat(path(h, unit).cursor()).isEqualTo(PathService.REASK_AT);

        assertThat(h.runUntil(() -> unit(h, unit).state() == UnitState.IDLE, 60_000))
                .isTrue();
        assertThat(unit(h, unit).x()).isEqualTo(159.5);
        assertThat(unit(h, unit).z()).isEqualTo(16.5);
        assertThat(h.queries().pathStats().requests()).isGreaterThan(2);
    }

    @Test
    void anOrderOutOfAClosedRoomEndsAtItsNearestPoint() {
        VoxelWorld w = VoxelFixtures.flat(32, 32, 16, 1);
        w.fill(4, 1, 4, 12, 6, 4, VoxelFixtures.SOLID);
        w.fill(4, 1, 12, 12, 6, 12, VoxelFixtures.SOLID);
        w.fill(4, 1, 4, 4, 6, 12, VoxelFixtures.SOLID);
        w.fill(12, 1, 4, 12, 6, 12, VoxelFixtures.SOLID);
        SimHarness h = harness(w);
        int unit = h.spawn(F0, new Vec2d(6.5, 8.5), 1, 0)[0];

        order(h, unit, 25.5, 8.5);
        h.runTicks(2);

        PathStore.UnitPath path = path(h, unit);
        assertThat(path.status()).as("PATH-04: the goal moved into the room").isEqualTo(PathStatus.OK);
        assertThat(path.waypoints().x(path.waypoints().count() - 1)).isEqualTo(11.5);
        assertThat(h.queries().pathStats().expansions())
                .as("under 1 % of the cap, no flood")
                .isLessThan(200);
        assertThat(h.runUntil(() -> unit(h, unit).state() == UnitState.IDLE, 200))
                .isTrue();
        assertThat(unit(h, unit).x()).isEqualTo(11.5);
        assertThat(unit(h, unit).z()).isEqualTo(8.5);
        assertThat(unit(h, unit).target()).as("the move is over").isNull();
    }

    @Test
    void withoutTerrainUnitsWalkM0sStraightLine() {
        SimHarness h = SimHarness.builder().size(64, 64).build();
        int unit = h.spawn(F0, new Vec2d(5.5, 5.5), 1, 0)[0];

        order(h, unit, 45.5, 5.5);

        assertThat(path(h, unit)).isEqualTo(PathStore.UnitPath.NONE);
        assertThat(unit(h, unit).x()).isEqualTo(5.5 + SPEED);
        assertThat(h.runUntil(() -> unit(h, unit).state() == UnitState.IDLE, 200))
                .isTrue();
        assertThat(h.tick())
                .as("40 blocks at 0.25 a tick from the order's tick")
                .isEqualTo(2 + 160 - 1);
        assertThat(h.queries().pathStats().requests()).isZero();
    }

    /** A harness the size of {@code world} whose pages are published on the first step. */
    private static SimHarness harness(VoxelWorld world) {
        SimHarness h = SimHarness.builder().size(world.sizeX(), world.sizeZ()).build();
        h.publishAll(world);
        return h;
    }

    /** Flat 64 x 64 with a wall at x 20 from z 0 to 40: the way east goes round its south end. */
    private static VoxelWorld wallWithGapAtTheSouth() {
        VoxelWorld w = VoxelFixtures.flat(64, 64, 16, 1);
        w.fill(20, 1, 0, 20, 4, 40, VoxelFixtures.SOLID);
        return w;
    }

    /** 160 x 32 of one-wide corridors joined at alternate ends: a path of thousands of nodes and ~150 corners. */
    static VoxelWorld serpentine() {
        VoxelWorld w = VoxelFixtures.flat(160, 32, 16, 1);
        for (int x = 2; x < 158; x += 2) {
            boolean gapNorth = (x / 2) % 2 == 0;
            w.fill(x, 1, gapNorth ? 2 : 0, x, 4, gapNorth ? 31 : 29, VoxelFixtures.SOLID);
        }
        return w;
    }

    /** Orders {@code unit} to (x, z) and steps the tick it applies. */
    private static void order(SimHarness h, int unit, double x, double z) {
        h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(unit), new Vec2d(x, z)));
        h.step();
    }

    private static PathStore.UnitPath path(SimHarness h, int unit) {
        PathStore.UnitPath path = h.queries().path(unit);
        assertThat(path).isNotNull();
        return path;
    }

    private static UnitView unit(SimHarness h, int unit) {
        UnitView view = h.queries().unit(unit);
        assertThat(view).isNotNull();
        return view;
    }
}
