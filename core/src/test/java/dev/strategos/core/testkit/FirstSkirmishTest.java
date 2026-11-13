package dev.strategos.core.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.content.Scenario;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.TerrainJob;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.map.MapChecker;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * MAP-02.1–3 (spec T12.1): the first skirmish map is deterministic and point-symmetric, and the checker passes it with
 * its start scenario: resources per start within 5 %, the walks to the dropsite sites within 10 %, every start reached
 * by every mobility class, and every terrain job.
 */
final class FirstSkirmishTest {
    private static FirstSkirmish map;
    private static MapChecker.Report report;

    @BeforeAll
    static void check() throws IOException {
        map = FirstSkirmish.generate();
        Scenario scenario = scenario();
        List<MapChecker.Site> sites = MapChecker.scenarioSites(scenario, map.startSlots(), TemplateSet.builtin());
        report = MapChecker.check(map.checkerInput(sites));
        report.measures().forEach(m -> System.out.println("first_skirmish: " + m));
    }

    static Scenario scenario() throws IOException {
        Path file = Path.of(System.getProperty("strategos.repoRoot"))
                .resolve("mod/src/main/resources/data/strategos/strategos/scenarios/first_skirmish.json");
        return Scenario.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(file)))
                .getOrThrow();
    }

    @Test
    void theGeneratorIsDeterministic() {
        assertThat(FirstSkirmish.generate().terrainHash()).isEqualTo(map.terrainHash());
    }

    @Test
    void theMapIsTheSameTurnedHalfRound() {
        int differ = 0;
        for (int x = 0; x < FirstSkirmish.SIZE; x++) {
            for (int z = 0; z < FirstSkirmish.SIZE; z++) {
                for (int y = 0; y < FirstSkirmish.HEIGHT; y++) {
                    differ += map.block(x, y, z) != map.block(FirstSkirmish.mirror(x), y, FirstSkirmish.mirror(z))
                            ? 1
                            : 0;
                }
            }
        }
        assertThat(differ).as("voxels unlike their mirror").isZero();
    }

    @Test
    void theCheckerPassesTheMapAndItsScenario() {
        assertThat(report.problems()).as(String.join("\n", report.measures())).isEmpty();
    }

    @Test
    void theManifestDocumentsEachKindOfJob() {
        assertThat(map.terrainJobs())
                .extracting(TerrainJob::kind)
                .contains(
                        TerrainJob.HILL,
                        TerrainJob.FOREST_LINE,
                        TerrainJob.RIVER,
                        TerrainJob.RESOURCE,
                        TerrainJob.ROOM);
        assertThat(map.terrainJobs().stream()
                        .filter(j -> j.kind().equals(TerrainJob.RIVER))
                        .mapToInt(TerrainJob::count)
                        .sum())
                .as("fords")
                .isEqualTo(2);
    }

    @Test
    void theFieldsRoomHoldsSixFieldsOfTheShippedBlueprint() {
        TemplateSet templates = TemplateSet.builtin();
        UnitTemplate field = Objects.requireNonNull(templates.byId(new TemplateId("strategos:building/field")));
        Blueprint b = Objects.requireNonNull(templates.blueprint(field.netIndex()));
        Footprint f = Footprint.of(b, 0, 0, 0);
        int side = Math.max(f.spanX(), f.spanZ());
        for (TerrainJob room : map.terrainJobs().stream()
                .filter(j -> j.kind().equals(TerrainJob.ROOM))
                .toList()) {
            assertThat(room.size())
                    .as("the room's square side against the field's span")
                    .isGreaterThanOrEqualTo(side);
            assertThat(room.count()).isEqualTo(6);
        }
    }

    @Test
    void eachStartHasACentreFiveWorkersAndAHorseman() throws IOException {
        for (Scenario.Slot slot : scenario().slots()) {
            assertThat(count(slot, "strategos:building/civic_centre")).isEqualTo(1);
            assertThat(count(slot, "strategos:unit/worker")).isEqualTo(5);
            assertThat(count(slot, "strategos:unit/horseman")).isEqualTo(1);
        }
    }

    private static int count(Scenario.Slot slot, String template) {
        return slot.entities().stream()
                .filter(e -> e.template().value().equals(template))
                .mapToInt(Scenario.Entity::count)
                .sum();
    }
}
