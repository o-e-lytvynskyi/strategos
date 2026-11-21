package dev.strategos.core.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FactionPresentationTest {
    @Test
    void detachedStylesRetainTheCivilisationAndExplicitColourInFactionOrder() {
        var styles = new ArrayList<>(List.of(
                new FactionPresentation.Style("strategos:placeholder", 14),
                new FactionPresentation.Style("strategos:other", 11)));
        var value = new FactionPresentation(styles);
        styles.clear();
        assertThat(value.styles()).hasSize(2);
        assertThat(value.style(0).colour()).isEqualTo(14);
        assertThat(value.style(1).civilisation()).isEqualTo("strategos:other");
        assertThat(value.isLegacy()).isFalse();
        assertThatThrownBy(() -> value.styles().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void legacyPresentationIsExplicitAndKeepsEveryFactionWithoutInventingSelectedColours() {
        var legacy = FactionPresentation.legacy(16);
        assertThat(legacy.styles()).hasSize(16);
        assertThat(legacy.isLegacy()).isTrue();
        assertThat(legacy.styles())
                .allMatch(s -> s.colour() == -1 && s.civilisation().equals("strategos:placeholder"));
        assertThatThrownBy(() -> FactionPresentation.legacy(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FactionPresentation.legacy(17)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void malformedResourceIdsPaletteIndicesAndUnboundedFactionListsAreRejected() {
        for (int colour : new int[] {-2, 16, Integer.MAX_VALUE})
            assertThatThrownBy(() -> new FactionPresentation.Style("strategos:placeholder", colour))
                    .isInstanceOf(IllegalArgumentException.class);
        for (String id : List.of("", "missing_namespace", "strategos:Bad", "strategos:" + "x".repeat(129)))
            assertThatThrownBy(() -> new FactionPresentation.Style(id, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FactionPresentation(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FactionPresentation(
                        java.util.Collections.nCopies(17, new FactionPresentation.Style("strategos:placeholder", -1))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theActualMaximumRoundTripsAndOversizedWireCountsFailBeforeAllocating() {
        var styles = new ArrayList<FactionPresentation.Style>();
        for (int i = 0; i < 16; i++) styles.add(new FactionPresentation.Style("s:" + "x".repeat(126), i));
        var value = new FactionPresentation(styles);
        var out = new WireWriter(FactionPresentation.WIRE.maxSize());
        FactionPresentation.WIRE.write(out, value);
        assertThat(out.toByteArray()).hasSize(FactionPresentation.WIRE.maxSize());
        assertThat(FactionPresentation.WIRE.read(new WireReader(out.toByteArray())))
                .isEqualTo(value);
        for (int count : new int[] {0, 17, Integer.MAX_VALUE}) {
            var malformed = new WireWriter(8);
            malformed.writeVarInt(count);
            assertThatThrownBy(() -> FactionPresentation.WIRE.read(new WireReader(malformed.toByteArray())))
                    .isInstanceOf(WireException.class);
        }
    }
}
