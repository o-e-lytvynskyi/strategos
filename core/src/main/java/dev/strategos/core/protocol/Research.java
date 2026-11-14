package dev.strategos.core.protocol;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.Hasher;
import dev.strategos.core.shared.TechId;

/**
 * Research {@code tech} at building {@code building} (TECH-01): an entry in its queue beside unit batches, the cost paid
 * as it joins; once its time has passed the faction has the technology and its effects apply.
 */
public record Research(int building, TechId tech) implements Command {
    static final MapCodec<Research> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                    Codec.INT.fieldOf("building").forGetter(Research::building),
                    TechId.CODEC.fieldOf("tech").forGetter(Research::tech))
            .apply(i, Research::new));

    /** The building's handle, the tech's id; wire id 31. */
    static final WireCodec<Research> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, Research value) {
            out.writeVarInt(value.building());
            WireCodecs.TECH_ID.write(out, value.tech());
        }

        @Override
        public Research read(WireReader in) {
            return new Research(in.readVarInt(), WireCodecs.TECH_ID.read(in));
        }

        @Override
        public int maxSize() {
            return 5 + WireCodecs.TECH_ID.maxSize();
        }
    };

    @Override
    public void hashInto(Hasher h) {
        h.putByte((byte) 31); // the command type: ... 24 SpawnDeposit, 31 Research
        h.putInt(building).putString(tech.value());
    }
}
