package dev.strategos.core.protocol;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.Hasher;

/**
 * Guard {@code target} (ORD-07): each of the own {@code units} follows a friendly unit or building (the issuer's or an
 * ally's) at a short distance, engages what attacks it or comes near it, and returns to it after the fight. The order
 * ends when the charge dies or changes hands. A target that is an enemy, or the unit itself, is refused.
 */
public record Guard(UnitSet units, int target, QueueMode mode) implements Command {
    static final MapCodec<Guard> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                    UnitSet.CODEC.fieldOf("units").forGetter(Guard::units),
                    Codec.INT.fieldOf("target").forGetter(Guard::target),
                    QueueMode.CODEC.fieldOf("mode").forGetter(Guard::mode))
            .apply(i, Guard::new));

    /** Units, the charge's handle, queue mode; wire id 37. */
    static final WireCodec<Guard> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, Guard value) {
            WireCodecs.UNIT_SET.write(out, value.units());
            out.writeVarInt(value.target());
            WireCodecs.QUEUE_MODE.write(out, value.mode());
        }

        @Override
        public Guard read(WireReader in) {
            return new Guard(WireCodecs.UNIT_SET.read(in), in.readVarInt(), WireCodecs.QUEUE_MODE.read(in));
        }

        @Override
        public int maxSize() {
            return WireCodecs.UNIT_SET.maxSize() + 5 + WireCodecs.QUEUE_MODE.maxSize();
        }
    };

    /** Replaces the units' orders. */
    public static Guard replace(UnitSet units, int target) {
        return new Guard(units, target, QueueMode.REPLACE);
    }

    @Override
    public void hashInto(Hasher h) {
        h.putByte((byte) 37); // the command type: ... 35 UseAbility, 36 Patrol, 37 Guard, 38 FormationMove
        units.hashInto(h);
        h.putInt(target).putString(mode.id());
    }
}
