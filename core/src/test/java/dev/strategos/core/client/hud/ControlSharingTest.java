package dev.strategos.core.client.hud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.protocol.FactionPresentation;
import dev.strategos.core.protocol.MatchJoin;
import dev.strategos.core.protocol.MatchPresentation;
import dev.strategos.core.protocol.PlayerAuthority;
import dev.strategos.core.protocol.SetControl;
import dev.strategos.core.shared.PlayerSlot;
import java.util.List;
import org.junit.jupiter.api.Test;

class ControlSharingTest {
    private static MatchJoin joined(PlayerSlot player) {
        var authority = player.isAdmin()
                ? new PlayerAuthority(7, player, 5, -1, 0, 0)
                : new PlayerAuthority(7, player, 5, 0, 1, 3);
        return new MatchJoin(7, 0, 0, 0, 48, 48, 0, 32, 3, player, "{}", authority);
    }

    private static MatchPresentation publicView(int match) {
        return new MatchPresentation(
                match,
                1,
                FactionPresentation.legacy(3),
                List.of(
                        new MatchPresentation.Participant(new PlayerSlot(0), 0, "host", true),
                        new MatchPresentation.Participant(new PlayerSlot(1), 0, "coop", true),
                        new MatchPresentation.Participant(new PlayerSlot(2), 1, "ally", true),
                        new MatchPresentation.Participant(new PlayerSlot(3), 2, "enemy", true),
                        new MatchPresentation.Participant(new PlayerSlot(4), 1, "offline", false)));
    }

    @Test
    void onlyConnectedNamedDistinctPrimaryAlliesAreOffered() {
        var model = ControlSharing.of(joined(new PlayerSlot(1)), publicView(7), true);
        assertThat(model.rows()).extracting(ControlSharing.Row::name).containsExactly("ally");
        assertThat(model.rows()).extracting(r -> r.player().value()).containsExactly(2);
        assertThat(model.rows()).extracting(ControlSharing.Row::faction).containsExactly(1);
    }

    @Test
    void explicitGrantAndRevokeUseTheNormalTypedCommandWithoutGuessingOutgoingRights() {
        var model = ControlSharing.of(joined(new PlayerSlot(0)), publicView(7), true);
        assertThat(model.intent(new PlayerSlot(2), true)).isEqualTo(new SetControl(new PlayerSlot(2), true));
        assertThat(model.intent(new PlayerSlot(2), false)).isEqualTo(new SetControl(new PlayerSlot(2), false));
        for (int excluded : new int[] {0, 1, 3, 4, 15})
            assertThatThrownBy(() -> model.intent(new PlayerSlot(excluded), true))
                    .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void observerEndedMissingAndForeignContextsCannotOfferCommandActions() {
        assertThat(ControlSharing.of(joined(PlayerSlot.ADMIN), publicView(7), true)
                        .rows())
                .isEmpty();
        assertThat(ControlSharing.of(joined(new PlayerSlot(0)), publicView(7), false)
                        .rows())
                .isEmpty();
        assertThat(ControlSharing.of(joined(new PlayerSlot(0)), null, true).rows())
                .isEmpty();
        assertThat(ControlSharing.of(joined(new PlayerSlot(0)), publicView(8), true)
                        .rows())
                .isEmpty();
        assertThat(ControlSharing.of(
                                joined(new PlayerSlot(0)),
                                new MatchPresentation(
                                        7,
                                        1,
                                        FactionPresentation.legacy(3),
                                        List.of(new MatchPresentation.Participant(new PlayerSlot(2), 1, "ally", true))),
                                true)
                        .rows())
                .isEmpty();
    }
}
