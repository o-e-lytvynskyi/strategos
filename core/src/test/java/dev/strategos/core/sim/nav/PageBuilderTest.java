package dev.strategos.core.sim.nav;

import static dev.strategos.core.testkit.VoxelFixtures.CARPET;
import static dev.strategos.core.testkit.VoxelFixtures.DOOR;
import static dev.strategos.core.testkit.VoxelFixtures.FENCE;
import static dev.strategos.core.testkit.VoxelFixtures.LAVA;
import static dev.strategos.core.testkit.VoxelFixtures.LEAVES;
import static dev.strategos.core.testkit.VoxelFixtures.MAGMA;
import static dev.strategos.core.testkit.VoxelFixtures.PATH;
import static dev.strategos.core.testkit.VoxelFixtures.SLAB;
import static dev.strategos.core.testkit.VoxelFixtures.SOLID;
import static dev.strategos.core.testkit.VoxelFixtures.SOUL_SAND;
import static dev.strategos.core.testkit.VoxelFixtures.STAIRS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.testkit.VoxelFixtures;
import dev.strategos.core.testkit.VoxelWorld;
import org.junit.jupiter.api.Test;

/** PATH-02.2: surfaces, kinds, links and costs of pages built from synthetic slices. */
class PageBuilderTest {
    private static final PageCoord ORIGIN = new PageCoord(0, 0);

    @Test
    void flatGroundHasOneLinkedGroundSurfacePerColumn() {
        SurfacePage page = build(VoxelFixtures.flat(16, 16, 16, 4));

        assertThat(page.surfaceCount()).isEqualTo(256);
        int s = only(page, 7, 7);
        assertThat(Surface.floor(s)).isEqualTo(64);
        assertThat(Surface.kind(s)).isEqualTo(Surface.GROUND);
        assertThat(Surface.headroom(s)).isEqualTo(Surface.MAX_HEADROOM);
        assertThat(Surface.links(s)).isEqualTo(0xFF);
        assertThat(Surface.cost(s)).isZero();
        assertThat(Surface.links(only(page, 0, 0)))
                .as("a page corner links only inside the page: E, SE, S")
                .isEqualTo((1 << Dir.E) | (1 << Dir.SE) | (1 << Dir.S));
    }

    @Test
    void floorsSitAtTheCollisionTop() {
        VoxelWorld w = VoxelFixtures.flat(16, 16, 16, 2);
        w.set(1, 2, 1, SLAB).set(2, 2, 1, CARPET).set(3, 2, 1, VoxelFixtures.snow(3));
        w.set(4, 2, 1, PATH).set(5, 2, 1, STAIRS).set(6, 2, 1, VoxelFixtures.snow(1));

        SurfacePage page = build(w);

        assertThat(Surface.floor(only(page, 1, 1))).as("slab").isEqualTo(40);
        assertThat(Surface.floor(only(page, 2, 1))).as("carpet").isEqualTo(33);
        assertThat(Surface.floor(only(page, 3, 1)))
                .as("three snow layers collide at 4/16")
                .isEqualTo(36);
        assertThat(Surface.floor(only(page, 4, 1))).as("dirt path").isEqualTo(47);
        assertThat(Surface.floor(only(page, 5, 1))).as("stairs").isEqualTo(48);
        assertThat(Surface.floor(only(page, 6, 1)))
                .as("one snow layer has no collision")
                .isEqualTo(32);
    }

    @Test
    void waterOneDeepIsShallowAndTwoDeepIsImpassable() {
        VoxelWorld w = VoxelFixtures.heightmap(16, """
                ..........~=......
                """.substring(0, 16));

        SurfacePage page = build(w);

        int shallow = only(page, 10, 0);
        assertThat(Surface.floor(shallow)).isEqualTo(16);
        assertThat(Surface.kind(shallow)).isEqualTo(Surface.SHALLOW_WATER);
        assertThat(Surface.passable(shallow)).isTrue();
        int deep = only(page, 11, 0);
        assertThat(Surface.kind(deep)).isEqualTo(Surface.DEEP_WATER);
        assertThat(Surface.passable(deep)).isFalse();
        assertThat(Surface.links(deep)).isZero();
        assertThat(Surface.linked(shallow, Dir.E))
                .as("nothing links into deep water")
                .isFalse();
    }

