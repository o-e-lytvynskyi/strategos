package dev.strategos.core.protocol;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.Hasher;

/**
 * Ring the bell of own finished {@code building} (BLD-16): every own worker within 64 blocks puts the walk into the
 * nearest building with room before its other orders, and the bell sounds.
 */
public record RingBell(int building) implements Command {
    static final MapCodec<RingBell> CODEC = RecordCodecBuilder.mapCodec(i ->
            i.group(Codec.INT.fieldOf("building").forGetter(RingBell::building)).apply(i, RingBell::new));

    /** The building's handle; wire id 28. */
    static final WireCodec<RingBell> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, RingBell value) {
            out.writeVarInt(value.building());
        }

        @Override
        public RingBell read(WireReader in) {
            return new RingBell(in.readVarInt());
        }

        @Override
        public int maxSize() {
            return 5;
        }
    };

    @Override
    public void hashInto(Hasher h) {
        h.putByte((byte) 28); // the command type: ... 27 Ungarrison, 28 RingBell, 29 AllClear, 30 AttackSpread
        h.putInt(building);
    }
}
