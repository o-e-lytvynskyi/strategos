package dev.strategos.core.protocol;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.Hasher;

/**
 * Let the {@code units} inside own {@code building} out at its door (BLD-16), back to the orders they kept; no units
 * means everyone inside.
 */
public record Ungarrison(int building, UnitSet units) implements Command {
    static final MapCodec<Ungarrison> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                    Codec.INT.fieldOf("building").forGetter(Ungarrison::building),
                    UnitSet.CODEC.fieldOf("units").forGetter(Ungarrison::units))
            .apply(i, Ungarrison::new));

    /** The building's handle, then the units; wire id 27. */
    static final WireCodec<Ungarrison> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, Ungarrison value) {
            out.writeVarInt(value.building());
            WireCodecs.UNIT_SET.write(out, value.units());
        }

        @Override
        public Ungarrison read(WireReader in) {
            return new Ungarrison(in.readVarInt(), WireCodecs.UNIT_SET.read(in));
        }

        @Override
        public int maxSize() {
            return 5 + WireCodecs.UNIT_SET.maxSize();
        }
    };

    @Override
    public void hashInto(Hasher h) {
        h.putByte((byte) 27); // the command type: ... 26 Garrison, 27 Ungarrison
        h.putInt(building);
        units.hashInto(h);
    }
}
