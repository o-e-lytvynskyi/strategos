package dev.strategos.core.protocol;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.Hasher;

/**
 * Upgrade own finished building {@code building} to the template its {@code Upgrade/to} names (BLD-12): an entry at the
 * head of its queue, the cost paid as it joins; once its time has passed the building is the new template in place.
 */
public record UpgradeBuilding(int building) implements Command {
    static final MapCodec<UpgradeBuilding> CODEC = RecordCodecBuilder.mapCodec(
            i -> i.group(Codec.INT.fieldOf("building").forGetter(UpgradeBuilding::building))
                    .apply(i, UpgradeBuilding::new));

    /** The building's handle; wire id 33. */
    static final WireCodec<UpgradeBuilding> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, UpgradeBuilding value) {
            out.writeVarInt(value.building());
        }

        @Override
        public UpgradeBuilding read(WireReader in) {
            return new UpgradeBuilding(in.readVarInt());
        }

        @Override
        public int maxSize() {
            return 5;
        }
    };

    @Override
    public void hashInto(Hasher h) {
        h.putByte((byte) 33); // the command type: ... 31 Research, 33 UpgradeBuilding
        h.putInt(building);
    }
}
