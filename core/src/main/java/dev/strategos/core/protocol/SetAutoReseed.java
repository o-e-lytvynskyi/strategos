package dev.strategos.core.protocol;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.Hasher;

/**
 * Turn own farmstead {@code building}'s auto-reseed {@code on} or off (ECO-17): on, it sows the empty fields in its
 * reach as soon as their reseed can be paid.
 */
public record SetAutoReseed(int building, boolean on) implements Command {
    static final MapCodec<SetAutoReseed> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                    Codec.INT.fieldOf("building").forGetter(SetAutoReseed::building),
                    Codec.BOOL.fieldOf("on").forGetter(SetAutoReseed::on))
            .apply(i, SetAutoReseed::new));

    /** The building's handle, then on as 1 or off as 0; wire id 22. */
    static final WireCodec<SetAutoReseed> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, SetAutoReseed value) {
            out.writeVarInt(value.building()).writeByte(value.on() ? 1 : 0);
        }

        @Override
        public SetAutoReseed read(WireReader in) {
            int building = in.readVarInt();
            int on = in.readUnsignedByte();
            if (on > 1) {
                throw new WireException("auto-reseed " + on + " is neither on nor off");
            }
            return new SetAutoReseed(building, on == 1);
        }

        @Override
        public int maxSize() {
            return 5 + 1;
        }
    };

    @Override
    public void hashInto(Hasher h) {
        h.putByte((byte) 22); // the command type: ... 21 Demolish, 22 SetAutoReseed, 23 TrainBatch
        h.putInt(building).putByte((byte) (on ? 1 : 0));
    }
}
