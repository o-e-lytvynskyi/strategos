package dev.strategos.core.protocol;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.Hasher;

/**
 * Send own {@code units} to capture a building of another, non-allied faction (CMB-10): each unit with a {@code
 * Capture/strength} walks into reach of it and adds its strength every tick, until the building changes hands or the
 * order is replaced. Replaces the units' orders, as a move does.
 */
public record Capture(UnitSet units, int building) implements Command {
    static final MapCodec<Capture> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                    UnitSet.CODEC.fieldOf("units").forGetter(Capture::units),
                    Codec.INT.fieldOf("building").forGetter(Capture::building))
            .apply(i, Capture::new));

    /** Units, the building's handle; wire id 32. */
    static final WireCodec<Capture> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, Capture value) {
            WireCodecs.UNIT_SET.write(out, value.units());
            out.writeVarInt(value.building());
        }

        @Override
        public Capture read(WireReader in) {
            return new Capture(WireCodecs.UNIT_SET.read(in), in.readVarInt());
        }

        @Override
        public int maxSize() {
            return WireCodecs.UNIT_SET.maxSize() + 5;
        }
    };

    @Override
    public void hashInto(Hasher h) {
        h.putByte((byte) 32); // the command type: ... 30 AttackSpread, 31 Research, 32 Capture
        units.hashInto(h);
        h.putInt(building);
    }
}
