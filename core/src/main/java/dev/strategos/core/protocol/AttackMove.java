package dev.strategos.core.protocol;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.Hasher;
import dev.strategos.core.shared.Vec2d;

/** Walk to {@code target}, fighting the enemies met on the way (ORD-04). */
public record AttackMove(UnitSet units, Vec2d target, QueueMode mode) implements Command {
    static final MapCodec<AttackMove> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                    UnitSet.CODEC.fieldOf("units").forGetter(AttackMove::units),
                    Vec2d.CODEC.fieldOf("target").forGetter(AttackMove::target),
                    QueueMode.CODEC.fieldOf("mode").forGetter(AttackMove::mode))
            .apply(i, AttackMove::new));

    /** Units, target, queue mode; wire id 5. */
    static final WireCodec<AttackMove> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, AttackMove value) {
            WireCodecs.UNIT_SET.write(out, value.units());
            WireCodecs.VEC2D.write(out, value.target());
            WireCodecs.QUEUE_MODE.write(out, value.mode());
        }

        @Override
        public AttackMove read(WireReader in) {
            return new AttackMove(
                    WireCodecs.UNIT_SET.read(in), WireCodecs.VEC2D.read(in), WireCodecs.QUEUE_MODE.read(in));
        }

        @Override
        public int maxSize() {
            return WireCodecs.UNIT_SET.maxSize() + WireCodecs.VEC2D.maxSize() + WireCodecs.QUEUE_MODE.maxSize();
        }
    };

    /** Replaces the units' orders. */
    public static AttackMove replace(UnitSet units, Vec2d target) {
        return new AttackMove(units, target, QueueMode.REPLACE);
    }

    @Override
    public void hashInto(Hasher h) {
        h.putByte((byte) 5); // the command type: 1 Move, 2 Stop, 3 SpawnUnits, 4 Attack, 5 AttackMove
        units.hashInto(h);
        h.putDouble(target.x()).putDouble(target.z()).putString(mode.id());
    }
}
