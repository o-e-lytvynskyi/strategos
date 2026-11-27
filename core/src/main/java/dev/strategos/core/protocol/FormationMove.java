package dev.strategos.core.protocol;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.Hasher;
import dev.strategos.core.shared.Vec2d;

/**
 * March own {@code units} to {@code target} in one formation of {@code shape} (ORD-06, 04 §9.4): the units keep their
 * places in it, walk at its slowest member's speed and hold its shape on arrival, until one is given an order of its
 * own, or the formation is told to gather or attack. Only {@link QueueMode#REPLACE} is taken.
 */
public record FormationMove(UnitSet units, Vec2d target, FormationShape shape, QueueMode mode) implements Command {
    static final MapCodec<FormationMove> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                    UnitSet.CODEC.fieldOf("units").forGetter(FormationMove::units),
                    Vec2d.CODEC.fieldOf("target").forGetter(FormationMove::target),
                    FormationShape.CODEC.fieldOf("shape").forGetter(FormationMove::shape),
                    QueueMode.CODEC.fieldOf("mode").forGetter(FormationMove::mode))
            .apply(i, FormationMove::new));

    /** Units, target, shape, queue mode; wire id 38. */
    static final WireCodec<FormationMove> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, FormationMove value) {
            WireCodecs.UNIT_SET.write(out, value.units());
            WireCodecs.VEC2D.write(out, value.target());
            FormationShape.WIRE.write(out, value.shape());
            WireCodecs.QUEUE_MODE.write(out, value.mode());
        }

        @Override
        public FormationMove read(WireReader in) {
            return new FormationMove(
                    WireCodecs.UNIT_SET.read(in),
                    WireCodecs.VEC2D.read(in),
                    FormationShape.WIRE.read(in),
                    WireCodecs.QUEUE_MODE.read(in));
        }

        @Override
        public int maxSize() {
            return WireCodecs.UNIT_SET.maxSize()
                    + WireCodecs.VEC2D.maxSize()
                    + FormationShape.WIRE.maxSize()
                    + WireCodecs.QUEUE_MODE.maxSize();
        }
    };

    /** Replaces the units' orders. */
    public static FormationMove replace(UnitSet units, Vec2d target, FormationShape shape) {
        return new FormationMove(units, target, shape, QueueMode.REPLACE);
    }

    @Override
    public void hashInto(Hasher h) {
        h.putByte((byte) 38); // the command type: ... 35 UseAbility, 36 Patrol, 37 Guard, 38 FormationMove
        units.hashInto(h);
        h.putDouble(target.x()).putDouble(target.z()).putInt(shape.code()).putString(mode.id());
    }
}
