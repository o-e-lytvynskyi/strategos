package dev.strategos.core.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.shared.CivId;
import java.util.List;
import org.junit.jupiter.api.Test;

/** MATCH-16: the day-and-night choice rides the setup and the match state as appended bytes; every older form reads as before. */
class DayNightWireTest {
    private static SkirmishSetup roundTrip(SkirmishSetup s) {
        return WireCodec.decode(SkirmishSetup.WIRE, WireCodec.encode(SkirmishSetup.WIRE, s));
    }

    @Test
    void theSetupCarriesDayNightWithAndWithoutCivilisations() {
        SkirmishSetup d = SkirmishSetup.DEFAULTS;
        assertThat(d.dayNight()).as("off by default").isFalse();
        int plain = WireCodec.encode(SkirmishSetup.WIRE, d).length;
        SkirmishSetup on = new SkirmishSetup(
                d.map(), d.level(), d.resources(), d.populationCap(), d.visibility(), List.of(), true);
        assertThat(WireCodec.encode(SkirmishSetup.WIRE, on))
                .as("a count of 0 and the byte")
                .hasSize(plain + 2);
        assertThat(roundTrip(on)).isEqualTo(on);
        SkirmishSetup both = new SkirmishSetup(
                d.map(),
                d.level(),
                d.resources(),
                d.populationCap(),
                d.visibility(),
                List.of(new CivId("t:lean")),
                true);
        assertThat(roundTrip(both)).isEqualTo(both);
        assertThat(WireCodec.encode(SkirmishSetup.WIRE, both).length).isLessThanOrEqualTo(SkirmishSetup.WIRE.maxSize());
        assertThat(roundTrip(d)).as("a setup without it is as it was").isEqualTo(d);
    }

    @Test
    void anEmptyCivilisationCountAloneIsStillAWireFault() {
        byte[] plain = WireCodec.encode(SkirmishSetup.WIRE, SkirmishSetup.DEFAULTS);
        byte[] bad = java.util.Arrays.copyOf(plain, plain.length + 1); // a trailing count of 0, no byte after it
        assertThatThrownBy(() -> WireCodec.decode(SkirmishSetup.WIRE, bad)).isInstanceOf(WireException.class);
    }

    @Test
    void theMatchStateCarriesTheTintAfterTheLooks() {
        MatchStateMsg off = new MatchStateMsg(3, MatchStateMsg.Lifecycle.RUNNING, 77);
        byte[] offBytes = WireCodec.encode(MatchStateMsg.WIRE, off);
        MatchStateMsg on = new MatchStateMsg(3, MatchStateMsg.Lifecycle.RUNNING, 77, null, List.of(), true);
        byte[] onBytes = WireCodec.encode(MatchStateMsg.WIRE, on);
        assertThat(onBytes).hasSize(offBytes.length + 2);
        assertThat(WireCodec.decode(MatchStateMsg.WIRE, onBytes)).isEqualTo(on);
        assertThat(WireCodec.decode(MatchStateMsg.WIRE, onBytes).tint()).isTrue();
        assertThat(WireCodec.decode(MatchStateMsg.WIRE, offBytes).tint()).isFalse();
        assertThat(onBytes.length).isLessThanOrEqualTo(MatchStateMsg.WIRE.maxSize());
        MatchStateMsg looks = new MatchStateMsg(
                3, MatchStateMsg.Lifecycle.RUNNING, 77, null, List.of(FactionLook.BASE, FactionLook.BASE), true);
        assertThat(WireCodec.decode(MatchStateMsg.WIRE, WireCodec.encode(MatchStateMsg.WIRE, looks)))
                .isEqualTo(looks);
    }
}
