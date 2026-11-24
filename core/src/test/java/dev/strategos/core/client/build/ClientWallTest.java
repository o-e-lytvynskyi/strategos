package dev.strategos.core.client.build;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.PlacementRules;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.TemplateId;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * BLD-13.2's client half (L1): the preview of a drag has one segment per {@code WallLine} segment, each with the
 * verdict the placement rules give it, so refused segments (outside the land, past the stock) show as gaps; gates are
 * the gate template's.
 */
class ClientWallTest {
    static final TemplateSet T = TemplateSet.builtin();
    static final int PALISADE =
            T.byId(new TemplateId("strategos:building/palisade")).netIndex();
    static final int GATE =
            T.byId(new TemplateId("strategos:building/palisade_gate")).netIndex();
    static final List<String> RESOURCES = List.of("food", "wood", "stone", "iron");

    /** Flat ground at 33, own land (owner 0) west of x 80 and neutral beyond. */
    private static final ClientPlacement.Columns FLAT = new ClientPlacement.Columns() {
        @Override
        public double floor(int x, int z) {
            return 33;
        }

        @Override
        public boolean node(int x, int z) {
            return false;
        }

        @Override
        public int territory(int x, int z) {
            return x < 80 ? 0 : -1;
        }
    };

    private static PlacementRules.World world() {
        return ClientPlacement.world(T, List.of(), 160, 160, FLAT);
    }

    @Test
    void theSegmentLengthAndTheGateTemplateComeFromTheSet() {
        assertThat(ClientWall.segmentLength(T, PALISADE)).isEqualTo(4);
        assertThat(ClientWall.segmentLength(T, GATE)).isEqualTo(4);
        assertThat(ClientWall.segmentLength(T, 1)).as("infantry is no wall").isZero();
        assertThat(ClientWall.gateOf(T, PALISADE)).isEqualTo(GATE);
        assertThat(ClientWall.gateOf(T, 1)).isEqualTo(-1);
    }

    @Test
    void aLineHasOneVerdictASegmentAndGatesAreTheGateTemplate() {
        List<ClientWall.Segment> preview =
                ClientWall.preview(T, PALISADE, 40, 60, 55, 60, List.of(2), world(), 0, RESOURCES, null);
        assertThat(preview).hasSize(4);
        assertThat(preview)
                .extracting(ClientWall.Segment::template)
                .containsExactly(PALISADE, PALISADE, GATE, PALISADE);
        assertThat(preview).allMatch(s -> s.verdict().ok());
        assertThat(preview).extracting(s -> s.at().anchorX()).containsExactly(40, 44, 48, 52);
        assertThat(preview).extracting(s -> s.baseY()).containsOnly(32);
    }

    @Test
    void segmentsOnNeutralLandAreRefusedAndTheRestStand() {
        List<ClientWall.Segment> preview =
                ClientWall.preview(T, PALISADE, 70, 60, 91, 60, List.of(), world(), 0, RESOURCES, null);
        assertThat(preview).extracting(s -> s.verdict().ok()).containsExactly(true, true, false, false, false, false);
        assertThat(preview.get(2).verdict().failed()).isEqualTo(PlacementRules.Rule.TERRITORY);
    }

    @Test
    void theStockPaysSegmentsInOrderAndTheLaterOnesAreRefusedForIt() {
        List<Long> stock = List.of(0L, 30_000L, 0L, 0L); // 30 wood: two segments at 14
        List<ClientWall.Segment> preview =
                ClientWall.preview(T, PALISADE, 40, 60, 55, 60, List.of(), world(), 0, RESOURCES, stock);
        assertThat(preview).extracting(s -> s.verdict().ok()).containsExactly(true, true, false, false);
        assertThat(preview.get(2).verdict().failed()).isEqualTo(PlacementRules.Rule.AFFORDABLE);
    }

    @Test
    void whatIsNoWallOrTooLongOrGatelessHasNoPreview() {
        assertThat(ClientWall.preview(T, 1, 40, 60, 55, 60, List.of(), world(), 0, RESOURCES, null))
                .isEmpty();
        assertThat(ClientWall.preview(T, PALISADE, 0, 60, 300, 60, List.of(), world(), 0, RESOURCES, null))
                .as("more than 64 segments")
                .isEmpty();
        assertThat(ClientWall.preview(T, GATE, 40, 60, 55, 60, List.of(1), world(), 0, RESOURCES, null))
                .as("a gate template has no gate of its own")
                .isEmpty();
    }
}
