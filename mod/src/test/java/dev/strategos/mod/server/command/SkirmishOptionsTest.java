package dev.strategos.mod.server.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.protocol.SkirmishSetup;
import dev.strategos.core.protocol.SkirmishSetup.Level;
import dev.strategos.core.protocol.SkirmishSetup.StartResources;
import dev.strategos.core.protocol.SkirmishSetup.Visibility;
import dev.strategos.mod.server.match.MatchRefusedException;
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
            "resources=lots"
        }) {
            assertThatThrownBy(() -> MatchCommands.setup(MAP, bad)).as(bad).isInstanceOf(MatchRefusedException.class);
        }
    }
}
