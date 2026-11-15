package dev.strategos.core.client.build;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.client.model.ClientMatchModel;
import dev.strategos.core.protocol.VisionDelta;
import org.junit.jupiter.api.Test;

/** The ghost's explored rule reads the vision grid in its cells, not in blocks. */
class ClientPlacementExploredTest {
    @Test
    void aBlockColumnIsLookedUpInItsFourBlockCell() {
        ClientMatchModel model = new ClientMatchModel();
        // 4 x 4 cells (16 x 16 blocks); only cell (1, 0), blocks 4..7 by 0..3, is explored
        model.acceptVision(new VisionDelta(7, 1, 4, 4, true, new int[0], new int[] {1}), 7);
        assertThat(ClientPlacement.explored(model, 4, 0)).isTrue();
        assertThat(ClientPlacement.explored(model, 7, 3)).isTrue();
        assertThat(ClientPlacement.explored(model, 8, 0)).isFalse();
        assertThat(ClientPlacement.explored(model, 0, 0)).isFalse();
        assertThat(ClientPlacement.explored(model, 1, 5))
                .as("the cell at block 1 would be (1, 5) read as cells")
                .isFalse();
    }

    @Test
    void aClientWithNoVisionGridHasNothingHidden() {
        assertThat(ClientPlacement.explored(new ClientMatchModel(), 100, 100)).isTrue();
    }
}
