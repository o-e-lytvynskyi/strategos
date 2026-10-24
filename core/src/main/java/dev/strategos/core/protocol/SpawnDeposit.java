package dev.strategos.core.protocol;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.Hasher;
import dev.strategos.core.shared.TemplateId;

/** Admin only (ECO-08): a deposit of template {@code template}, its corner at match-local cell ({@code x}, {@code z}). */
public record SpawnDeposit(TemplateId template, int x, int z) implements Command {
    static final MapCodec<SpawnDeposit> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                    TemplateId.CODEC.fieldOf("template").forGetter(SpawnDeposit::template),
                    Codec.INT.fieldOf("x").forGetter(SpawnDeposit::x),
                    Codec.INT.fieldOf("z").forGetter(SpawnDeposit::z))
            .apply(i, SpawnDeposit::new));

    /** The template id, the corner; wire id 24. */
    static final WireCodec<SpawnDeposit> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, SpawnDeposit value) {
            WireCodecs.TEMPLATE_ID.write(out, value.template());
            out.writeVarInt(value.x()).writeVarInt(value.z());
        }

        @Override
        public SpawnDeposit read(WireReader in) {
            return new SpawnDeposit(WireCodecs.TEMPLATE_ID.read(in), in.readVarInt(), in.readVarInt());
        }

        @Override
        public int maxSize() {
            return WireCodecs.TEMPLATE_ID.maxSize() + 10;
        }
    };

    @Override
    public void hashInto(Hasher h) {
        h.putByte((byte) 24); // the command type: ... 19 Build, 24 SpawnDeposit
        h.putString(template.value()).putInt(x).putInt(z);
    }
}
