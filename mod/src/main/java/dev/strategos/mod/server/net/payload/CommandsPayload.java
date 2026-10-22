package dev.strategos.mod.server.net.payload;

import dev.strategos.core.protocol.CommandBatch;
import dev.strategos.core.protocol.Limits;
import dev.strategos.core.protocol.WireCodec;
import dev.strategos.core.protocol.WireException;
import dev.strategos.mod.platform.Ids;
import dev.strategos.mod.server.net.PayloadSpec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jspecify.annotations.Nullable;

/**
 * {@code strategos:commands}, client to server: a batch of commands. It is read leniently: a batch that is malformed or
 * above {@link Limits#BATCH_BYTES} arrives as a refusal with its reason, for the server to count as a violation; a
 * decoding error would disconnect the client at once instead (spec §6.5).
 */
public record CommandsPayload(@Nullable CommandBatch batch, String problem) implements CustomPacketPayload {
    public static final Type<CommandsPayload> TYPE = new Type<>(Ids.id("commands"));
    public static final PayloadSpec SPEC = PayloadSpec.toServer(TYPE.id(), Limits.BATCH_BYTES);
    public static final StreamCodec<RegistryFriendlyByteBuf, CommandsPayload> STREAM_CODEC =
            StreamCodec.of(CommandsPayload::write, CommandsPayload::read);

    public static CommandsPayload of(CommandBatch batch) {
        return new CommandsPayload(batch, "");
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
        buf.writeBytes(WireCodec.encode(CommandBatch.WIRE, batch));
    }

    /** Every byte left is the batch's; a refusal never leaves one unread. */
    static CommandsPayload read(RegistryFriendlyByteBuf buf) {
        int n = buf.readableBytes();
        if (n > Limits.BATCH_BYTES) {
            buf.skipBytes(n);
            return new CommandsPayload(null, n + " bytes above the batch's " + Limits.BATCH_BYTES);
        }
        byte[] bytes = new byte[n];
        buf.readBytes(bytes);
        try {
            return of(WireCodec.decode(CommandBatch.WIRE, bytes));
        } catch (WireException e) {
            return new CommandsPayload(null, String.valueOf(e.getMessage()));
        }
    }
}
