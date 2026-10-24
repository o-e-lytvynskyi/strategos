package dev.strategos.core.protocol;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.Hasher;

/**
 * Send builders to building {@code building} (M4 §1.6 row 1): the issuer's units that can build take a build order on
 * an own foundation or construction, joining or replacing what they do as {@code mode} says.
 */
public record Build(UnitSet units, int building, QueueMode mode) implements Command {
    static final MapCodec<Build> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                    UnitSet.CODEC.fieldOf("units").forGetter(Build::units),
                    Codec.INT.fieldOf("building").forGetter(Build::building),
                    QueueMode.CODEC.fieldOf("mode").forGetter(Build::mode))
            .apply(i, Build::new));

    /** Units, the building's handle, queue mode; wire id 19. */
    static final WireCodec<Build> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, Build value) {
            WireCodecs.UNIT_SET.write(out, value.units());
            out.writeVarInt(value.building());
            WireCodecs.QUEUE_MODE.write(out, value.mode());
        }

        @Override
        public Build read(WireReader in) {
            return new Build(WireCodecs.UNIT_SET.read(in), in.readVarInt(), WireCodecs.QUEUE_MODE.read(in));
        }

        @Override
        public int maxSize() {
            return WireCodecs.UNIT_SET.maxSize() + 5 + WireCodecs.QUEUE_MODE.maxSize();
        }
    };

    /** Replaces the units' orders. */
    public static Build replace(UnitSet units, int building) {
        return new Build(units, building, QueueMode.REPLACE);
    }

    @Override
    public void hashInto(Hasher h) {
        h.putByte((byte) 19); // the command type: ... 18 SetAi, 19 Build
        units.hashInto(h);
        h.putInt(building).putString(mode.id());
    }
}
