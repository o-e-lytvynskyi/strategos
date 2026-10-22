package dev.strategos.core.sim;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.CommandKinds;
import dev.strategos.core.protocol.WireCodec;
import dev.strategos.core.protocol.WireCodecs;
import dev.strategos.core.protocol.WireReader;
import dev.strategos.core.protocol.WireWriter;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.Hashable;
import dev.strategos.core.shared.Hasher;
import dev.strategos.core.shared.PlayerSlot;
import java.util.Comparator;

/** A command as received: who sent it, their sequence number and the tick it arrived in. */
@CoreApi
public record CommandEnvelope(PlayerSlot issuer, int clientSeq, long receivedTick, Command command)
        implements InputEvent, Hashable {
    /** The order commands apply in: by tick received, then issuer, then the issuer's sequence. */
    public static final Comparator<CommandEnvelope> CANONICAL = Comparator.comparingLong(CommandEnvelope::receivedTick)
            .thenComparingInt(e -> e.issuer().value())
            .thenComparingInt(CommandEnvelope::clientSeq);

    public static final Codec<CommandEnvelope> CODEC = RecordCodecBuilder.create(i -> i.group(
                    PlayerSlot.CODEC.fieldOf("issuer").forGetter(CommandEnvelope::issuer),
                    Codec.INT.fieldOf("clientSeq").forGetter(CommandEnvelope::clientSeq),
                    Codec.LONG.fieldOf("receivedTick").forGetter(CommandEnvelope::receivedTick),
                    CommandKinds.DEFAULT.codec().fieldOf("command").forGetter(CommandEnvelope::command))
            .apply(i, CommandEnvelope::new));

    /** Issuer, sequence, tick received and the command, as the journal stores it. */
    public static final WireCodec<CommandEnvelope> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, CommandEnvelope value) {
            WireCodecs.PLAYER_SLOT.write(out, value.issuer());
            out.writeZigZag(value.clientSeq());
            out.writeZigZag(value.receivedTick());
            CommandKinds.DEFAULT.wireCodec().write(out, value.command());
        }

        @Override
        public CommandEnvelope read(WireReader in) {
            return new CommandEnvelope(
                    WireCodecs.PLAYER_SLOT.read(in),
                    in.readZigZagInt(),
                    in.readZigZagLong(),
                    CommandKinds.DEFAULT.wireCodec().read(in));
        }

        @Override
        public int maxSize() {
            return WireCodecs.PLAYER_SLOT.maxSize()
                    + 5
                    + 10
                    + CommandKinds.DEFAULT.wireCodec().maxSize();
        }
    };

    @Override
    public void hashInto(Hasher h) {
        h.putLong(receivedTick).putInt(issuer.value()).putInt(clientSeq);
        command.hashInto(h);
    }
}
