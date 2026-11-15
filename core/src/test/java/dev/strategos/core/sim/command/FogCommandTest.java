package dev.strategos.core.sim.command;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.testkit.SimHarness;
import org.junit.jupiter.api.Test;

/** FOW-01: a command cannot tell a hidden enemy from a handle that never existed. */
class FogCommandTest {
    private final SimHarness h = SimHarness.builder().size(384, 384).factions(2).build();
    private final MatchState state = KernelAccess.state(h.core());

    @Test
    void hiddenIsUnknownTargetVisibleIsNotOwner() {
        h.spawn(new FactionSlot(0), new Vec2d(50, 50), 2, 2.0);
        int enemy = h.spawn(new FactionSlot(1), new Vec2d(300, 300), 1, 0.0)[0];
        PlayerSlot player = new PlayerSlot(0);

        assertThat(Ownership.check(player, enemy, state)).isEqualTo(RejectReason.UNKNOWN_TARGET);

        int i = Handle.index(enemy);
        state.motion().set(i, 54, state.motion().y(i), 50, 0);
        h.step();

        assertThat(Ownership.check(player, enemy, state)).isEqualTo(RejectReason.NOT_OWNER);
        state.motion().set(i, 300, state.motion().y(i), 300, 0);
        for (int k = 0; k < 11; k++) {
            h.step();
        }
        assertThat(Ownership.check(PlayerSlot.ADMIN, enemy, state)).isNull();
    }
}
