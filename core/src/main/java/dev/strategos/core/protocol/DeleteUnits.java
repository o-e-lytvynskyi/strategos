package dev.strategos.core.protocol;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.Hasher;

/**
 * Delete own {@code units} (UI-17): each dies at once, as a unit that lost its hit points does but with no killer: no
 * loot goes to anyone, and what it carried is lost with it. The client asks the player to confirm before it sends.
 */
public record DeleteUnits(UnitSet units) implements Command {
    static final MapCodec<DeleteUnits> CODEC = RecordCodecBuilder.mapCodec(
            i -> i.group(UnitSet.CODEC.fieldOf("units").forGetter(DeleteUnits::units))
                    .apply(i, DeleteUnits::new));

    /** The units; wire id 39. */
    static final WireCodec<DeleteUnits> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, DeleteUnits value) {
            WireCodecs.UNIT_SET.write(out, value.units());
        }

        @Override
        public DeleteUnits read(WireReader in) {
            return new DeleteUnits(WireCodecs.UNIT_SET.read(in));
        }

        @Override
        public int maxSize() {
            return WireCodecs.UNIT_SET.maxSize();
        }
    };

    @Override
    public void hashInto(Hasher h) {
        h.putByte((byte) 39); // the command type: ... 37 Guard, 38 FormationMove, 39 DeleteUnits
        units.hashInto(h);
    }
}
