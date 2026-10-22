package dev.strategos.core.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class WireReaderTest {

    @Test
    void readsBackEveryKindOfValue() {
        WireWriter out = new WireWriter(4);
        int[] varints = {0, 1, 127, 128, 16_383, 16_384, Integer.MAX_VALUE};
        for (int v : varints) {
            out.writeVarInt(v);
        }
        int[] zigzags = {0, -1, 1, Integer.MIN_VALUE, Integer.MAX_VALUE};
        for (int v : zigzags) {
            out.writeZigZag(v);
        }
        long[] longZigzags = {0, -1, Long.MIN_VALUE, Long.MAX_VALUE};
        for (long v : longZigzags) {
            out.writeZigZag(v);
        }
        out.writeVarLong(Long.MAX_VALUE)
                .writeLong(-42L)
                .writeInt(-7)
                .writeShort(0xBEEF)
                .writeBoolean(true);
        out.writeDouble(-0.0).writeDouble(Double.NaN).writeFloat(1.5f);
        out.writeString("strategos:unit/spearman", 64)
                .writeString("Ωmega ✓", 64)
                .writeBytes(new byte[] {1, 2, 3});

        WireReader in = new WireReader(out.toByteArray());
        for (int v : varints) {
            assertThat(in.readVarInt()).isEqualTo(v);
        }
        for (int v : zigzags) {
            assertThat(in.readZigZagInt()).isEqualTo(v);
        }
        for (long v : longZigzags) {
            assertThat(in.readZigZagLong()).isEqualTo(v);
        }
        assertThat(in.readVarLong()).isEqualTo(Long.MAX_VALUE);
        assertThat(in.readLong()).isEqualTo(-42L);
        assertThat(in.readInt()).isEqualTo(-7);
        assertThat(in.readUnsignedShort()).isEqualTo(0xBEEF);
        assertThat(in.readBoolean()).isTrue();
        assertThat(Double.doubleToRawLongBits(in.readDouble())).isEqualTo(Double.doubleToRawLongBits(-0.0));
        assertThat(in.readDouble()).isNaN();
        assertThat(in.readFloat()).isEqualTo(1.5f);
        assertThat(in.readString(64)).isEqualTo("strategos:unit/spearman");
        assertThat(in.readString(64)).isEqualTo("Ωmega ✓");
        assertThat(in.readBytes(3)).containsExactly(1, 2, 3);
        in.expectEnd();
    }

    @Test
    void smallValuesTakeOneByte() {
        assertThat(new WireWriter().writeVarInt(127).size()).isEqualTo(1);
        assertThat(new WireWriter().writeVarInt(128).size()).isEqualTo(2);
        assertThat(new WireWriter().writeZigZag(-64).size()).isEqualTo(1);
        assertThat(new WireWriter().writeZigZag(-65).size()).isEqualTo(2);
        assertThat(new WireWriter().writeVarInt(Integer.MAX_VALUE).size()).isEqualTo(5);
        assertThat(new WireWriter().writeZigZag(Long.MIN_VALUE).size()).isEqualTo(10);
    }

    @Test
    void refusesToReadPastTheEnd() {
        WireReader in = new WireReader(new byte[] {1, 2, 3});
        assertThatThrownBy(in::readInt).isInstanceOf(WireException.class).hasMessageContaining("needs 4 bytes, 3 left");
    }

    @Test
    void refusesMalformedVarints() {
        assertThatThrownBy(() -> new WireReader(bytes(0x80, 0x80, 0x80, 0x80, 0x80, 0x01)).readVarInt())
                .isInstanceOf(WireException.class)
                .hasMessageContaining("longer than 5 bytes");
        assertThatThrownBy(() -> new WireReader(bytes(0x80, 0x00)).readVarInt())
                .isInstanceOf(WireException.class)
                .hasMessageContaining("redundant zero");
        assertThatThrownBy(() -> new WireReader(bytes(0xFF, 0xFF, 0xFF, 0xFF, 0x0F)).readVarInt())
                .isInstanceOf(WireException.class)
                .hasMessageContaining("above");
        assertThatThrownBy(() -> new WireReader(bytes(0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x02))
                        .readZigZagLong())
                .isInstanceOf(WireException.class)
                .hasMessageContaining("64 bits");
    }

    @Test
    void refusesCountsAndLengthsAboveTheirBound() {
        byte[] three = new WireWriter().writeVarInt(3).toByteArray();
        assertThatThrownBy(() -> new WireReader(three).readVarInt(2))
                .isInstanceOf(WireException.class)
                .hasMessageContaining("above its bound 2");
        byte[] text = new WireWriter().writeString("abcdef", 6).toByteArray();
        assertThatThrownBy(() -> new WireReader(text).readString(5)).isInstanceOf(WireException.class);
        assertThatThrownBy(() -> new WireWriter().writeString("abcdef", 5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesMalformedUtf8AndBadBooleans() {
        assertThatThrownBy(() -> new WireReader(bytes(2, 0xC3, 0x28)).readString(8))
                .isInstanceOf(WireException.class)
                .hasMessageContaining("malformed UTF-8");
        assertThatThrownBy(() -> new WireReader(bytes(2)).readBoolean()).isInstanceOf(WireException.class);
    }

    @Test
    void refusesTrailingBytes() {
        WireReader in = new WireReader(bytes(1, 2));
        in.readByte();
        assertThatThrownBy(in::expectEnd).isInstanceOf(WireException.class).hasMessageContaining("1 trailing bytes");
    }

    @Test
    void writingANegativeVarintIsABug() {
        assertThatThrownBy(() -> new WireWriter().writeVarInt(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WireWriter().writeVarLong(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    private static byte[] bytes(int... values) {
        byte[] b = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            b[i] = (byte) values[i];
        }
        return b;
    }
}
