package dev.strategos.core.protocol;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.Hasher;

/**
 * Sound the all clear at own finished {@code building} (BLD-16): the workers the bell called in, inside or on the way,
 * go back to what they were doing.
 */
public record AllClear(int building) implements Command {
    static final MapCodec<AllClear> CODEC = RecordCodecBuilder.mapCodec(i ->
            i.group(Codec.INT.fieldOf("building").forGetter(AllClear::building)).apply(i, AllClear::new));

    /** The building's handle; wire id 29. */
    static final WireCodec<AllClear> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, AllClear value) {
            out.writeVarInt(value.building());
        }

        @Override
        public AllClear read(WireReader in) {
            return new AllClear(in.readVarInt());
        }

        @Override
        public int maxSize() {
            return 5;
        }
    };

    @Override
    public void hashInto(Hasher h) {
        h.putByte((byte) 29); // the command type: ... 27 Ungarrison, 28 RingBell, 29 AllClear, 30 AttackSpread
        h.putInt(building);
    }
}
