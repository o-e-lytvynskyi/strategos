package dev.strategos.core.protocol;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.Hasher;

/**
 * Cancel own foundation or construction {@code building} (BLD-09): its cost comes back but for the share already
 * built, {@code cost × (1 − progress)} rounded down to the thousandth, and its blocks go.
 */
public record CancelBuilding(int building) implements Command {
    static final MapCodec<CancelBuilding> CODEC = RecordCodecBuilder.mapCodec(
            i -> i.group(Codec.INT.fieldOf("building").forGetter(CancelBuilding::building))
                    .apply(i, CancelBuilding::new));

    /** The building's handle; wire id 20. */
    static final WireCodec<CancelBuilding> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, CancelBuilding value) {
            out.writeVarInt(value.building());
        }

        @Override
        public CancelBuilding read(WireReader in) {
            return new CancelBuilding(in.readVarInt());
        }

        @Override
        public int maxSize() {
            return 5;
        }
    };

    @Override
    public void hashInto(Hasher h) {
        h.putByte((byte) 20); // the command type: ... 19 Build, 20 CancelBuilding, 21 Demolish, 24 SpawnDeposit
        h.putInt(building);
    }
}
