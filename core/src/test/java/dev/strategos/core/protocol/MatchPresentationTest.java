package dev.strategos.core.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.shared.PlayerSlot;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchPresentationTest {
    @Test
    void coopNamesKeepDistinctPlayersAndOnePrimaryWhileOnlyPublicFieldsRoundTrip() {
        var names = new ArrayList<>(List.of(
                new MatchPresentation.Participant(new PlayerSlot(1), 0, "peer", true),
                new MatchPresentation.Participant(new PlayerSlot(0), 0, "host", true)));
        var view = new MatchPresentation(3, 8, FactionPresentation.legacy(2), names);
        names.clear();
        assertThat(view.participants()).extracting(p -> p.player().value()).containsExactly(0, 1);
        assertThat(view.participants())
                .extracting(MatchPresentation.Participant::faction)
                .containsExactly(0, 0);
        var out = new WireWriter(MatchPresentation.WIRE.maxSize());
        MatchPresentation.WIRE.write(out, view);
        assertThat(MatchPresentation.WIRE.read(new WireReader(out.toByteArray())))
                .isEqualTo(view);
        assertThat(view.sameView(new MatchPresentation(3, 9, view.factions(), view.participants())))
                .isTrue();
        assertThat(view.sameView(new MatchPresentation(4, 9, view.factions(), view.participants())))
                .isFalse();
    }

    @Test
    void duplicateOrForeignPlayersCannotSpoofPublicMembership() {
        var human = new MatchPresentation.Participant(new PlayerSlot(0), 0, "host", true);
        assertThatThrownBy(() -> new MatchPresentation(3, 8, FactionPresentation.legacy(2), List.of(human, human)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MatchPresentation(
                        3,
                        8,
                        FactionPresentation.legacy(2),
                        List.of(new MatchPresentation.Participant(new PlayerSlot(1), 2, "foreign", true))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MatchPresentation.Participant(PlayerSlot.ADMIN, 0, "observer", true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MatchPresentation.Participant(new PlayerSlot(0), 0, "bad\nname", true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MatchPresentation.Participant(new PlayerSlot(0), 0, "x".repeat(65), true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theActualMaximumRemainsBelowFourKiBAndRoundTripsExactly() {
        var styles = new ArrayList<FactionPresentation.Style>();
        var participants = new ArrayList<MatchPresentation.Participant>();
        for (int i = 0; i < 16; i++) {
            styles.add(new FactionPresentation.Style("s:" + "x".repeat(126), i));
            participants.add(new MatchPresentation.Participant(new PlayerSlot(i), i, "x".repeat(64), true));
        }
        var view =
                new MatchPresentation(Integer.MAX_VALUE, Long.MAX_VALUE, new FactionPresentation(styles), participants);
        var out = new WireWriter(MatchPresentation.WIRE.maxSize());
        MatchPresentation.WIRE.write(out, view);
        assertThat(out.toByteArray()).hasSize(MatchPresentation.WIRE.maxSize());
        assertThat(MatchPresentation.WIRE.maxSize()).isLessThan(4096);
        assertThat(MatchPresentation.WIRE.read(new WireReader(out.toByteArray())))
                .isEqualTo(view);
    }
}
