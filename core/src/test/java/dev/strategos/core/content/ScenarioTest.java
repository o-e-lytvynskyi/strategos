package dev.strategos.core.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.RuleChange;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.command.ScenarioSetup;
import java.util.List;
import org.junit.jupiter.api.Test;

/** MATCH-01's scenarios in L1: a scenario file becomes exactly the commands its slots list, from their start slots. */
class ScenarioTest {
    static final String FILE = """
            { "map": "strategos:proving_ground",
              "slots": [
                { "resources": { "wood": 50, "food": 300 },
                  "entities": [ { "template": "strategos:building/civic_centre" },
                                { "template": "strategos:unit/worker", "dz": 9, "count": 5 } ] },
                { "player": "ai:first_loop",
                  "entities": [ { "template": "strategos:building/house", "dx": 6, "rotation": 1 } ] } ] }
            """;

    static Scenario parse(String json) {
        return Scenario.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .getOrThrow();
    }

    @Test
    void aScenarioBecomesExactlyItsCommands() {
        Scenario s = parse(FILE);
        assertThat(s.victory()).as("the default rule").isEqualTo("civic_centre");
        assertThat(s.slots().get(1).player()).isEqualTo("ai:first_loop");
        List<Command> commands = ScenarioSetup.commands(
                s, List.of(new StartSlot(40, 0, 40, 0), new StartSlot(200, 0, 200, 180)), TemplateSet.builtin());
        assertThat(commands)
                .containsExactly(
                        new RuleChange("victory", "civic_centre"),
                        new Grant(new FactionSlot(0), "food", 300),
                        new Grant(new FactionSlot(0), "wood", 50),
                        new SpawnBuilding(
                                new TemplateId("strategos:building/civic_centre"), new FactionSlot(0), 35, 35, 0),
                        new SpawnUnits(TemplateSet.WORKER, new FactionSlot(0), new Vec2d(40.5, 49.5), 5, 2.0),
                        new SpawnBuilding(new TemplateId("strategos:building/house"), new FactionSlot(1), 204, 198, 1));
    }

    @Test
    void refusesWhatItCannotPlace() {
        Scenario s = parse(FILE);
        assertThatThrownBy(() -> ScenarioSetup.commands(s, List.of(new StartSlot(40, 0, 40, 0)), TemplateSet.builtin()))
                .hasMessageContaining("2 slots but 1 start slots");
        Scenario unknown = parse("""
                { "map": "m", "slots": [ { "entities": [ { "template": "strategos:unit/nobody" } ] } ] }""");
        assertThatThrownBy(() ->
                        ScenarioSetup.commands(unknown, List.of(new StartSlot(0, 0, 0, 0)), TemplateSet.builtin()))
                .hasMessageContaining("no template strategos:unit/nobody");
    }
}
