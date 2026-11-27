package dev.strategos.mod.server.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonParser;
import dev.strategos.core.content.RawCiv;
import dev.strategos.core.content.TemplateCompiler;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.protocol.SkirmishSetup;
import dev.strategos.core.shared.CivId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** CIV-01 in a skirmish: the setup's civilisations are checked against the template set, and the AI profile is the civilisation's. */
class SkirmishCivsTest {
    private static final CivId LEAN = new CivId("t:lean");

    /** The built-in templates and the default civilisation, and {@code t:lean} playing the {@code raider} profile. */
    private static TemplateSet set() {
        TemplateSet builtin = TemplateSet.builtin();
        List<TemplateCompiler.Flat> flats = new ArrayList<>();
        for (UnitTemplate t : builtin.all()) {
            flats.add(new TemplateCompiler.Flat(t.id(), t.netIndex(), builtin.flattened(t.id())));
        }
        List<TemplateCompiler.TechFlat> techs =
                builtin.techs().stream().map(TemplateCompiler.TechFlat::of).toList();
        RawCiv lean = new RawCiv(
                LEAN,
                "data/t/strategos/civs/lean.json",
                JsonParser.parseString(
                                "{\"roles\":{\"house\":\"strategos:building/house\"},\"ai\":{\"profile\":\"raider\"}}")
                        .getAsJsonObject());
        RawCiv base = new RawCiv(
                CivId.DEFAULT,
                "data/strategos/strategos/civs/default.json",
                JsonParser.parseString("{\"roles\":{\"house\":\"strategos:building/house\"}}")
                        .getAsJsonObject());
        TemplateCompiler.Result r = TemplateCompiler.fromFlattened(flats, techs, List.of(base, lean));
        assertThat(r.problems()).isEmpty();
        return r.set();
    }

    private static SkirmishSetup with(CivId... civs) {
        SkirmishSetup d = SkirmishSetup.DEFAULTS;
        return new SkirmishSetup(d.map(), d.level(), d.resources(), d.populationCap(), d.visibility(), List.of(civs));
    }

    @Test
    void theDefaultSetupPlaysTheDefaultCivilisationEverywhere() {
        assertThat(Skirmishes.civs(SkirmishSetup.DEFAULTS, 2, set())).isEmpty();
        assertThat(Skirmishes.civs(with(CivId.DEFAULT, CivId.DEFAULT), 2, set()))
                .isEmpty();
    }

    @Test
    void oneCivilisationIsEverySlotsAndTwoArePerSlot() {
        assertThat(Skirmishes.civs(with(LEAN), 2, set())).containsExactly(LEAN, LEAN);
        assertThat(Skirmishes.civs(with(CivId.DEFAULT, LEAN), 2, set())).containsExactly(CivId.DEFAULT, LEAN);
    }

    @Test
    void aCivilisationTheSetLacksOrTheWrongCountIsRefusedBeforeAMatchExists() {
        assertThatThrownBy(() -> Skirmishes.civs(with(new CivId("t:gone")), 2, set()))
                .isInstanceOf(MatchRefusedException.class)
                .hasMessageContaining("t:gone");
        assertThatThrownBy(() -> Skirmishes.civs(with(LEAN, LEAN, LEAN), 2, set()))
                .isInstanceOf(MatchRefusedException.class)
                .hasMessageContaining("3 civilisations");
        assertThatThrownBy(() -> Skirmishes.civs(with(LEAN), 2, TemplateSet.builtin()))
                .isInstanceOf(MatchRefusedException.class);
    }

    @Test
    void theComputersProfileIsTheCivilisationsOwn() {
        TemplateSet set = set();
        assertThat(Skirmishes.profile(set, CivId.DEFAULT))
                .as("today's behaviour")
                .isEqualTo("skirmish");
        assertThat(Skirmishes.profile(set, LEAN)).isEqualTo("raider");
        assertThat(Skirmishes.profile(set, new CivId("t:gone"))).isEqualTo("skirmish");
    }
}
