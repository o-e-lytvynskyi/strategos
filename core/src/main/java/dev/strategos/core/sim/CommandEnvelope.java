package dev.strategos.core.sim;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.CommandKinds;
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

    @Override
    public void hashInto(Hasher h) {
        h.putLong(receivedTick).putInt(issuer.value()).putInt(clientSeq);
        command.hashInto(h);
    }
}
