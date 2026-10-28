package dev.strategos.core.protocol;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.Hasher;

/**
 * Send own {@code units} into own finished {@code building} (BLD-16): each walks to its door and goes in if it is of a
 * class the building admits and there is room, else its order fails. Replaces the units' orders, as a move does.
 */
public record Garrison(UnitSet units, int building) implements Command {
    static final MapCodec<Garrison> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                    UnitSet.CODEC.fieldOf("units").forGetter(Garrison::units),
                    Codec.INT.fieldOf("building").forGetter(Garrison::building))
            .apply(i, Garrison::new));

    /** Units, the building's handle; wire id 26. */
    static final WireCodec<Garrison> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, Garrison value) {
            WireCodecs.UNIT_SET.write(out, value.units());
            out.writeVarInt(value.building());
        }

        @Override
        public Garrison read(WireReader in) {
            return new Garrison(WireCodecs.UNIT_SET.read(in), in.readVarInt());
        }

        @Override
        public int maxSize() {
            return WireCodecs.UNIT_SET.maxSize() + 5;
        }
    };

    @Override
    public void hashInto(Hasher h) {
        h.putByte((byte) 26); // the command type: ... 25 SetStance, 26 Garrison, 27 Ungarrison, 28-29 the bell
        units.hashInto(h);
        h.putInt(building);
    }
}
