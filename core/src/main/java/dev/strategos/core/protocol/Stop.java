package dev.strategos.core.protocol;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.Hasher;

/** Clear the units' orders. */
public record Stop(UnitSet units) implements Command {
    static final MapCodec<Stop> CODEC = RecordCodecBuilder.mapCodec(
            i -> i.group(UnitSet.CODEC.fieldOf("units").forGetter(Stop::units)).apply(i, Stop::new));

    /** The units; wire id 2. */
    static final WireCodec<Stop> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, Stop value) {
            WireCodecs.UNIT_SET.write(out, value.units());
        }

        @Override
        public Stop read(WireReader in) {
            return new Stop(WireCodecs.UNIT_SET.read(in));
        }

        @Override
        public int maxSize() {
            return WireCodecs.UNIT_SET.maxSize();
        }
    };

    @Override
    public void hashInto(Hasher h) {
        h.putByte((byte) 2); // the command type: 1 Move, 2 Stop, 3 SpawnUnits
        units.hashInto(h);
    }
}
