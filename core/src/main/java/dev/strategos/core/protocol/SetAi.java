package dev.strategos.core.protocol;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Hasher;

/**
 * Admin only (AI-01, AI-17): faction {@code faction} is played by the computer, profile {@code profile}, by strategy
 * {@code script} (its JSON, carried in the command so the journal holds what the computer played); an empty script
 * hands the faction back.
 */
public record SetAi(FactionSlot faction, String profile, String script) implements Command {
    /** The most bytes of a profile, and of a script. */
    public static final int PROFILE_BYTES = 64;

    public static final int SCRIPT_BYTES = 3_000;

    static final MapCodec<SetAi> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                    FactionSlot.CODEC.fieldOf("faction").forGetter(SetAi::faction),
                    Codec.STRING.fieldOf("profile").forGetter(SetAi::profile),
                    Codec.STRING.fieldOf("script").forGetter(SetAi::script))
            .apply(i, SetAi::new));

    /** Faction, profile, script; wire id 18. */
    static final WireCodec<SetAi> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, SetAi value) {
            WireCodecs.FACTION_SLOT.write(out, value.faction());
            out.writeString(value.profile(), PROFILE_BYTES);
            out.writeString(value.script(), SCRIPT_BYTES);
        }

        @Override
        public SetAi read(WireReader in) {
            FactionSlot faction = WireCodecs.FACTION_SLOT.read(in);
            String profile = in.readString(PROFILE_BYTES);
            return new SetAi(faction, profile, in.readString(SCRIPT_BYTES));
        }

        @Override
        public int maxSize() {
            return WireCodecs.FACTION_SLOT.maxSize() + 2 * 5 + PROFILE_BYTES + SCRIPT_BYTES;
        }
    };

    @Override
    public void hashInto(Hasher h) {
        h.putByte((byte) 18); // the command type: ... 17 AnnounceWave, 18 SetAi
        h.putInt(faction.value()).putString(profile).putString(script);
    }
}
