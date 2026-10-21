package dev.strategos.mod.entity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.mod.testing.CodecSizes;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

/** UNIT-01: the spawn data survives the wire exactly and stays within 16 bytes. */
class UnitSpawnDataTest {
    /** The largest value M0 can send: any match id, a handle at the last index and generation, the last faction. */
    private static final UnitSpawnData LARGEST = new UnitSpawnData(
            Integer.MAX_VALUE,
            Handle.of(Handle.MAX_INDEX, Handle.MAX_GENERATION),
            127,
            FactionSlot.MAX,
            UnitSpawnData.KINEMATIC);

    @Test
    void roundTripsWithNoBytesLeft() {
        for (UnitSpawnData data : List.of(
                new UnitSpawnData(1, Handle.of(0, 1), 0, 0, UnitSpawnData.KINEMATIC),
                LARGEST,
                new UnitSpawnData(-1, -1, -1, -1, (byte) -1))) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                UnitSpawnData.STREAM_CODEC.encode(buf, data);
                assertThat(UnitSpawnData.STREAM_CODEC.decode(buf)).isEqualTo(data);
                assertThat(buf.readableBytes()).as("bytes left after %s", data).isZero();
            } finally {
                buf.release();
            }
        }
    }

    @Test
    void theLargestFitsIn16Bytes() {
        CodecSizes.assertEncodedSize(UnitSpawnData.STREAM_CODEC, LARGEST, 16);
        assertThat(CodecSizes.encodedSize(UnitSpawnData.STREAM_CODEC, LARGEST))
                .as("5 + 5 + 1 + 1 + 1: a negative handle takes a full VarInt")
                .isEqualTo(13);
    }
}
