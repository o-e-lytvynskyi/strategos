package dev.strategos.mod.server.net;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.AllClear;
import dev.strategos.core.protocol.AttackSpread;
import dev.strategos.core.protocol.Garrison;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.RingBell;
import dev.strategos.core.protocol.SetStance;
import dev.strategos.core.protocol.Ungarrison;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.Stance;
import org.junit.jupiter.api.Test;

/** The rate limiter counts the units of M5's unit-set commands as it counts a move's (security.md). */
class CommandHandlesTest {
    @Test
    void theUnitSetCommandsCountTheirUnits() {
        UnitSet three = UnitSet.of(1, 2, 3);
        assertThat(NetGateway.handles(new AttackSpread(three, 9, QueueMode.REPLACE)))
                .isEqualTo(3);
        assertThat(NetGateway.handles(new SetStance(three, Stance.DEFENSIVE))).isEqualTo(3);
        assertThat(NetGateway.handles(new Garrison(three, 0x80001))).isEqualTo(3);
        assertThat(NetGateway.handles(new Ungarrison(0x80001, three))).isEqualTo(3);
        assertThat(NetGateway.handles(new Ungarrison(0x80001, UnitSet.of())))
                .as("everyone inside counts as a full centre")
                .isEqualTo(20);
        assertThat(NetGateway.handles(new RingBell(0x80001)))
                .as("a bell calls up to a full centre of workers")
                .isEqualTo(20);
        assertThat(NetGateway.handles(new AllClear(0x80001))).isEqualTo(20);
    }
}
