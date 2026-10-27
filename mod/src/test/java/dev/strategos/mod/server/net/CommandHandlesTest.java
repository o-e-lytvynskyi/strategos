package dev.strategos.mod.server.net;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.AttackSpread;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.SetStance;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.Stance;
import org.junit.jupiter.api.Test;

/** The rate limiter counts the units of M5's unit-set commands as it counts a move's (security.md). */
class CommandHandlesTest {
    @Test
    void theSpreadAndTheStanceCountTheirUnits() {
        UnitSet three = UnitSet.of(1, 2, 3);
        assertThat(NetGateway.handles(new AttackSpread(three, 9, QueueMode.REPLACE)))
                .isEqualTo(3);
        assertThat(NetGateway.handles(new SetStance(three, Stance.DEFENSIVE))).isEqualTo(3);
    }
}
