package dev.strategos.mod.server.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.protocol.SkirmishSetup;
import dev.strategos.core.protocol.SkirmishSetup.Level;
import dev.strategos.core.protocol.SkirmishSetup.StartResources;
import dev.strategos.core.protocol.SkirmishSetup.Visibility;
import dev.strategos.core.shared.CivId;
import dev.strategos.mod.server.match.MatchRefusedException;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** MATCH-02: the options of {@code /strategos skirmish <map> [level=…] [visibility=…] …} make a setup of the defaults. */
class SkirmishOptionsTest {
    private static final ResourceLocation MAP = ResourceLocation.fromNamespaceAndPath("strategos", "first_skirmish");

    @Test
    void noOptionsAreTheDefaultsOnTheGivenMap() {
        assertThat(MatchCommands.setup(MAP, "")).isEqualTo(SkirmishSetup.DEFAULTS);
        assertThat(MatchCommands.setup(MAP, "   ")).isEqualTo(SkirmishSetup.DEFAULTS);
    }

    @Test
    void eachOptionSetsItsFieldAndKeepsTheRest() {
        SkirmishSetup s = MatchCommands.setup(MAP, "level=hard visibility=revealed");
        assertThat(s.level()).isEqualTo(Level.HARD);
        assertThat(s.visibility()).isEqualTo(Visibility.REVEALED);
        assertThat(s.resources()).isEqualTo(StartResources.MAP);
        assertThat(s.populationCap()).isEqualTo(150);
        SkirmishSetup t = MatchCommands.setup(MAP, " resources=LOW  population=75 level=Easy ");
        assertThat(t.resources()).isEqualTo(StartResources.LOW);
        assertThat(t.populationCap()).isEqualTo(75);
        assertThat(t.level()).isEqualTo(Level.EASY);
        assertThat(t.map()).isEqualTo("strategos:first_skirmish");
    }

    @Test
    void dayNightIsOffByDefaultAndSetByOnOrOff() {
        assertThat(MatchCommands.setup(MAP, "").dayNight()).isFalse();
        assertThat(MatchCommands.setup(MAP, "daynight=on level=easy").dayNight())
                .isTrue();
        assertThat(MatchCommands.setup(MAP, "daynight=OFF").dayNight()).isFalse();
        assertThatThrownBy(() -> MatchCommands.setup(MAP, "daynight=maybe")).isInstanceOf(MatchRefusedException.class);
        assertThatThrownBy(() -> MatchCommands.setup(MAP, "daynight")).isInstanceOf(MatchRefusedException.class);
    }

    @Test
    void badOptionsAreRefusedNotThrownRaw() {
        for (String bad : new String[] {
            "level=insane",
            "visibility=",
            "level",
            "population=abc",
            "population=49",
            "population=201",
            "population=99999999999",
            "colour=red",
            "resources=lots",
            "civ=",
            "civ=Settlers",
            "civ=t:a,",
            "civ=t:a,,t:b"
        }) {
            assertThatThrownBy(() -> MatchCommands.setup(MAP, bad)).as(bad).isInstanceOf(MatchRefusedException.class);
        }
    }

    @Test
    void civIsOneIdForEverySlotOrOnePerSlot() {
        assertThat(MatchCommands.setup(MAP, "civ=t:lean").civs()).containsExactly(new CivId("t:lean"));
        SkirmishSetup two = MatchCommands.setup(MAP, "level=hard civ=strategos:default,t:lean");
        assertThat(two.civs()).containsExactly(CivId.DEFAULT, new CivId("t:lean"));
        assertThat(two.level()).isEqualTo(Level.HARD);
        assertThat(two.civ(1)).isEqualTo(new CivId("t:lean"));
        assertThat(MatchCommands.setup(MAP, "").civs()).isEqualTo(List.of());
    }
}
