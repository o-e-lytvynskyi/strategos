package dev.strategos.core.protocol;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.Hasher;

/**
 * Demolish own finished building {@code building} (BLD-09), once the player has confirmed it: no refund; its blocks go
 * and its housing, dropsite and queue with it.
 */
public record Demolish(int building) implements Command {
    static final MapCodec<Demolish> CODEC = RecordCodecBuilder.mapCodec(i ->
            i.group(Codec.INT.fieldOf("building").forGetter(Demolish::building)).apply(i, Demolish::new));

    /** The building's handle; wire id 21. */
    static final WireCodec<Demolish> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, Demolish value) {
            out.writeVarInt(value.building());
        }

        @Override
        public Demolish read(WireReader in) {
            return new Demolish(in.readVarInt());
        }

        @Override
        public int maxSize() {
            return 5;
        }
    };

    @Override
    public void hashInto(Hasher h) {
        h.putByte((byte) 21); // the command type: ... 19 Build, 20 CancelBuilding, 21 Demolish, 24 SpawnDeposit
        h.putInt(building);
    }
}
