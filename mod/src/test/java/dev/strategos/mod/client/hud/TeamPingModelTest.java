package dev.strategos.mod.client.hud;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.MatchJoin;
import dev.strategos.core.protocol.PlayerAuthority;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.mod.server.net.payload.TeamPingPayload;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class TeamPingModelTest {
    @Test
    void markerExpiresByClientTicksAndARepeatReplacesThatPlayersMarker() {
        var markers = new TeamPingModel();
        markers.join(7, joined(1, 3, 1));
        assertThat(markers.accept(ping(7, 1, 1, 4))).isTrue();
        IntStream.range(0, 99).forEach(i -> markers.tick());
        assertThat(markers.active()).hasSize(1);
        assertThat(markers.accept(ping(7, 1, 1, 8))).isTrue();
        IntStream.range(0, 99).forEach(i -> markers.tick());
        assertThat(markers.active()).containsExactly(ping(7, 1, 1, 8).ping());
        markers.tick();
        assertThat(markers.active()).isEmpty();
    }

    @Test
    void wrongEpochMatchAndBoundsCannotPopulateTheCurrentView() {
        var markers = new TeamPingModel();
        markers.join(7, joined(1, 3, 1));
        assertThat(markers.accept(ping(6, 1, 1, 4))).isFalse();
        assertThat(markers.accept(ping(7, 2, 1, 4))).isFalse();
        assertThat(markers.accept(ping(7, 1, 1, 48))).isFalse();
        assertThat(markers.active()).isEmpty();
    }

    @Test
    void teamAndSessionChangesClearMarkersWhileGrantChangesKeepThem() {
        var markers = new TeamPingModel();
        markers.join(7, joined(1, 3, 1));
        assertThat(markers.accept(ping(7, 1, 1, 4))).isTrue();
        markers.authority(joined(1, 3, 3).authority());
        assertThat(markers.active()).hasSize(1);
        markers.authority(joined(1, 1, 1).authority());
        assertThat(markers.active()).isEmpty();
        assertThat(markers.accept(ping(7, 1, 0, 5))).isTrue();
        markers.join(8, joined(1, 1, 1));
        assertThat(markers.active()).isEmpty();
        assertThat(markers.accept(ping(7, 1, 0, 5))).isFalse();
        assertThat(markers.accept(ping(8, 1, 0, 5))).isTrue();
        markers.clear();
        assertThat(markers.active()).isEmpty();
        assertThat(markers.accept(ping(8, 1, 0, 5))).isFalse();
    }

    private static MatchJoin joined(int match, int allies, int control) {
        var player = new PlayerSlot(0);
        return new MatchJoin(
                match,
                100,
                2,
                100,
                48,
                48,
                0,
                25,
                2,
                player,
                "{}",
                new PlayerAuthority(match, player, 0, 0, control, allies));
    }

    private static TeamPingPayload ping(long epoch, int match, int player, int x) {
        return new TeamPingPayload(epoch, new TeamPingPayload.Ping(match, new PlayerSlot(player), x, 7));
    }
}
