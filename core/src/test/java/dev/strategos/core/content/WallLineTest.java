package dev.strategos.core.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * BLD-13.1 in L1 (M9 spec §2.10): a line is cut into end-to-end segments with no gap and no overlap, in either
 * direction and along either axis; each stands at its own base, the floor under its middle, so a step puts the next
 * segment at its own height; the named indices are gates.
 */
class WallLineTest {
    static final WallLine.Floor FLAT = (x, z) -> 33;

    /** The columns a segment covers along its run, as the footprint of a 4 x 1 blueprint at its rotation does. */
    private static List<Integer> run(WallLine.Segment s, boolean alongX) {
        List<Integer> out = new ArrayList<>();
        for (int k = 0; k < 4; k++) {
            out.add(alongX ? s.x() + k : s.z() + k);
        }
        return out;
    }

    @Test
    void segmentsRunEndToEndWithNoGapAndNoOverlapInEveryDirection() {
        java.util.Random rng = new java.util.Random(7);
        for (int trial = 0; trial < 200; trial++) {
            int x1 = 20 + rng.nextInt(60);
            int z1 = 20 + rng.nextInt(60);
            int x2 = 20 + rng.nextInt(60);
            int z2 = 20 + rng.nextInt(60);
            boolean alongX = Math.abs(x2 - x1) >= Math.abs(z2 - z1);
            List<WallLine.Segment> segments = WallLine.of(x1, z1, x2, z2, 4, List.of(), FLAT);
            assertThat(segments).hasSize(WallLine.count(x1, z1, x2, z2, 4));
            TreeSet<Integer> covered = new TreeSet<>();
            int cells = 0;
            for (WallLine.Segment s : segments) {
                assertThat(s.rotation()).isEqualTo(alongX ? 0 : 1);
                assertThat(alongX ? s.z() : s.x()).as("the fixed coordinate").isEqualTo(alongX ? z1 : x1);
                covered.addAll(run(s, alongX));
                cells += 4;
            }
            assertThat(covered).as("no overlap").hasSize(cells);
            assertThat(covered.last() - covered.first() + 1).as("no gap").isEqualTo(cells);
            int from = alongX ? x1 : z1;
            int to = alongX ? x2 : z2;
            assertThat(covered).as("the run lies inside").contains(from, to);
            assertThat(cells - 4)
                    .as("only the last segment reaches past the end")
                    .isLessThan(Math.abs(to - from) + 1);
            assertThat(segments.get(0).index()).isZero();
        }
    }

    @Test
    void theCountIsLongArithmeticSoExtremeCoordinatesNeverWrap() {
        assertThat(WallLine.count(Integer.MIN_VALUE, 0, Integer.MAX_VALUE, 0, 4))
                .isEqualTo(1 << 30);
        assertThat(WallLine.count(0, Integer.MIN_VALUE, 0, Integer.MAX_VALUE, 1))
                .isEqualTo(Integer.MAX_VALUE);
        assertThat(WallLine.count(Integer.MAX_VALUE, 5, Integer.MIN_VALUE, 5, 1))
                .isEqualTo(Integer.MAX_VALUE);
        assertThat(WallLine.count(3, 3, 3, 3, 0)).as("no length, no segments").isZero();
    }

    @Test
    void aSinglePointIsOneSegmentAlongX() {
        List<WallLine.Segment> one = WallLine.of(30, 40, 30, 40, 4, List.of(), FLAT);
        assertThat(one).hasSize(1);
        assertThat(one.get(0).rotation()).isZero();
        assertThat(one.get(0).x()).isEqualTo(30);
    }

    @Test
    void aRunToTheLowerSideStartsAtThePressedColumn() {
        List<WallLine.Segment> back = WallLine.of(50, 40, 39, 40, 4, List.of(), FLAT);
        assertThat(back).extracting(WallLine.Segment::x).containsExactly(47, 43, 39);
        assertThat(back.get(0).x() + 3)
                .as("its first segment ends on the pressed column")
                .isEqualTo(50);
    }

    @Test
    void eachSegmentStandsAtItsMiddlesFloorSoAStepPutsTheNextAtItsOwnHeight() {
        WallLine.Floor stairs = (x, z) -> x < 44 ? 33 : x < 48 ? 37 : 40.4; // steps of 4 and 3 blocks
        List<WallLine.Segment> line = WallLine.of(40, 10, 51, 10, 4, List.of(), stairs);
        assertThat(line).extracting(WallLine.Segment::baseY).containsExactly(32, 36, 39);
        WallLine.Floor ramp = (x, z) -> 33 + (x - 40) * 0.25; // a gentle slope: the middle's floor, rounded
        assertThat(WallLine.of(40, 10, 51, 10, 4, List.of(), ramp))
                .extracting(WallLine.Segment::baseY)
                .containsExactly(32, 33, 34);
    }

    @Test
    void aSegmentWithNoLandUnderItsMiddleHasNoBase() {
        WallLine.Floor lake = (x, z) -> x >= 44 && x < 48 ? PlacementRules.NO_LAND : 33;
        assertThat(WallLine.of(40, 10, 51, 10, 4, List.of(), lake))
                .extracting(WallLine.Segment::baseY)
                .containsExactly(32, Integer.MIN_VALUE, 32);
    }

    @Test
    void theNamedIndicesAreGates() {
        List<WallLine.Segment> line = WallLine.of(40, 10, 40, 40, 4, List.of(1, 5), FLAT);
        assertThat(line).hasSize(8);
        assertThat(line)
                .filteredOn(WallLine.Segment::gate)
                .extracting(WallLine.Segment::index)
                .containsExactly(1, 5);
        assertThat(line.get(1).rotation()).as("along Z").isEqualTo(1);
    }

    @Test
    void noLengthMakesNoSegments() {
        assertThat(WallLine.of(0, 0, 10, 0, 0, List.of(), FLAT)).isEmpty();
    }
}
