package dev.strategos.core.protocol;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Hasher;

/**
 * Admin only (ECO-01): gives a faction {@code amount} whole units of resource {@code resource}, as a scenario's or a
 * sandbox's starting stock; counted as granted in its treasury.
 */
public record Grant(FactionSlot faction, String resource, long amount) implements Command {
    /** The most one grant gives: a billion units. */
    public static final long MAX = 1_000_000_000L;

    static final MapCodec<Grant> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                    FactionSlot.CODEC.fieldOf("faction").forGetter(Grant::faction),
                    Codec.STRING.fieldOf("resource").forGetter(Grant::resource),
                    Codec.LONG.fieldOf("amount").forGetter(Grant::amount))
            .apply(i, Grant::new));

    /** Faction, resource id, amount; wire id 7. */
    static final WireCodec<Grant> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, Grant value) {
            WireCodecs.FACTION_SLOT.write(out, value.faction());
            out.writeString(value.resource(), RESOURCE_BYTES);
            out.writeVarLong(value.amount());
        }

        @Override
        public Grant read(WireReader in) {
            FactionSlot faction = WireCodecs.FACTION_SLOT.read(in);
            String resource = in.readString(RESOURCE_BYTES);
            return new Grant(faction, resource, in.readVarLong());
        }

        @Override
        public int maxSize() {
            return WireCodecs.FACTION_SLOT.maxSize() + 5 + RESOURCE_BYTES + 10;
        }
    };

    /** A resource id's most bytes on the wire. */
    static final int RESOURCE_BYTES = 32;

    @Override
    public void hashInto(Hasher h) {
        h.putByte(
                (byte) 7); // the command type: 1 Move, 2 Stop, 3 SpawnUnits, 4 Attack, 5 AttackMove, 6 Follow, 7 Grant
        h.putInt(faction.value()).putString(resource).putLong(amount);
    }
}
