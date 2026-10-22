package dev.strategos.core.protocol;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Hasher;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;

/** Admin only: what {@code /strategos debug spawn} and the tests use. Positions come from Rng inside the core. */
public record SpawnUnits(TemplateId template, FactionSlot faction, Vec2d center, int count, double spread)
        implements Command {
    static final MapCodec<SpawnUnits> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                    TemplateId.CODEC.fieldOf("template").forGetter(SpawnUnits::template),
                    FactionSlot.CODEC.fieldOf("faction").forGetter(SpawnUnits::faction),
                    Vec2d.CODEC.fieldOf("center").forGetter(SpawnUnits::center),
                    Codec.INT.fieldOf("count").forGetter(SpawnUnits::count),
                    Codec.DOUBLE.fieldOf("spread").forGetter(SpawnUnits::spread))
            .apply(i, SpawnUnits::new));

    /** Template, faction, centre, count, spread; wire id 3. */
    static final WireCodec<SpawnUnits> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, SpawnUnits value) {
            WireCodecs.TEMPLATE_ID.write(out, value.template());
            WireCodecs.FACTION_SLOT.write(out, value.faction());
            WireCodecs.VEC2D.write(out, value.center());
            out.writeVarInt(value.count());
            out.writeDouble(value.spread());
        }

        @Override
        public SpawnUnits read(WireReader in) {
            TemplateId template = WireCodecs.TEMPLATE_ID.read(in);
            FactionSlot faction = WireCodecs.FACTION_SLOT.read(in);
            Vec2d center = WireCodecs.VEC2D.read(in);
            int count = in.readVarInt();
            double spread = in.readDouble();
            if (!Double.isFinite(spread)) {
                throw new WireException("non-finite spread " + spread);
            }
            return new SpawnUnits(template, faction, center, count, spread);
        }

        @Override
        public int maxSize() {
            return WireCodecs.TEMPLATE_ID.maxSize()
                    + WireCodecs.FACTION_SLOT.maxSize()
                    + WireCodecs.VEC2D.maxSize()
                    + 5
                    + 8;
        }
    };

    @Override
    public void hashInto(Hasher h) {
        h.putByte((byte) 3); // the command type: 1 Move, 2 Stop, 3 SpawnUnits
        h.putString(template.value()).putInt(faction.value());
        h.putDouble(center.x()).putDouble(center.z()).putInt(count).putDouble(spread);
    }
}
