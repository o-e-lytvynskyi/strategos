package dev.strategos.core.sim.journal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.protocol.WireException;
import dev.strategos.core.protocol.WireReader;
import dev.strategos.core.protocol.WireWriter;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.sim.FactionExpiry;
import dev.strategos.core.sim.SliceSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FactionExpiryInputCodecTest {
    @ParameterizedTest
    @ValueSource(ints = {0, 15})
    void aNewBoundedTagNeverReusesAnExistingInputMeaning(int faction) {
        var event = new FactionExpiry(new FactionSlot(faction));
        var out = new WireWriter(16);
        InputCodec.write(out, event);
        assertThat(out.toByteArray()).containsExactly((byte) 12, (byte) faction);
        assertThat(InputCodec.read(new WireReader(out.toByteArray()), SliceSource.NONE))
                .isEqualTo(event);
    }

    @Test
    void truncatedOrOutOfRangeExpiryDoesNotBecomeAnotherInput() {
        for (byte[] bytes : new byte[][] {{12}, {12, 16}, {12, (byte) 255, (byte) 255, 127}})
            assertThatThrownBy(() -> InputCodec.read(new WireReader(bytes), SliceSource.NONE))
                    .isInstanceOf(WireException.class);
    }
}
