package dev.strategos.core.protocol;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.Hasher;
import dev.strategos.core.shared.Stance;

/** Set the {@code stance} of own {@code units} (CMB-05): how they fight on their own, from where they stand now. */
public record SetStance(UnitSet units, Stance stance) implements Command {
    private static final Codec<Stance> STANCE_CODEC = Codec.STRING.comapFlatMap(
            id -> {
                Stance stance = Stance.byId(id);
                return stance == null ? DataResult.error(() -> "no stance '" + id + "'") : DataResult.success(stance);
            },
            Stance::id);

    static final MapCodec<SetStance> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                    UnitSet.CODEC.fieldOf("units").forGetter(SetStance::units),
                    STANCE_CODEC.fieldOf("stance").forGetter(SetStance::stance))
            .apply(i, SetStance::new));

    /** The units, then the stance's code; wire id 25. */
    static final WireCodec<SetStance> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, SetStance value) {
            WireCodecs.UNIT_SET.write(out, value.units());
            out.writeByte(value.stance().code());
        }

        @Override
        public SetStance read(WireReader in) {
            UnitSet units = WireCodecs.UNIT_SET.read(in);
            int code = in.readUnsignedByte();
            Stance stance = Stance.of(code);
            if (stance == null) {
                throw new WireException("no stance has code " + code);
            }
            return new SetStance(units, stance);
        }

        @Override
        public int maxSize() {
            return WireCodecs.UNIT_SET.maxSize() + 1;
        }
    };

    @Override
    public void hashInto(Hasher h) {
        h.putByte((byte) 25); // the command type: ... 24 SpawnDeposit, 25 SetStance
        units.hashInto(h);
        h.putByte((byte) stance.code());
    }
}
