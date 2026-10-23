package dev.strategos.core.protocol;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.Hasher;

/** Attack unit {@code target} (ORD-04): close in and strike until it dies or the order ends. */
public record Attack(UnitSet units, int target, QueueMode mode) implements Command {
    static final MapCodec<Attack> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                    UnitSet.CODEC.fieldOf("units").forGetter(Attack::units),
                    Codec.INT.fieldOf("target").forGetter(Attack::target),
                    QueueMode.CODEC.fieldOf("mode").forGetter(Attack::mode))
            .apply(i, Attack::new));

    /** Units, the target's handle, queue mode; wire id 4. */
    static final WireCodec<Attack> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, Attack value) {
            WireCodecs.UNIT_SET.write(out, value.units());
            out.writeVarInt(value.target());
            WireCodecs.QUEUE_MODE.write(out, value.mode());
        }

        @Override
        public Attack read(WireReader in) {
            return new Attack(WireCodecs.UNIT_SET.read(in), in.readVarInt(), WireCodecs.QUEUE_MODE.read(in));
        }

        @Override
        public int maxSize() {
            return WireCodecs.UNIT_SET.maxSize() + 5 + WireCodecs.QUEUE_MODE.maxSize();
        }
    };

    /** Replaces the units' orders. */
    public static Attack replace(UnitSet units, int target) {
        return new Attack(units, target, QueueMode.REPLACE);
    }

    @Override
    public void hashInto(Hasher h) {
        h.putByte((byte) 4); // the command type: 1 Move, 2 Stop, 3 SpawnUnits, 4 Attack, 5 AttackMove
        units.hashInto(h);
        h.putInt(target).putString(mode.id());
    }
}
