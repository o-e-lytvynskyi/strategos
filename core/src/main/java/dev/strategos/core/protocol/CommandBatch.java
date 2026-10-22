package dev.strategos.core.protocol;

import dev.strategos.core.shared.CoreApi;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@code commands} payload (04 §7.3): at most {@link Limits#BATCH_COMMANDS} commands from one client, the first
 * numbered {@code clientSeq} by the client and each next one more, in at most {@link Limits#BATCH_BYTES} bytes.
 */
@CoreApi
public record CommandBatch(int clientSeq, List<Command> commands) {
    /** A varint sequence number, a one-byte count, then each command with its wire id. */
    public static final WireCodec<CommandBatch> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, CommandBatch value) {
            out.writeVarInt(value.clientSeq());
            out.writeVarInt(value.commands().size());
            for (Command command : value.commands()) {
                CommandKinds.DEFAULT.wireCodec().write(out, command);
            }
        }

        @Override
        public CommandBatch read(WireReader in) {
            int seq = in.readVarInt();
            int count = in.readVarInt(Limits.BATCH_COMMANDS);
            List<Command> commands = new ArrayList<>(count);
            for (int k = 0; k < count; k++) {
                commands.add(CommandKinds.DEFAULT.wireCodec().read(in));
            }
            try {
                return new CommandBatch(seq, commands);
            } catch (IllegalArgumentException e) {
                throw new WireException(String.valueOf(e.getMessage()));
            }
        }

        @Override
        public int maxSize() {
            return Limits.BATCH_BYTES;
        }
    };

    public CommandBatch {
        if (clientSeq < 0) {
            throw new IllegalArgumentException("client sequence " + clientSeq + " below 0");
        }
        if (commands.isEmpty() || commands.size() > Limits.BATCH_COMMANDS) {
            throw new IllegalArgumentException(
                    commands.size() + " commands in a batch, not 1 to " + Limits.BATCH_COMMANDS);
        }
        commands = List.copyOf(commands);
    }
}
