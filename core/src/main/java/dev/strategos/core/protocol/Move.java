package dev.strategos.core.protocol;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.Hasher;
import dev.strategos.core.shared.Vec2d;

/** Walk to {@code target}; {@code formation} 0 means none (M0). */
public record Move(UnitSet units, Vec2d target, QueueMode mode, int formation) implements Command {
    static final MapCodec<Move> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                    UnitSet.CODEC.fieldOf("units").forGetter(Move::units),
                    Vec2d.CODEC.fieldOf("target").forGetter(Move::target),
                    QueueMode.CODEC.fieldOf("mode").forGetter(Move::mode),
                    Codec.INT.fieldOf("formation").forGetter(Move::formation))
            .apply(i, Move::new));

    /** Units, target, queue mode, formation; wire id 1. */
    static final WireCodec<Move> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, Move value) {
            WireCodecs.UNIT_SET.write(out, value.units());
            WireCodecs.VEC2D.write(out, value.target());
            WireCodecs.QUEUE_MODE.write(out, value.mode());
            out.writeZigZag(value.formation());
        }

        @Override
        public Move read(WireReader in) {
            return new Move(
                    WireCodecs.UNIT_SET.read(in),
                    WireCodecs.VEC2D.read(in),
                    WireCodecs.QUEUE_MODE.read(in),
                    in.readZigZagInt());
        }

        @Override
        public int maxSize() {
            return WireCodecs.UNIT_SET.maxSize() + WireCodecs.VEC2D.maxSize() + WireCodecs.QUEUE_MODE.maxSize() + 5;
        }
    };

    /** Replaces the units' orders, with no formation. */
    public static Move replace(UnitSet units, Vec2d target) {
        return new Move(units, target, QueueMode.REPLACE, 0);
    }

    @Override
    public void hashInto(Hasher h) {
        h.putByte((byte) 1); // the command type: 1 Move, 2 Stop, 3 SpawnUnits
        units.hashInto(h);
        h.putDouble(target.x()).putDouble(target.z()).putString(mode.id()).putInt(formation);
    }
}
