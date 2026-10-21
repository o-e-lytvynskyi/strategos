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

    @Override
    public void hashInto(Hasher h) {
        h.putByte((byte) 3); // the command type: 1 Move, 2 Stop, 3 SpawnUnits
        h.putString(template.value()).putInt(faction.value());
        h.putDouble(center.x()).putDouble(center.z()).putInt(count).putDouble(spread);
    }
}
