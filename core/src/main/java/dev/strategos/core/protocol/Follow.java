package dev.strategos.core.protocol;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.Hasher;

/** Follow the issuer, a commander in first person (CAM-07): the units keep places behind it as it walks. */
public record Follow(UnitSet units, QueueMode mode) implements Command {
    static final MapCodec<Follow> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                    UnitSet.CODEC.fieldOf("units").forGetter(Follow::units),
                    QueueMode.CODEC.fieldOf("mode").forGetter(Follow::mode))
            .apply(i, Follow::new));

    /** Units, queue mode; wire id 6. */
    static final WireCodec<Follow> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, Follow value) {
            WireCodecs.UNIT_SET.write(out, value.units());
            WireCodecs.QUEUE_MODE.write(out, value.mode());
        }

        @Override
        public Follow read(WireReader in) {
            return new Follow(WireCodecs.UNIT_SET.read(in), WireCodecs.QUEUE_MODE.read(in));
        }

        @Override
        public int maxSize() {
            return WireCodecs.UNIT_SET.maxSize() + WireCodecs.QUEUE_MODE.maxSize();
        }
    };

    /** Replaces the units' orders. */
    public static Follow replace(UnitSet units) {
        return new Follow(units, QueueMode.REPLACE);
    }

    @Override
    public void hashInto(Hasher h) {
        h.putByte((byte) 6); // the command type: 1 Move, 2 Stop, 3 SpawnUnits, 4 Attack, 5 AttackMove, 6 Follow
        units.hashInto(h);
        h.putString(mode.id());
    }
}
