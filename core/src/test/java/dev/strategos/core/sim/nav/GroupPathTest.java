package dev.strategos.core.sim.nav;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.google.gson.JsonElement;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CorePorts;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchRules;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.order.OrderQueues;
import dev.strategos.core.sim.persist.MatchSnapshotCodec;
import dev.strategos.core.sim.persist.Migrations;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import dev.strategos.core.testkit.VoxelWorld;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * PATH-09.1 (spec §5.11): an order to 150 units makes at most a tenth of the requests one by one would, and all 150
 * settle; members follow the group's path in lanes at their places' offsets, keeping to the path where a lane would
 * run into a wall; a member cut off from the path joins it by a search, or, when even that fails, asks for its own
 * path; groups under eight plan one by one; a group's result in flight survives a save.
 */
class GroupPathTest {
    private static final FactionSlot F0 = new FactionSlot(0);

    @Test
    void anOrderToAHundredAndFiftyMakesAtMostATenthOfTheRequests() {
        SimHarness grouped = harness(VoxelFixtures.flat(160, 96, 16, 1), MatchRules.DEFAULTS);
        int[] units = grouped.spawn(F0, new Vec2d(20, 48), 150, 12);
        grouped.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(units), new Vec2d(130.5, 48.5)));
        grouped.step();
        SimHarness alone = harness(VoxelFixtures.flat(160, 96, 16, 1), MatchRules.DEFAULTS);
        int[] single = alone.spawn(F0, new Vec2d(20, 48), 150, 12);
        for (int unit : single) {
            alone.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(unit), new Vec2d(130.5, 48.5)));
        }

        alone.runTicks(10);
        grouped.runTicks(9);
        PathService.Stats planned = grouped.queries().pathStats(); // the order's planning, before any stuck repath
        boolean settled = grouped.runUntil(() -> allIdle(grouped, units), 1_500);

        assertThat(alone.queries().pathStats().requests()).isEqualTo(150);
        assertThat(planned.groupRequests()).isEqualTo(1);
        assertThat(planned.requests()).as("requests for the group of 150").isLessThanOrEqualTo(15);
        assertThat(planned.ok()).as("every member has its path").isEqualTo(150);
        assertThat(settled).as("all 150 settle").isTrue();
    }

    @Test
    void membersWalkLanesAtTheirPlacesOffsets() {
        SimHarness h = harness(VoxelFixtures.flat(64, 64, 16, 1), MatchRules.DEFAULTS);
        h.step();
        SurfaceGrid grid = h.queries().surfaces();
        // a group path east from (10.5, 10.5) to a corner at (40.5, 10.5), then north to the goal at (40.5, 40.5)
        PathRequest request = new PathRequest(1, 0, 0, node(grid, 10, 10), 10.5, 10.5, 40.5, 40.5, 20_000, 1, false);
        PathStore.Waypoints corner = new PathStore.Waypoints(
                new double[] {40.5, 40.5}, new double[] {10.5, 40.5}, new int[] {16, 16}, false);
        double leftX = -Math.sqrt(0.5); // left of the facing, (30, 30) from start to goal
        double leftZ = Math.sqrt(0.5);
        List<GroupPaths.Member> members = new ArrayList<>();
        double[] lateral = {-3, 0, 3};
        for (int k = 0; k < lateral.length; k++) {
            members.add(new GroupPaths.Member(
                    k + 1, node(grid, 12, 10), 12.5, 10.5, 40.5 + leftX * lateral[k], 40.5 + leftZ * lateral[k]));
        }

        PathResult result = GroupPaths.members(
                grid, request, new PathResult(1, PathStatus.OK, corner, 0), members, 2_000, 10, new GridAStar());

        for (int k = 0; k < lateral.length; k++) {
            MemberPath m = result.members().get(k);
            assertThat(m.join()).isEqualTo(MemberPath.LINE);
            PathStore.Waypoints w = m.waypoints();
            assertThat(w.count())
                    .as("the join, the corner in its lane, its place")
                    .isEqualTo(3);
            assertThat(w.x(1)).isEqualTo(40.5);
            assertThat(w.z(1))
                    .as("the corner %s to the left of the eastward leg", lateral[k])
                    .isCloseTo(10.5 + lateral[k], within(1e-9));
            assertThat(w.x(2)).isEqualTo(members.get(k).placeX());
            assertThat(w.z(2)).isEqualTo(members.get(k).placeZ());
            assertThat(w.truncated()).isFalse();
        }
    }

    @Test
    void aLaneIntoAWallKeepsToThePath() {
        VoxelWorld w = VoxelFixtures.flat(64, 64, 16, 1);
        w.fill(30, 1, 5, 50, 3, 9, VoxelFixtures.SOLID); // south of the corner: no room for a lane to the right
        SimHarness h = harness(w, MatchRules.DEFAULTS);
        h.step();
        SurfaceGrid grid = h.queries().surfaces();
        PathRequest request = new PathRequest(1, 0, 0, node(grid, 10, 10), 10.5, 10.5, 40.5, 40.5, 20_000, 1, false);
        PathStore.Waypoints corner = new PathStore.Waypoints(
                new double[] {40.5, 40.5}, new double[] {10.5, 40.5}, new int[] {16, 16}, false);
        GroupPaths.Member right = new GroupPaths.Member(
                1, node(grid, 12, 10), 12.5, 10.5, 40.5 + 3 * Math.sqrt(0.5), 40.5 - 3 * Math.sqrt(0.5));

        PathResult result = GroupPaths.members(
                grid, request, new PathResult(1, PathStatus.OK, corner, 0), List.of(right), 2_000, 10, new GridAStar());

        PathStore.Waypoints path = result.members().get(0).waypoints();
        assertThat(path.z(1))
                .as("the lane would end in the wall: the path's own corner")
                .isEqualTo(10.5);
    }

    @Test
    void aMemberCutOffFromThePathJoinsByASearch() {
        SimHarness h = harness(pocket(), MatchRules.DEFAULTS);
        int[] units = members(h);
        h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(units), new Vec2d(100.5, 32.5)));
        h.step();

        assertThat(h.runUntil(() -> allIdle(h, units), 1_500)).isTrue();
        assertThat(h.queries().pathStats().joins()).isGreaterThanOrEqualTo(1);
        assertThat(h.queries().pathStats().unjoined()).isZero();
        assertThat(h.queries().pathStats().groupRequests()).isEqualTo(1);
    }

    @Test
    void aMemberThatCannotJoinAsksForItsOwnPath() {
        SimHarness h = harness(pocket(), joinCap(20));
        int[] units = members(h);
        h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(units), new Vec2d(100.5, 32.5)));
        h.runTicks(6);
        PathService.Stats planned = h.queries().pathStats();

        assertThat(planned.unjoined()).isEqualTo(1);
        assertThat(planned.requests())
                .as("the group's request and one of its own for the member that could not join")
                .isEqualTo(2);
        assertThat(h.runUntil(() -> allIdle(h, units), 1_500)).isTrue();
    }

    @Test
    void groupsUnderEightPlanOneByOne() {
        SimHarness h = harness(VoxelFixtures.flat(128, 64, 16, 1), MatchRules.DEFAULTS);
        int[] units = h.spawn(F0, new Vec2d(20, 32), 7, 3);
        h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(units), new Vec2d(100.5, 32.5)));
        h.runTicks(4);

        assertThat(h.queries().pathStats().groupRequests()).isZero();
        assertThat(h.queries().pathStats().requests()).isEqualTo(7);
    }

    @Test
    void aGroupsResultInFlightSurvivesASave() {
        SimHarness h = harness(pocket(), MatchRules.DEFAULTS);
        int[] units = members(h);
        h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(units), new Vec2d(100.5, 32.5)));
        h.step();
        MatchSnapshot snapshot = h.core().snapshot(); // joins the group's job: its members' paths are in the save

        JsonElement json = MatchSnapshotCodec.encode(JsonOps.INSTANCE, snapshot);
        MatchSnapshot decoded = MatchSnapshotCodec.decode(
                        new Dynamic<>(JsonOps.INSTANCE, json), Migrations.production())
                .getOrThrow();

        assertThat(snapshot.paths().deliveries()).singleElement().satisfies(d -> {
            assertThat(d.request().groupId()).isPositive();
            assertThat(d.result().members()).hasSize(units.length);
        });
        assertThat(decoded).isEqualTo(snapshot);
        MatchCore restored = MatchCore.restore(decoded, TemplateSet.builtin(), CorePorts.of(() -> 0L));
        assertThat(restored.stateHash())
                .isEqualTo(MatchCore.restore(snapshot, TemplateSet.builtin(), CorePorts.of(() -> 0L))
                        .stateHash());
    }

    /**
     * Flat ground with a pen two columns deep south of the group's way, walled but for its east end: a unit in it sees
     * none of the group's path.
     */
    private static VoxelWorld pocket() {
        VoxelWorld w = VoxelFixtures.flat(128, 64, 16, 1);
        w.fill(10, 1, 14, 40, 3, 14, VoxelFixtures.SOLID); // the pen's north wall
        w.fill(10, 1, 9, 40, 3, 9, VoxelFixtures.SOLID); // its south wall
        w.fill(10, 1, 9, 10, 3, 14, VoxelFixtures.SOLID); // its west wall; the east end is open
        return w;
    }

    /** Eleven units north of the pen, round (20, 30), and one inside it at (12.5, 11.5). */
    private static int[] members(SimHarness h) {
        List<Integer> all = new ArrayList<>();
        for (int unit : h.spawn(F0, new Vec2d(20, 30), 11, 3)) {
            all.add(unit);
        }
        all.add(h.spawn(F0, new Vec2d(12.5, 11.5), 1, 0)[0]);
        return all.stream().mapToInt(Integer::intValue).toArray();
    }

    private static int node(SurfaceGrid grid, int x, int z) {
        return PathService.startNode(grid, x + 0.5, 1, z + 0.5, 0);
    }

    private static boolean allIdle(SimHarness h, int[] units) {
        for (int unit : units) {
            UnitView u = h.queries().unit(unit);
            if (u == null || u.state() != UnitState.IDLE) {
                return false;
            }
            List<OrderQueues.Order> orders = h.queries().orders(unit);
            if (!orders.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static MatchRules joinCap(int cap) {
        MatchRules d = MatchRules.DEFAULTS;
        return new MatchRules(
                d.commandsPerTick(),
                d.unitCap(),
                d.delivery(),
                d.pathK(),
                d.pageK(),
                d.admissionW(),
                d.maxOutstanding(),
                d.busyRetry(),
                d.searchCap(),
                cap,
                d.shortMove(),
                d.groupMin());
    }

    private static SimHarness harness(VoxelWorld world, MatchRules rules) {
        SimHarness h = SimHarness.builder()
                .size(world.sizeX(), world.sizeZ())
                .rules(rules)
                .build();
        h.publishAll(world);
        return h;
    }
}
