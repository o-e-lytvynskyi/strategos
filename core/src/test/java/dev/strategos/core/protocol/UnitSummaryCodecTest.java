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

    @Test
    void aFrameWithoutFormationsIsByteForByteWhatItWas() {
        UnitSummary plain = sixHundred();
        byte[] bytes = WireCodec.encode(UnitSummary.WIRE, plain);
        UnitSummary same = new UnitSummary(plain.matchId(), plain.tick(), plain.units());
        assertThat(WireCodec.encode(UnitSummary.WIRE, same)).isEqualTo(bytes);
        assertThat(WireCodec.decode(UnitSummary.WIRE, bytes).units()).allMatch(e -> e.formation() == 0);
    }

    /** ORD-06: the formation id rides in a trailer written only when a unit has one. */
    @Test
    void formationIdsRideInATrailerOnlyForTheUnitsThatHaveOne() {
        List<UnitSummary.Entry> units = new ArrayList<>();
        for (int k = 0; k < 40; k++) {
            units.add(UnitSummary.Entry.at(k, k % 2, 1, 0, 10 + k, 20, k % 4 == 0 ? 300 + k / 4 : 0));
        }
        UnitSummary frame = new UnitSummary(2, 99, units);
        byte[] bytes = WireCodec.encode(UnitSummary.WIRE, frame);
        UnitSummary back = WireCodec.decode(UnitSummary.WIRE, bytes);
        assertThat(back).isEqualTo(frame);
        assertThat(back.units().stream().filter(e -> e.formation() > 0)).hasSize(10);
        int plain = WireCodec.encode(
                        UnitSummary.WIRE,
                        new UnitSummary(
                                2,
                                99,
                                units.stream()
                                        .map(e -> new UnitSummary.Entry(
                                                e.index(), e.faction(), e.template(), e.state(), e.qx(), e.qz()))
                                        .toList()))
                .length;
        assertThat(bytes.length - plain)
                .as("the trailer: a count and about 3 bytes a member")
                .isLessThanOrEqualTo(1 + 10 * 3);
    }

    /** UI-17: the standing flag rides in a trailer after the formation one, only when some unit holds an order. */
    @Test
    void standingOrdersRideInASecondTrailerAndAFrameWithoutThemIsUnchanged() {
        List<UnitSummary.Entry> plain = new ArrayList<>();
        for (int k = 0; k < 40; k++) {
            plain.add(UnitSummary.Entry.at(k, k % 2, 1, 0, 10 + k, 20, k % 4 == 0 ? 300 + k / 4 : 0));
        }
        byte[] before = WireCodec.encode(UnitSummary.WIRE, new UnitSummary(2, 99, plain));

        List<UnitSummary.Entry> marked = new ArrayList<>();
        for (UnitSummary.Entry e : plain) {
            marked.add(UnitSummary.Entry.at(
                    e.index(), e.faction(), e.template(), e.state(), e.x(), e.z(), e.formation(), e.index() % 6 == 1));
        }
        UnitSummary frame = new UnitSummary(2, 99, marked);
        byte[] bytes = WireCodec.encode(UnitSummary.WIRE, frame);
        assertThat(WireCodec.decode(UnitSummary.WIRE, bytes)).isEqualTo(frame);
        assertThat(bytes.length - before.length).as("a count and a step each").isLessThanOrEqualTo(1 + 7);
        assertThat(java.util.Arrays.copyOf(bytes, before.length))
                .as("the bytes before the new trailer are the old frame's")
                .isEqualTo(before);

        UnitSummary unmarked = new UnitSummary(2, 99, plain);
        assertThat(WireCodec.encode(UnitSummary.WIRE, unmarked)).isEqualTo(before);
        assertThat(WireCodec.decode(UnitSummary.WIRE, before).units()).noneMatch(UnitSummary.Entry::standing);
    }

    /** UI-17: with no formation in the frame the formation trailer's count is written as 0 so the next one can follow. */
    @Test
    void standingOrdersWithoutAnyFormationRoundTrip() {
        List<UnitSummary.Entry> units = List.of(
                UnitSummary.Entry.at(1, 0, 1, 0, 10, 20, 0, false),
                UnitSummary.Entry.at(5, 0, 1, 0, 11, 20, 0, true),
                UnitSummary.Entry.at(7, 1, 1, 0, 12, 20, 0, false));
        UnitSummary frame = new UnitSummary(2, 99, units);
        UnitSummary back = WireCodec.decode(UnitSummary.WIRE, WireCodec.encode(UnitSummary.WIRE, frame));
        assertThat(back).isEqualTo(frame);
        assertThat(back.units()).extracting(UnitSummary.Entry::standing).containsExactly(false, true, false);
        assertThat(WireCodec.encode(UnitSummary.WIRE, frame).length).isLessThanOrEqualTo(UnitSummary.WIRE.maxSize());
    }

    @Test
    void aFullFrameOfFormedUnitsFitsItsCap() {
        List<UnitSummary.Entry> units = new ArrayList<>();
        for (int k = 0; k < UnitSummary.MAX_UNITS; k++) {
            units.add(UnitSummary.Entry.at(k * 2, 15, 40_000, 7, 2_047.9, 2_047.9, Integer.MAX_VALUE, true));
        }
        UnitSummary frame = new UnitSummary(Integer.MAX_VALUE, Long.MAX_VALUE, units);
        byte[] bytes = WireCodec.encode(UnitSummary.WIRE, frame);
        assertThat(bytes.length).isLessThanOrEqualTo(UnitSummary.WIRE.maxSize());
        assertThat(WireCodec.decode(UnitSummary.WIRE, bytes)).isEqualTo(frame);
    }
}
