package dev.strategos.core.sim.journal;

import dev.strategos.core.protocol.WireCodec;
import dev.strategos.core.protocol.WireException;
import dev.strategos.core.protocol.WireReader;
import dev.strategos.core.protocol.WireWriter;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.InputEvent;

/** Every journaled input as a one-byte kind and its body: 1 a command envelope; later kinds arrive with their inputs. */
final class InputCodec implements WireCodec<InputEvent> {
    static final InputCodec INSTANCE = new InputCodec();

    private static final int COMMAND = 1;

    private InputCodec() {}

    @Override
    public void write(WireWriter out, InputEvent value) {
        switch (value) {
            case CommandEnvelope envelope -> {
                out.writeByte(COMMAND);
                CommandEnvelope.WIRE.write(out, envelope);
            }
        }
    }

    @Override
    public InputEvent read(WireReader in) {
        int kind = in.readUnsignedByte();
        return switch (kind) {
            case COMMAND -> CommandEnvelope.WIRE.read(in);
            default -> throw new WireException("unknown input kind " + kind);
        };
    }

    @Override
    public int maxSize() {
        return 1 + CommandEnvelope.WIRE.maxSize();
    }
}
