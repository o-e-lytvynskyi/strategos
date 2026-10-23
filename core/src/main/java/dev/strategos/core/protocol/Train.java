package dev.strategos.core.protocol;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.Hasher;
import dev.strategos.core.shared.TemplateId;

/** Train one unit of {@code template} at building {@code building} (PROD-01): its cost is paid as it joins the queue. */
public record Train(int building, TemplateId template) implements Command {
    static final MapCodec<Train> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                    Codec.INT.fieldOf("building").forGetter(Train::building),
                    TemplateId.CODEC.fieldOf("template").forGetter(Train::template))
            .apply(i, Train::new));

    /** The building's handle, the template; wire id 11. */
    static final WireCodec<Train> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, Train value) {
            out.writeVarInt(value.building());
            WireCodecs.TEMPLATE_ID.write(out, value.template());
        }

        @Override
        public Train read(WireReader in) {
            return new Train(in.readVarInt(), WireCodecs.TEMPLATE_ID.read(in));
        }

        @Override
        public int maxSize() {
            return 5 + WireCodecs.TEMPLATE_ID.maxSize();
        }
    };

    @Override
    public void hashInto(Hasher h) {
        h.putByte((byte) 11); // the command type: ... 10 PlaceBuilding, 11 Train
        h.putInt(building).putString(template.value());
    }
}
