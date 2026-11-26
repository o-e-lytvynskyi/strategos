package dev.strategos.core.protocol;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.Hasher;
import dev.strategos.core.shared.PlayerSlot;

/** Grant/revoke the issuer's primary faction, never an inherited grant. Rules validate the participants. */
public record SetControl(PlayerSlot recipient, boolean granted) implements Command {
    static final MapCodec<SetControl> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                    PlayerSlot.CODEC.fieldOf("recipient").forGetter(SetControl::recipient),
                    Codec.BOOL.fieldOf("granted").forGetter(SetControl::granted))
            .apply(i, SetControl::new));

    /** One bounded player slot and one canonical boolean; appended wire id 32. */
    static final WireCodec<SetControl> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, SetControl value) {
            WireCodecs.PLAYER_SLOT.write(out, value.recipient());
            out.writeBoolean(value.granted());
        }

        @Override
        public SetControl read(WireReader in) {
            return new SetControl(WireCodecs.PLAYER_SLOT.read(in), in.readBoolean());
        }

        @Override
        public int maxSize() {
            return 2;
        }
    };

    @Override
    public void hashInto(Hasher h) {
        h.putByte((byte) 32).putInt(recipient.value()).putBoolean(granted);
    }
}