    @Test
    void hazardsAndSlowGroundAreMarked() {
        VoxelWorld w = VoxelFixtures.flat(16, 16, 16, 1);
        w.set(2, 1, 2, LAVA).set(3, 0, 2, MAGMA).set(4, 0, 2, SOUL_SAND);

        SurfacePage page = build(w);

        assertThat(Surface.kind(only(page, 2, 2))).as("lava over the floor").isEqualTo(Surface.HAZARD);
        assertThat(Surface.kind(only(page, 3, 2))).as("magma").isEqualTo(Surface.HAZARD);
        assertThat(Surface.passable(only(page, 3, 2))).isFalse();
        int soul = only(page, 4, 2);
        assertThat(Surface.kind(soul)).isEqualTo(Surface.SLOW);
        assertThat(Surface.floor(soul)).isEqualTo(14);
        assertThat(Surface.linked(only(page, 1, 2), Dir.E))
                .as("no link into lava")
                .isFalse();
    }

    @Test
    void leavesAreNeverAFloorButLeaveRoomBelow() {
        VoxelWorld w = VoxelFixtures.flat(16, 16, 16, 1);
        w.fill(4, 4, 4, 6, 5, 6, LEAVES);

        SurfacePage page = build(w);

        int under = only(page, 5, 5);
        assertThat(Surface.floor(under)).isEqualTo(16);
        assertThat(Surface.headroom(under)).isEqualTo(48);
    }

    @Test
    void aLowCeilingLeavesNoSurfaceUnderIt() {
        VoxelWorld w = VoxelFixtures.flat(16, 16, 16, 1);
        w.set(5, 2, 5, SOLID);

        SurfacePage page = build(w);

        assertThat(page.count(SurfacePage.column(5, 5)))
                .as("only the block's top")
                .isEqualTo(1);
        assertThat(Surface.floor(only(page, 5, 5))).isEqualTo(48);
    }

    @Test
    void aDoorwayIsADoorSurface() {
        VoxelWorld w = VoxelFixtures.flat(16, 16, 16, 1);
        w.fill(0, 1, 8, 15, 5, 8, SOLID);
        w.set(7, 1, 8, DOOR).set(7, 2, 8, DOOR);

        SurfacePage page = build(w);

        int door = page.surface(SurfacePage.column(7, 8), 1);
        assertThat(Surface.floor(door)).isEqualTo(16);
        assertThat(Surface.kind(door)).isEqualTo(Surface.DOOR);
        assertThat(Surface.linked(door, Dir.N) && Surface.linked(door, Dir.S))
                .as("the doorway joins both sides")
                .isTrue();
    }

    @Test
    void aBridgeOverGroundGivesTwoSurfacesTopmostFirst() {
        VoxelWorld w = VoxelFixtures.flat(16, 16, 16, 1);
        w.fill(4, 4, 8, 11, 4, 8, SOLID);

        SurfacePage page = build(w);

        int column = SurfacePage.column(6, 8);
        assertThat(page.count(column)).isEqualTo(2);
        assertThat(Surface.floor(page.surface(column, 0))).as("the deck").isEqualTo(80);
        assertThat(Surface.floor(page.surface(column, 1)))
                .as("the ground under it")
                .isEqualTo(16);
        assertThat(Surface.headroom(page.surface(column, 1))).isEqualTo(48);
        assertThat(Surface.linked(page.surface(column, 0), Dir.E))
                .as("along the deck")
                .isTrue();
        assertThat(Surface.linked(page.surface(column, 0), Dir.N))
                .as("a 4-block drop is too far")
                .isFalse();
        assertThat(Surface.linked(page.surface(column, 1), Dir.N))
                .as("under the bridge")
                .isTrue();
    }

    @Test
    void aWallWalkwayIsWalkableAlongItsTop() {
        VoxelWorld w = VoxelFixtures.flat(16, 16, 16, 1);
        w.fill(2, 1, 5, 12, 4, 5, SOLID);

        SurfacePage page = build(w);

        int top = only(page, 6, 5);
        assertThat(Surface.floor(top)).isEqualTo(80);
        assertThat(Surface.linked(top, Dir.E) && Surface.linked(top, Dir.W)).isTrue();
        assertThat(Surface.linked(top, Dir.N) || Surface.linked(top, Dir.S)).isFalse();
        assertThat(Surface.cost(top)).as("both sides drop away").isEqualTo(2);
    }

