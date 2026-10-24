package dev.strategos.core.protocol;

import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Hasher;

/** Faction {@code faction} resigns (MATCH-01): it is defeated at once; a player resigns its own, an admin any. */
public record Resign(FactionSlot faction) implements Command {
    static final com.mojang.serialization.MapCodec<Resign> CODEC =
            FactionSlot.CODEC.fieldOf("faction").xmap(Resign::new, Resign::faction);

    /** The faction; wire id 15. */
    static final WireCodec<Resign> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, Resign value) {
            WireCodecs.FACTION_SLOT.write(out, value.faction());
        }

        @Override
        public Resign read(WireReader in) {
            return new Resign(WireCodecs.FACTION_SLOT.read(in));
        }

        @Override
        public int maxSize() {
            return WireCodecs.FACTION_SLOT.maxSize();
        }
    };

    @Override
    public void hashInto(Hasher h) {
        h.putByte((byte) 15); // the command type: ... 14 Reseed, 15 Resign
        h.putInt(faction.value());
    }
}
