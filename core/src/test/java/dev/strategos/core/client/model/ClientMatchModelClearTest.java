package dev.strategos.core.client.model;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.BuildingState;
import dev.strategos.core.protocol.DepositState;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A client that leaves a match and joins another with the same id (a world switch restarts the ids) must show the new
 * match's buildings and deposits, not the old match's at a later tick, which would be kept as "newer".
 */
class ClientMatchModelClearTest {
    @Test
    void clearForgetsBuildingsAndDepositsSoALowerTickOfTheNextMatchIsTaken() {
        ClientMatchModel model = new ClientMatchModel();
        model.accept(new BuildingState(1, 500, List.of(building(7))), 1);
        model.accept(new DepositState(1, 500, List.of(new DepositState.Entry(3, 0, 10, 10, 0, 5, 5))), 1);
        assertThat(model.buildings()).hasSize(1);

        model.clear();
        assertThat(model.buildings()).isEmpty();
        assertThat(model.deposits()).isEmpty();

        model.accept(new BuildingState(1, 10, List.of(building(8), building(9))), 1);
        model.accept(new DepositState(1, 10, List.of()), 1);
        assertThat(model.buildings()).extracting(BuildingState.Entry::handle).containsExactly(8, 9);
        assertThat(model.deposits()).isEmpty();
    }

    private static BuildingState.Entry building(int handle) {
        return new BuildingState.Entry(
                handle, 0, 0, 20, 20, 0, 64, 0, 0, 100, true, List.of(), false, 0, 0, 0, 0, 0, false, List.of());
    }
}
