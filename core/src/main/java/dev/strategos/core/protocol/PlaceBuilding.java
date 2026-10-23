package dev.strategos.core.protocol;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.Hasher;
import dev.strategos.core.shared.TemplateId;

/**
 * Place a building of {@code template} for the issuer's faction (BLD-02): its blueprint's corner at match-local cell
 * ({@code x}, {@code z}), turned {@code rotation} quarter turns; the server checks the placement rules, takes the cost
 * and lays the foundation, and {@code builders} go to build it, their orders joined by {@code mode}.
 */
public record PlaceBuilding(TemplateId template, int x, int z, int rotation, UnitSet builders, QueueMode mode)
        implements Command {
    static final MapCodec<PlaceBuilding> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                    TemplateId.CODEC.fieldOf("template").forGetter(PlaceBuilding::template),
                    Codec.INT.fieldOf("x").forGetter(PlaceBuilding::x),
                    Codec.INT.fieldOf("z").forGetter(PlaceBuilding::z),
                    Codec.INT.optionalFieldOf("rotation", 0).forGetter(PlaceBuilding::rotation),
                    UnitSet.CODEC.fieldOf("builders").forGetter(PlaceBuilding::builders),
                    QueueMode.CODEC.fieldOf("mode").forGetter(PlaceBuilding::mode))
            .apply(i, PlaceBuilding::new));

    /** Template, corner, rotation, builders, queue mode; wire id 10. */
    static final WireCodec<PlaceBuilding> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, PlaceBuilding value) {
            WireCodecs.TEMPLATE_ID.write(out, value.template());
            out.writeZigZag(value.x()).writeZigZag(value.z()).writeVarInt(value.rotation());
            WireCodecs.UNIT_SET.write(out, value.builders());
            WireCodecs.QUEUE_MODE.write(out, value.mode());
        }

        @Override
        public PlaceBuilding read(WireReader in) {
            TemplateId template = WireCodecs.TEMPLATE_ID.read(in);
            int x = in.readZigZagInt();
            int z = in.readZigZagInt();
            int rotation = in.readVarInt(3);
            return new PlaceBuilding(
                    template, x, z, rotation, WireCodecs.UNIT_SET.read(in), WireCodecs.QUEUE_MODE.read(in));
        }

        @Override
        public int maxSize() {
            return WireCodecs.TEMPLATE_ID.maxSize()
                    + 5
                    + 5
                    + 1
                    + WireCodecs.UNIT_SET.maxSize()
                    + WireCodecs.QUEUE_MODE.maxSize();
        }
    };

    @Override
    public void hashInto(Hasher h) {
        h.putByte((byte) 10); // the command type: ... 8 SpawnBuilding, 9 Gather, 10 PlaceBuilding
        h.putString(template.value()).putInt(x).putInt(z).putInt(rotation);
        builders.hashInto(h);
        h.putString(mode.id());
    }
}
