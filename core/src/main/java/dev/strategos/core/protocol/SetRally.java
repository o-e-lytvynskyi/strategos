package dev.strategos.core.protocol;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.Hasher;

/**
 * Set the rally point of {@code buildings} at the block at match-local ({@code x}, {@code z}) and height {@code y}
 * (PROD-03): a resource node there makes their new units gather, a building walk to it, the ground walk there.
 */
public record SetRally(UnitSet buildings, int x, int y, int z) implements Command {
    static final MapCodec<SetRally> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                    UnitSet.CODEC.fieldOf("buildings").forGetter(SetRally::buildings),
                    Codec.INT.fieldOf("x").forGetter(SetRally::x),
                    Codec.INT.fieldOf("y").forGetter(SetRally::y),
                    Codec.INT.fieldOf("z").forGetter(SetRally::z))
            .apply(i, SetRally::new));

    /** The buildings' handles, the block; wire id 13. */
    static final WireCodec<SetRally> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, SetRally value) {
            WireCodecs.UNIT_SET.write(out, value.buildings());
            out.writeZigZag(value.x()).writeZigZag(value.y()).writeZigZag(value.z());
        }

        @Override
        public SetRally read(WireReader in) {
            return new SetRally(
                    WireCodecs.UNIT_SET.read(in), in.readZigZagInt(), in.readZigZagInt(), in.readZigZagInt());
        }

        @Override
        public int maxSize() {
            return WireCodecs.UNIT_SET.maxSize() + 15;
        }
    };

    @Override
    public void hashInto(Hasher h) {
        h.putByte((byte) 13); // the command type: ... 12 CancelTrain, 13 SetRally
        buildings.hashInto(h);
        h.putInt(x).putInt(y).putInt(z);
    }
}
