package dev.strategos.core.protocol;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.Hasher;
import dev.strategos.core.shared.TemplateId;

/**
 * Train {@code count} units of {@code template} at building {@code building} as one batch (PROD-02): paid in full as it
 * joins the queue, trained in the unit's time × count^0.8, and stepping out together. {@code count} is 1 to {@value
 * #MOST}.
 */
public record TrainBatch(int building, TemplateId template, int count) implements Command {
    /** The most a batch holds. */
    public static final int MOST = 5;

    static final MapCodec<TrainBatch> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                    Codec.INT.fieldOf("building").forGetter(TrainBatch::building),
                    TemplateId.CODEC.fieldOf("template").forGetter(TrainBatch::template),
                    Codec.INT.fieldOf("count").forGetter(TrainBatch::count))
            .apply(i, TrainBatch::new));

    /** The building's handle, the template, the count; wire id 23. */
    static final WireCodec<TrainBatch> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, TrainBatch value) {
            out.writeVarInt(value.building());
            WireCodecs.TEMPLATE_ID.write(out, value.template());
            out.writeVarInt(value.count());
        }

        @Override
        public TrainBatch read(WireReader in) {
            return new TrainBatch(in.readVarInt(), WireCodecs.TEMPLATE_ID.read(in), in.readVarInt());
        }

        @Override
        public int maxSize() {
            return 5 + WireCodecs.TEMPLATE_ID.maxSize() + 5;
        }
    };

    @Override
    public void hashInto(Hasher h) {
        h.putByte((byte) 23); // the command type: ... 21 Demolish, 23 TrainBatch, 24 SpawnDeposit
        h.putInt(building).putString(template.value()).putInt(count);
    }
}
