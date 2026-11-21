package dev.strategos.core.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mojang.serialization.JsonOps;
import dev.strategos.core.shared.PlayerSlot;
import org.junit.jupiter.api.Test;

class ControlGrantWireTest {
    @Test
    void boundedWireAndJsonPreserveEveryRecipientAndBothActions() {
        for (int slot = -1; slot <= 15; slot++)
            for (boolean granted : new boolean[] {false, true}) {
                var command = new SetControl(new PlayerSlot(slot), granted);
                WireWriter out = new WireWriter();
                CommandKinds.DEFAULT.wireCodec().write(out, command);
                assertThat(out.toByteArray()).hasSize(4);
                assertThat(CommandKinds.DEFAULT.wireCodec().read(new WireReader(out.toByteArray())))
                        .isEqualTo(command);
                var json = CommandKinds.DEFAULT
                        .codec()
                        .encodeStart(JsonOps.INSTANCE, command)
                        .getOrThrow();
                assertThat(CommandKinds.DEFAULT
                                .codec()
                                .parse(JsonOps.INSTANCE, json)
                                .getOrThrow())
                        .isEqualTo(command);
            }
        assertThat(CommandKinds.SET_CONTROL.wireId()).isEqualTo(32);
        assertThat(CommandKinds.SET_CONTROL.adminOnly()).isFalse();
    }

    @Test
    void truncatedAndMalformedRecipientAndBooleanBytesFailClosed() {
        assertThatThrownBy(() -> SetControl.WIRE.read(new WireReader(new byte[] {2})))
                .isInstanceOf(WireException.class);
        assertThatThrownBy(() -> SetControl.WIRE.read(new WireReader(new byte[] {34, 0})))
                .isInstanceOf(WireException.class);
        assertThatThrownBy(() -> SetControl.WIRE.read(new WireReader(new byte[] {2, 2})))
                .isInstanceOf(WireException.class);
    }
}
