package dev.strategos.core.protocol;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.Hasher;

/**
 * Attack, spread over the enemies round {@code target} (ORD-05): the enemies within the target's sight share the
 * group's attackers, one to a target round the even split, and each attacker whose target dies takes another of them.
 */
public record AttackSpread(UnitSet units, int target, QueueMode mode) implements Command {
    static final MapCodec<AttackSpread> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                    UnitSet.CODEC.fieldOf("units").forGetter(AttackSpread::units),
                    Codec.INT.fieldOf("target").forGetter(AttackSpread::target),
                    QueueMode.CODEC.fieldOf("mode").forGetter(AttackSpread::mode))
            .apply(i, AttackSpread::new));

    /** Units, the target's handle, queue mode; wire id 30. */
    static final WireCodec<AttackSpread> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, AttackSpread value) {
            WireCodecs.UNIT_SET.write(out, value.units());
            out.writeVarInt(value.target());
            WireCodecs.QUEUE_MODE.write(out, value.mode());
        }

        @Override
        public AttackSpread read(WireReader in) {
            return new AttackSpread(WireCodecs.UNIT_SET.read(in), in.readVarInt(), WireCodecs.QUEUE_MODE.read(in));
        }

        @Override
        public int maxSize() {
            return WireCodecs.UNIT_SET.maxSize() + 5 + WireCodecs.QUEUE_MODE.maxSize();
        }
    };

    @Override
    public void hashInto(Hasher h) {
        h.putByte((byte) 30); // the command type: ... 25 SetStance, 26-29 garrison and bell, 30 AttackSpread
        units.hashInto(h);
        h.putInt(target).putString(mode.id());
    }
}
