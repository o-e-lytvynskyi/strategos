package dev.strategos.core.protocol;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.Hasher;

/** Sow field {@code field} again (ECO-09): an empty field ripens afresh for its reseed's cost (D-14, manual in M3). */
public record Reseed(int field) implements Command {
    static final MapCodec<Reseed> CODEC = RecordCodecBuilder.mapCodec(
            i -> i.group(Codec.INT.fieldOf("field").forGetter(Reseed::field)).apply(i, Reseed::new));

    /** The field's handle; wire id 14. */
    static final WireCodec<Reseed> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, Reseed value) {
            out.writeVarInt(value.field());
        }

        @Override
        public Reseed read(WireReader in) {
            return new Reseed(in.readVarInt());
        }

        @Override
        public int maxSize() {
            return 5;
        }
    };

    @Override
    public void hashInto(Hasher h) {
        h.putByte((byte) 14); // the command type: ... 13 SetRally, 14 Reseed
        h.putInt(field);
    }
}
