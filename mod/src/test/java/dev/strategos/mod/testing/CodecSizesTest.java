package dev.strategos.mod.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.shared.Handle;
import dev.strategos.mod.entity.UnitSpawnData;
import org.junit.jupiter.api.Test;

/** QA-03.2: the size check passes at the cap and fails above it, naming the type, the size and the cap. */
class CodecSizesTest {
    private static final UnitSpawnData DATA = new UnitSpawnData(7, Handle.of(12, 3), 0, 1, UnitSpawnData.KINEMATIC);

    @Test
    void passesAtTheCap() {
        int size = CodecSizes.encodedSize(UnitSpawnData.STREAM_CODEC, DATA);
        assertThat(size)
                .as("1 + 4 + 1 + 1 + 1: the handle (3 << 20 | 12) takes four VarInt bytes")
                .isEqualTo(8);

        CodecSizes.assertEncodedSize(UnitSpawnData.STREAM_CODEC, DATA, size);
    }

    @Test
    void failsAboveTheCapNamingTheTypeTheSizeAndTheCap() {
        assertThatThrownBy(() -> CodecSizes.assertEncodedSize(UnitSpawnData.STREAM_CODEC, DATA, 7))
                .isInstanceOf(AssertionError.class)
                .hasMessage("UnitSpawnData encoded to 8 bytes, cap 7");
    }
}
