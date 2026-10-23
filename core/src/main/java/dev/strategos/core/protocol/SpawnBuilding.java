package dev.strategos.core.protocol;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Hasher;
import dev.strategos.core.shared.TemplateId;

/**
 * Admin only (BLD-01): a finished building of {@code template} for {@code faction}, its blueprint's corner at
 * match-local cell ({@code x}, {@code z}), turned {@code rotation} quarter turns: a scenario's starting civic centre,
 * a test's house. Players place buildings through placement (BLD-02).
 */
public record SpawnBuilding(TemplateId template, FactionSlot faction, int x, int z, int rotation) implements Command {
    static final MapCodec<SpawnBuilding> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                    TemplateId.CODEC.fieldOf("template").forGetter(SpawnBuilding::template),
                    FactionSlot.CODEC.fieldOf("faction").forGetter(SpawnBuilding::faction),
                    Codec.INT.fieldOf("x").forGetter(SpawnBuilding::x),
                    Codec.INT.fieldOf("z").forGetter(SpawnBuilding::z),
                    Codec.INT.optionalFieldOf("rotation", 0).forGetter(SpawnBuilding::rotation))
            .apply(i, SpawnBuilding::new));

    /** Template, faction, corner, rotation; wire id 8. */
    static final WireCodec<SpawnBuilding> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, SpawnBuilding value) {
            WireCodecs.TEMPLATE_ID.write(out, value.template());
            WireCodecs.FACTION_SLOT.write(out, value.faction());
            out.writeZigZag(value.x()).writeZigZag(value.z()).writeVarInt(value.rotation());
        }

        @Override
        public SpawnBuilding read(WireReader in) {
            TemplateId template = WireCodecs.TEMPLATE_ID.read(in);
            FactionSlot faction = WireCodecs.FACTION_SLOT.read(in);
            return new SpawnBuilding(template, faction, in.readZigZagInt(), in.readZigZagInt(), in.readVarInt(3));
        }

        @Override
        public int maxSize() {
            return WireCodecs.TEMPLATE_ID.maxSize() + WireCodecs.FACTION_SLOT.maxSize() + 5 + 5 + 1;
        }
    };

    @Override
    public void hashInto(Hasher h) {
        h.putByte((byte) 8); // the command type: ... 7 Grant, 8 SpawnBuilding
        h.putString(template.value())
                .putInt(faction.value())
                .putInt(x)
                .putInt(z)
                .putInt(rotation);
    }
}
