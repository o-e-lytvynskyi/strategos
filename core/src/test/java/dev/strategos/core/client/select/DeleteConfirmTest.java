package dev.strategos.core.client.select;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** UI-17 in L1: a stray delete key asks; only a second press on the same selection in time deletes. */
class DeleteConfirmTest {
    @Test
    void theFirstPressAsksAndTheSecondConfirms() {
        DeleteConfirm confirm = new DeleteConfirm();
        assertThat(confirm.press(List.of(1, 2), 0, 100)).isFalse();
        assertThat(confirm.open(110)).isTrue();
        assertThat(confirm.press(List.of(1, 2), 0, 110)).isTrue();
        assertThat(confirm.open(111)).as("asked once, confirmed once").isFalse();
        assertThat(confirm.press(List.of(1, 2), 0, 112))
                .as("a third press asks again")
                .isFalse();
    }

    @Test
    void aLatePressOrAnotherSelectionAsksAfresh() {
        DeleteConfirm confirm = new DeleteConfirm();
        confirm.press(List.of(1, 2), 0, 100);
        assertThat(confirm.press(List.of(1, 2), 0, 100 + DeleteConfirm.WINDOW + 1))
                .as("too late")
                .isFalse();
        assertThat(confirm.press(List.of(1, 3), 0, 100 + DeleteConfirm.WINDOW + 2))
                .as("another selection")
                .isFalse();
        assertThat(confirm.press(List.of(), 0x100001, 300)).as("a building").isFalse();
        assertThat(confirm.press(List.of(), 0x100001, 310)).isTrue();
        assertThat(confirm.open(1000)).isFalse();
    }
}
