package dev.strategos.core.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class HandleTest {

    @Test
    void roundTripsTheSmallestIssuedHandle() {
        int handle = Handle.of(0, 1);

        assertThat(Handle.index(handle)).isZero();
        assertThat(Handle.generation(handle)).isEqualTo(1);
    }

    @Test
    void roundTripsTheLargestIndexAndGeneration() {
        int handle = Handle.of(Handle.MAX_INDEX, Handle.MAX_GENERATION);

        assertThat(Handle.index(handle)).isEqualTo(Handle.MAX_INDEX);
        assertThat(Handle.generation(handle)).isEqualTo(Handle.MAX_GENERATION);
    }

    @Test
    void keepsTheTwentyBitIndexAndTwelveBitGenerationLayout() {
        // Literals on purpose: handles are saved and journaled, so the layout is a file format.
        assertThat(Handle.MAX_INDEX).isEqualTo(1_048_575);
        assertThat(Handle.MAX_GENERATION).isEqualTo(4_095);
        assertThat(Handle.of(5, 3)).isEqualTo(0x0030_0005);
        assertThat(Handle.of(Handle.MAX_INDEX, Handle.MAX_GENERATION)).isEqualTo(0xFFFF_FFFF);
    }

    @Test
    void neverIssuesNone() {
        assertThat(Handle.of(0, 1)).isNotEqualTo(Handle.NONE);
        assertThat(Handle.of(Handle.MAX_INDEX, Handle.MAX_GENERATION)).isNotEqualTo(Handle.NONE);
        assertThatThrownBy(() -> Handle.of(0, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nextGenerationCountsUpAndWrapsToOne() {
        assertThat(Handle.nextGeneration(0)).isEqualTo(1);
        assertThat(Handle.nextGeneration(1)).isEqualTo(2);
        assertThat(Handle.nextGeneration(Handle.MAX_GENERATION - 1)).isEqualTo(Handle.MAX_GENERATION);
        assertThat(Handle.nextGeneration(Handle.MAX_GENERATION)).isEqualTo(1);
    }

    @Test
    void rejectsAnIndexOutOfRange() {
        assertThatThrownBy(() -> Handle.of(-1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Handle.of(Handle.MAX_INDEX + 1, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAGenerationOutOfRange() {
        assertThatThrownBy(() -> Handle.of(0, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Handle.of(0, Handle.MAX_GENERATION + 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Handle.nextGeneration(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Handle.nextGeneration(Handle.MAX_GENERATION + 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void printsIndexAndGeneration() {
        assertThat(Handle.toString(Handle.of(42, 7))).isEqualTo("#42.7");
        assertThat(Handle.toString(Handle.NONE)).isEqualTo("#0.0");
    }
}
