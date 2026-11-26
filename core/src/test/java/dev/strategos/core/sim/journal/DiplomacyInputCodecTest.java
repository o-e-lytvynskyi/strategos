package dev.strategos.core.sim.journal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.protocol.WireException;
import dev.strategos.core.protocol.WireReader;
import dev.strategos.core.protocol.WireWriter;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.sim.DiplomacySetup;
import dev.strategos.core.sim.SliceSource;
import dev.strategos.core.sim.diplomacy.DiplomacyState;
import org.junit.jupiter.api.Test;

class DiplomacyInputCodecTest {
    @Test
    void setupHasItsOwnAppendOnlyTagAndRoundTripsWithoutLosingAuthority() {
        var state = DiplomacyState.identity(2)
                .withTeam(new FactionSlot(1), 0)
                .grant(new PlayerSlot(0), new PlayerSlot(1))
                .lockTeams();
        DiplomacySetup input = new DiplomacySetup(state.snapshot());
        WireWriter out = new WireWriter(256);
        InputCodec.write(out, input);
        byte[] bytes = out.toByteArray();
        assertThat(Byte.toUnsignedInt(bytes[0])).isEqualTo(9);
        assertThat(InputCodec.read(new WireReader(bytes), SliceSource.NONE)).isEqualTo(input);
    }

    @Test
    void oversizedOrEmptyFactionListsAreRejectedBeforeAnUnboundedAllocation() {
        for (int count : new int[] {0, 17, Integer.MAX_VALUE}) {
            WireWriter out = new WireWriter(16);
            out.writeByte(9).writeVarInt(count);
            assertThatThrownBy(() -> InputCodec.read(new WireReader(out.toByteArray()), SliceSource.NONE))
                    .isInstanceOf(WireException.class);
        }
    }
}
