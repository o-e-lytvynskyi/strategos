package dev.strategos.mod.client.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.protocol.SkirmishSetup;
import dev.strategos.mod.server.net.LobbyConfiguration;
import dev.strategos.mod.server.net.LobbyState;
import java.util.List;
import org.junit.jupiter.api.Test;

class LobbyCoopConfigurationTest {
    private static LobbyScreenModel model(boolean host, LobbyState.Phase phase) {
        return new LobbyScreenModel(new LobbyState(
                new LobbyConfiguration(
                        4,
                        List.of(
                                new LobbyConfiguration.Seat(
                                        0, LobbyConfiguration.Kind.HUMAN, 0, 3, "strategos:placeholder", 2),
                                new LobbyConfiguration.Seat(
                                        1, LobbyConfiguration.Kind.HUMAN, 1, 1, "strategos:placeholder", 0),
                                new LobbyConfiguration.Seat(
                                        2, LobbyConfiguration.Kind.AI, 2, 2, "strategos:placeholder", 1)),
                        SkirmishSetup.DEFAULTS),
                List.of(new LobbyState.Member(0, "host", false, true), new LobbyState.Member(1, "peer", false, false)),
                0,
                host,
                9,
                7,
                phase,
                ""));
    }

    @Test
    void joiningAHumanFactionCopiesItsSettingsAtomicallyAndPreservesPlayerIdentity() {
        var model = model(true, LobbyState.Phase.EDITING);
        var joined = model.factionIntent(1, 0).configuration();
        var peer = joined.seats().get(1);
        assertThat(peer.player()).isEqualTo(1);
        assertThat(peer.faction()).isZero();
        assertThat(peer.team()).isEqualTo(3);
        assertThat(peer.colour()).isEqualTo(2);
        assertThat(peer.civilisation()).isEqualTo(joined.seats().getFirst().civilisation());
        assertThat(model.state().configuration().seats().get(1).faction()).isEqualTo(1);
        assertThat(joined.setup()).isEqualTo(model.state().configuration().setup());
    }

    @Test
    void splittingCoopUsesAFreeColourWithoutChangingTheRemainingPrimary() {
        var original = model(true, LobbyState.Phase.EDITING);
        var joined = original.factionIntent(1, 0).configuration();
        var state = original.state();
        var coop = new LobbyScreenModel(new LobbyState(
                joined,
                state.members(),
                state.self(),
                true,
                state.revision(),
                state.configurationRevision(),
                state.phase(),
                ""));
        var split = coop.factionIntent(1, 3).configuration();
        assertThat(split.seats().get(0)).isEqualTo(joined.seats().get(0));
        assertThat(split.seats().get(1).faction()).isEqualTo(3);
        assertThat(split.seats().get(1).team()).isEqualTo(3);
        assertThat(split.seats().get(1).colour()).isZero();
        assertThat(split.seats().get(2)).isEqualTo(joined.seats().get(2));
    }

    @Test
    void aiFactionsAndFrozenOrUnauthorizedEditsAreUnavailable() {
        var editable = model(true, LobbyState.Phase.EDITING);
        assertThat(editable.factionsFor(1)).containsExactly(0, 1, 3);
        assertThatThrownBy(() -> editable.factionIntent(1, 2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> editable.factionIntent(2, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> editable.factionIntent(1, 4)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> editable.factionIntent(99, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> model(false, LobbyState.Phase.EDITING).factionIntent(1, 0))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> model(true, LobbyState.Phase.PREPARING).factionIntent(1, 0))
                .isInstanceOf(IllegalStateException.class);
    }
}
