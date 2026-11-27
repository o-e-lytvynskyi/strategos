package dev.strategos.core.client.select;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** UI-17 in L1: a bookmark is set, jumped to, replaced and forgotten; one never set is unset. */
class CameraBookmarksTest {
    @Test
    void setJumpReplaceAndUnset() {
        CameraBookmarks marks = new CameraBookmarks();
        assertThat(marks.get(0)).as("never set").isNull();

        marks.set(0, 12.5, -3);
        marks.set(3, 100, 200);
        assertThat(marks.get(0)).containsExactly(12.5, -3);
        assertThat(marks.get(3)).containsExactly(100, 200);
        assertThat(marks.get(1)).isNull();

        marks.set(0, 1, 2);
        assertThat(marks.get(0)).as("set again: replaced").containsExactly(1, 2);

        double[] copy = marks.get(0);
        copy[0] = 99;
        assertThat(marks.get(0)).as("a read is a copy").containsExactly(1, 2);

        marks.clear();
        assertThat(marks.get(0)).isNull();
        assertThat(marks.get(3)).isNull();
    }

    @Test
    void thereAreFour() {
        CameraBookmarks marks = new CameraBookmarks();
        assertThat(CameraBookmarks.COUNT).isEqualTo(4);
        assertThatThrownBy(() -> marks.set(4, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> marks.get(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
