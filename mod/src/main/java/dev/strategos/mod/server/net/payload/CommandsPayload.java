package dev.strategos.mod.server.net.payload;

import dev.strategos.core.protocol.CommandBatch;
import dev.strategos.core.protocol.Limits;
import dev.strategos.core.protocol.WireCodec;
import dev.strategos.core.protocol.WireException;
import dev.strategos.mod.platform.Ids;
import dev.strategos.mod.server.net.MatchScopedPayload;
import dev.strategos.mod.server.net.PayloadSpec;
import dev.strategos.mod.server.net.SessionEnvelope;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.Nullable;

/**
 * {@code strategos:commands}, client to server: a batch of commands. It is read leniently: a batch that is malformed or
 * whose body exceeds {@link Limits#BATCH_BYTES} arrives as a refusal with its reason, for the server to count as a violation; a
 * decoding error would disconnect the client at once instead (spec §6.5). The fixed epoch header adds exactly eight bytes.
 */
public record CommandsPayload(long epoch, @Nullable CommandBatch batch, String problem) implements MatchScopedPayload {
    public CommandsPayload(@Nullable CommandBatch batch, String problem) {
        this(0, batch, problem);
    }

    private static final WireCodec<SessionEnvelope<CommandBatch>> WIRE = SessionEnvelope.codec(CommandBatch.WIRE);
    public static final Type<CommandsPayload> TYPE = new Type<>(Ids.id("commands"));
    public static final PayloadSpec SPEC = PayloadSpec.toServer(TYPE.id(), WIRE.maxSize());
    public static final StreamCodec<RegistryFriendlyByteBuf, CommandsPayload> STREAM_CODEC =
            StreamCodec.of(CommandsPayload::write, CommandsPayload::read);

    public static CommandsPayload of(CommandBatch batch) {
        return of(batch, 0);
    }

    public static CommandsPayload of(CommandBatch batch, long epoch) {
        return new CommandsPayload(epoch, batch, "");
    }

    @Override
    public boolean decoded() {
        return batch != null;
    }

    @Override
    public CommandsPayload withEpoch(long epoch) {
        return new CommandsPayload(epoch, batch, problem);
    }

    /** Whether the batch was read; otherwise {@link #problem()} says why not. */
    public boolean ok() {
        return batch != null;
    }

    @Override
    public Type<CommandsPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buf, CommandsPayload payload) {
        CommandBatch batch = payload.batch();
        if (batch == null) {
            throw new IllegalStateException("a refused batch is never sent: " + payload.problem());
        }
        buf.writeBytes(WireCodec.encode(WIRE, new SessionEnvelope<>(payload.epoch(), batch)));
    }

    /** Every byte left is the batch's; a refusal never leaves one unread. */
    static CommandsPayload read(RegistryFriendlyByteBuf buf) {
        int n = buf.readableBytes();
        if (n > WIRE.maxSize()) {
            buf.skipBytes(n);
            return new CommandsPayload(null, n + " bytes above the framed batch's " + WIRE.maxSize());
        }
        byte[] bytes = new byte[n];
        buf.readBytes(bytes);
        try {
            var frame = WireCodec.decode(WIRE, bytes);
            return of(frame.body(), frame.epoch());
        } catch (WireException | IllegalArgumentException e) {
            return new CommandsPayload(null, String.valueOf(e.getMessage()));
        }
    }
}