    @Test
    void aTowerKeepsItsFourTopFloorsAndCountsTheRest() {
        VoxelWorld w = new VoxelWorld(16, 32, 16);
        for (int floor = 0; floor < 5; floor++) {
            w.set(8, floor * 3, 8, SOLID);
        }

        SurfacePage page = build(w);

        int column = SurfacePage.column(8, 8);
        assertThat(page.count(column)).isEqualTo(SurfacePage.MAX_LAYERS);
        assertThat(Surface.floor(page.surface(column, 0))).isEqualTo(13 * 16);
        assertThat(Surface.floor(page.surface(column, 3))).isEqualTo(4 * 16);
        assertThat(page.overflow()).isEqualTo(1);
        assertThat(page.overflowColumns()).isEqualTo(1);
    }

    @Test
    void stepsAndDropsFollowTheMobilityClass() {
        SurfacePage page = build(VoxelFixtures.heightmap(16, """
                1244500000000000
                """));

        assertThat(Surface.linked(only(page, 0, 0), Dir.E)).as("one block up").isTrue();
        assertThat(Surface.linked(only(page, 1, 0), Dir.E)).as("two blocks up").isFalse();
        assertThat(Surface.linked(only(page, 2, 0), Dir.W))
                .as("two blocks down")
                .isTrue();
        assertThat(Surface.linked(only(page, 3, 0), Dir.E))
                .as("one block up, from 4 to 5")
                .isTrue();
        assertThat(Surface.linked(only(page, 4, 0), Dir.W)).as("one block down").isTrue();
        SurfacePage cliff = build(VoxelFixtures.heightmap(16, """
                5100000000000000
                """));
        assertThat(Surface.linked(only(cliff, 0, 0), Dir.E))
                .as("a 4-block drop")
                .isFalse();
    }

    @Test
    void aFenceBlocksTheColumnItStandsIn() {
        VoxelWorld w = VoxelFixtures.flat(16, 16, 16, 1);
        w.set(5, 1, 5, FENCE);

        SurfacePage page = build(w);

        assertThat(page.count(SurfacePage.column(5, 5))).isZero();
        assertThat(Surface.linked(only(page, 4, 5), Dir.E)).isFalse();
        assertThat(Surface.cost(only(page, 4, 5))).isEqualTo(1);
    }

    @Test
    void aDiagonalNeedsBothOrthogonalNeighbours() {
        VoxelWorld w = VoxelFixtures.flat(16, 16, 16, 1);
        w.fill(5, 1, 4, 5, 5, 4, SOLID);

        int s = only(build(w), 4, 4);

        assertThat(Surface.linked(s, Dir.N)).isTrue();
        assertThat(Surface.linked(s, Dir.NE))
                .as("E is a wall: no corner cutting")
                .isFalse();
        assertThat(Surface.linked(s, Dir.SE)).isFalse();
        assertThat(Surface.linked(s, Dir.NW)).isTrue();
    }

    @Test
    void aStepUnderAnOverhangNeedsClearanceAboveTheHigherFloor() {
        VoxelWorld w = VoxelFixtures.flat(16, 16, 16, 1);
        w.set(6, 1, 6, SOLID);
        w.set(5, 3, 6, SOLID);

        SurfacePage page = build(w);

        int column = SurfacePage.column(5, 6);
        assertThat(page.count(column)).as("the overhang's top and the ground").isEqualTo(2);
        int low = page.surface(column, 1);
        assertThat(Surface.headroom(low)).as("the low column alone has room").isEqualTo(32);
        assertThat(Surface.linked(low, Dir.E))
                .as("stepping up would hit the overhang")
                .isFalse();
    }

    @Test
    void footprintsRemoveTheSurfacesTheyCover() {
        NavOverlay overlay = NavOverlay.builder().block(3, 3).build();
        SurfacePage page = new PageBuilder(MobilityClass.INFANTRY)
                .build(VoxelFixtures.flat(16, 16, 16, 1).slice(ORIGIN), overlay, ORIGIN);

        assertThat(page.count(SurfacePage.column(3, 3))).isZero();
        assertThat(Surface.linked(only(page, 2, 3), Dir.E)).isFalse();
    }

    private static SurfacePage build(VoxelWorld w) {
        return new PageBuilder(MobilityClass.INFANTRY).build(w.slice(ORIGIN), NavOverlay.EMPTY, ORIGIN);
    }

    private static int only(SurfacePage page, int x, int z) {
        int column = SurfacePage.column(x, z);
        assertThat(page.count(column)).as("surfaces in column %d, %d", x, z).isEqualTo(1);
        return page.surface(column, 0);
    }
}
