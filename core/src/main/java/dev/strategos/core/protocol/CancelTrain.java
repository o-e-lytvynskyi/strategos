package dev.strategos.core.protocol;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.Hasher;

/** Cancel entry {@code index} of building {@code building}'s queue (PROD-01): its cost is refunded. */
public record CancelTrain(int building, int index) implements Command {
    static final MapCodec<CancelTrain> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                    Codec.INT.fieldOf("building").forGetter(CancelTrain::building),
                    Codec.INT.fieldOf("index").forGetter(CancelTrain::index))
            .apply(i, CancelTrain::new));

    /** The building's handle, the entry; wire id 12. */
    static final WireCodec<CancelTrain> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, CancelTrain value) {
            out.writeVarInt(value.building()).writeVarInt(value.index());
        }

        @Override
        public CancelTrain read(WireReader in) {
            return new CancelTrain(in.readVarInt(), in.readVarInt());
        }

        @Override
        public int maxSize() {
            return 5 + 5;
        }
    };

    @Override
    public void hashInto(Hasher h) {
        h.putByte((byte) 12); // the command type: ... 11 Train, 12 CancelTrain
        h.putInt(building).putInt(index);
    }
}
