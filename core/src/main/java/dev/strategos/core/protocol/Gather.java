package dev.strategos.core.protocol;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.Hasher;

/**
 * Gather at the block at match-local ({@code x}, {@code z}) and height {@code y} (ECO-05): the node that holds it, or
 * the nearest of its kind within a few blocks, which the server resolves; the units carry what they gather to their
 * nearest dropsite and come back until it runs out.
 */
public record Gather(UnitSet units, int x, int y, int z, QueueMode mode) implements Command {
    static final MapCodec<Gather> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                    UnitSet.CODEC.fieldOf("units").forGetter(Gather::units),
                    Codec.INT.fieldOf("x").forGetter(Gather::x),
                    Codec.INT.fieldOf("y").forGetter(Gather::y),
                    Codec.INT.fieldOf("z").forGetter(Gather::z),
                    QueueMode.CODEC.fieldOf("mode").forGetter(Gather::mode))
            .apply(i, Gather::new));

    /** Units, the block, queue mode; wire id 9. */
    static final WireCodec<Gather> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, Gather value) {
            WireCodecs.UNIT_SET.write(out, value.units());
            out.writeZigZag(value.x()).writeZigZag(value.y()).writeZigZag(value.z());
            WireCodecs.QUEUE_MODE.write(out, value.mode());
        }

        @Override
        public Gather read(WireReader in) {
            return new Gather(
                    WireCodecs.UNIT_SET.read(in),
                    in.readZigZagInt(),
                    in.readZigZagInt(),
                    in.readZigZagInt(),
                    WireCodecs.QUEUE_MODE.read(in));
        }

        @Override
        public int maxSize() {
            return WireCodecs.UNIT_SET.maxSize() + 5 + 5 + 5 + WireCodecs.QUEUE_MODE.maxSize();
        }
    };

    /** Replaces the units' orders. */
    public static Gather replace(UnitSet units, int x, int y, int z) {
        return new Gather(units, x, y, z, QueueMode.REPLACE);
    }

    @Override
    public void hashInto(Hasher h) {
        h.putByte((byte) 9); // the command type: ... 7 Grant, 8 SpawnBuilding, 9 Gather
        units.hashInto(h);
        h.putInt(x).putInt(y).putInt(z).putString(mode.id());
    }
}
