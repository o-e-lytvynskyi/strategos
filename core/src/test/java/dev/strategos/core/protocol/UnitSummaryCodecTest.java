package dev.strategos.core.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** NET-08.1: a summary frame round-trips at about 6 bytes a unit, and 600 units fit its cap. */
class UnitSummaryCodecTest {
    /** 600 units of two factions spread over a 512² space, as two armies stand: every third index, both kinds. */
    private static UnitSummary sixHundred() {
        List<UnitSummary.Entry> units = new ArrayList<>();
        for (int k = 0; k < 600; k++) {
            int faction = k % 2;
            units.add(UnitSummary.Entry.at(k * 3 / 2, faction, k % 4, k % 2, 20 + (k * 37) % 470, 30 + (k * 53) % 450));
        }
        return new UnitSummary(3, 12_345, units);
    }

    @Test
    void sixHundredUnitsRoundTripAtAboutSixBytesEach() {
        UnitSummary frame = sixHundred();
        byte[] bytes = WireCodec.encode(UnitSummary.WIRE, frame);
        assertThat(WireCodec.decode(UnitSummary.WIRE, bytes)).isEqualTo(frame);
        assertThat(bytes.length / 600.0).as("bytes a unit").isLessThanOrEqualTo(6.1);
        assertThat(bytes.length).isLessThanOrEqualTo(UnitSummary.WIRE.maxSize());
    }

    @Test
    void entriesGoByFactionThenIndexAndTemplatesPastThirtyEscape() {
        UnitSummary frame = new UnitSummary(
                1,
                7,
                List.of(
                        UnitSummary.Entry.at(9, 1, 40, 1, 100.06, 3.2),
                        UnitSummary.Entry.at(4, 0, 2, 0, 0, 511.99),
                        UnitSummary.Entry.at(2, 1, 0, 0, 7.5, 7.5)));
        assertThat(frame.units()).extracting(UnitSummary.Entry::index).containsExactly(4, 2, 9);
        UnitSummary back = WireCodec.decode(UnitSummary.WIRE, WireCodec.encode(UnitSummary.WIRE, frame));
        assertThat(back).isEqualTo(frame);
        assertThat(back.units().get(2).template()).isEqualTo(40);
        assertThat(back.units().get(2).x()).isEqualTo(100.0); // an eighth of a block
        assertThat(back.units().get(0).z()).isEqualTo(512.0);
    }

    @Test
    void aFrameHoldsAtMostItsCap() {
        List<UnitSummary.Entry> units = new ArrayList<>();
        for (int k = 0; k <= UnitSummary.MAX_UNITS; k++) {
            units.add(new UnitSummary.Entry(k, 0, 0, 0, 0, 0));
        }
        assertThatThrownBy(() -> new UnitSummary(1, 1, units)).isInstanceOf(IllegalArgumentException.class);
        assertThat(WireCodec.encode(UnitSummary.WIRE, new UnitSummary(1, 1, units.subList(0, UnitSummary.MAX_UNITS)))
                        .length)
                .isLessThanOrEqualTo(UnitSummary.WIRE.maxSize());
    }
}
