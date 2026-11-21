package dev.strategos.core.client.model;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.FactionPresentation;
import dev.strategos.core.protocol.MatchPresentation;
import dev.strategos.core.shared.PlayerSlot;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClientPresentationTest {
    private static MatchPresentation frame(int match, long revision, int colour) {
        return new MatchPresentation(
                match,
                revision,
                new FactionPresentation(List.of(new FactionPresentation.Style("strategos:placeholder", colour))),
                List.of(new MatchPresentation.Participant(new PlayerSlot(0), 0, "host", true)));
    }

    @Test
    void staleForeignAndWrongShapeFramesCannotReplaceTheJoinedView() {
        var client = new ClientPresentation();
        client.joined(7, 1);
        assertThat(client.accept(frame(7, 3, 14))).isTrue();
        assertThat(client.accept(frame(7, 2, 11))).isFalse();
        assertThat(client.accept(frame(7, 3, 11))).isFalse();
        assertThat(client.accept(frame(8, 4, 11))).isFalse();
        assertThat(client.accept(new MatchPresentation(7, 4, FactionPresentation.legacy(2), List.of())))
                .isFalse();
        assertThat(client.palette().dyeName(0)).isEqualTo("red");
    }

    @Test
    void publicRevisionCanAdvanceWithoutAnAdvancingSimulationClockAndRejoinResetsIt() {
        var client = new ClientPresentation();
        client.joined(7, 1);
        assertThat(client.accept(frame(7, 5, 14))).isTrue();
        assertThat(client.accept(frame(7, 6, 11))).isTrue();
        assertThat(client.palette().dyeName(0)).isEqualTo("blue");
        client.clear();
        assertThat(client.view()).isNull();
        assertThat(client.accept(frame(7, 7, 14))).isFalse();
        client.joined(7, 1);
        assertThat(client.view()).isNull();
        assertThat(client.accept(frame(7, 1, 14))).isTrue();
        assertThat(client.palette().dyeName(0)).isEqualTo("red");
    }
}
