package dev.strategos.core.client.select;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SelectionControlTest {
    private static final Seen OWN = new Seen(11, 0, 7, 2, 2, 2, 1);
    private static final Seen GRANTED = new Seen(22, 1, 7, 3, 3, 3, 2);
    private static final Seen OTHER = new Seen(33, 2, 7, 4, 4, 4, 3);

    @Test
    void clickBoxAndDoubleClickUseExplicitControlWithoutChangingThePrimaryPerspective() {
        Selection selection = new Selection(0, faction -> faction == 0 || faction == 1);
        assertThat(selection.faction()).isZero();
        selection.click(GRANTED, false);
        assertThat(selection.handles()).containsExactly(22);
        selection.click(OTHER, false);
        assertThat(selection.handles()).isEmpty();
        assertThat(selection.inspected()).isEqualTo(33);
        selection.box(List.of(OWN, GRANTED, OTHER), 0, 0, 10, 10, false);
        assertThat(selection.handles()).containsExactly(11, 22);
        selection.allOfType(List.of(OWN, GRANTED, OTHER), OWN, false);
        assertThat(selection.handles()).containsExactly(11, 22);
    }

    @Test
    void revocationPrunesCommandsButAnInspectedForeignBuildingRemainsPubliclyInspectable() {
        AtomicInteger rights = new AtomicInteger(3);
        Selection selection =
                new Selection(0, faction -> faction >= 0 && faction < 16 && (rights.get() & (1 << faction)) != 0);
        selection.box(List.of(OWN, GRANTED), 0, 0, 10, 10, false);
        rights.set(1);
        selection.pruneControlled(handle -> handle == OWN.handle());
        assertThat(selection.handles()).containsExactly(11);
        rights.set(3);
        selection.clickBuilding(123, 1);
        assertThat(selection.building()).isEqualTo(123);
        rights.set(1);
        assertThat(selection.building()).isNull();
        assertThat(selection.shownBuilding()).isEqualTo(123);
        selection.clickBuilding(124, 0);
        assertThat(selection.building()).isEqualTo(124);
    }
}
