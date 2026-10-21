package dev.strategos.mod.server.persist;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Dynamic;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.Hex64;
import dev.strategos.core.sim.CorePorts;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.persist.MatchSnapshotCodec;
import dev.strategos.core.sim.persist.MigrationStep;
import dev.strategos.core.sim.persist.Migrations;
import dev.strategos.core.sim.persist.SchemaVersion;
import dev.strategos.core.testkit.Scenario;
import dev.strategos.core.testkit.Scenarios;
import dev.strategos.core.testkit.Seeds;
import dev.strategos.core.testkit.SimHarness;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * CORE-07.4: the released schema-1 save as NBT, the canary at tick 500, still decodes to its recorded hash, and a
 * test-only migration runs on its NBT {@code Dynamic}. {@code -Dstrategos.fixtures.update=true} writes the two files
 * once; they are released-schema fixtures, never changed or deleted, so an existing file is kept.
 */
class MatchSnapshotFixtureTest {
    private static final String FIXTURE = "fixtures/match_v1.nbt";
    private static final String EXPECTED = "fixtures/match_v1.expected.json";
    private static final long AT_TICK = 500;

    /** A file of this module's test resources, read from the repository (the test JVM runs in the module layer). */
    private static Path repo(String resource) {
        return Path.of(System.getProperty("strategos.repoRoot"), "mod/src/test/resources", resource);
    }

    private static Path core(String resource) {
        return Path.of(System.getProperty("strategos.repoRoot"), "core/src/test/resources", resource);
    }

    @BeforeAll
    static void writeOnceWhenAsked() throws IOException {
        Path fixture = repo(FIXTURE);
        Path expected = repo(EXPECTED);
        if (!Boolean.getBoolean("strategos.fixtures.update") || Files.exists(fixture) || Files.exists(expected)) {
            return; // released: never rewritten
        }
        Scenario canary = Scenarios.canary(Seeds.first("canary"));
        SimHarness h = SimHarness.forScenario(canary);
        canary.setup(h);
        while (h.tick() < AT_TICK) {
            h.step();
            canary.onTick(h);
        }
        MatchSnapshot snapshot = h.core().snapshot();
        Files.createDirectories(fixture.getParent());
        NbtIo.writeCompressed((CompoundTag) MatchSnapshotCodec.encode(NbtOps.INSTANCE, snapshot), fixture);
        JsonObject facts = new JsonObject();
        facts.addProperty("v", SchemaVersion.CURRENT);
        facts.addProperty("tick", snapshot.tick());
        facts.addProperty("units", snapshot.units().size());
        facts.addProperty("stateHash", Hex64.format(h.stateHash()));
        Files.writeString(
                expected, new GsonBuilder().setPrettyPrinting().create().toJson(facts) + "\n", StandardCharsets.UTF_8);
    }

    private static CompoundTag fixture() throws IOException {
        return NbtIo.readCompressed(repo(FIXTURE), NbtAccounter.unlimitedHeap());
    }

    private static JsonObject facts(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    @Test
    void theReleasedVersion1SaveDecodesToItsRecordedHash() throws IOException {
        CompoundTag tag = fixture();
        JsonObject expected = facts(repo(EXPECTED));
        assertThat(tag.getInt("v")).isEqualTo(1);

        MatchSnapshot snapshot = MatchSnapshotCodec.decode(NbtBridge.dynamic(tag), Migrations.production())
                .getOrThrow();
        MatchCore core = MatchCore.restore(snapshot, TemplateSet.builtin(), CorePorts.of(() -> 0L));

        assertThat(core.queries().tick())
                .isEqualTo(expected.get("tick").getAsLong())
                .isEqualTo(AT_TICK);
        assertThat(core.queries().unitCount()).isEqualTo(expected.get("units").getAsInt());
        assertThat(Hex64.format(core.stateHash()))
                .isEqualTo(expected.get("stateHash").getAsString());
        assertThat(expected.get("stateHash"))
                .as("the same canary tick as the JSON fixture of core: NBT and JSON hold the same match")
                .isEqualTo(facts(core(EXPECTED)).get("stateHash"));
    }

    @Test
    void aTestOnlyMigrationRunsOnTheNbtDynamic() throws IOException {
        CompoundTag original = fixture();
        double floorY = original.getCompound("config").getCompound("bounds").getDouble("flatFloorY");
        // The kind of step M1 brings when terrain replaces the flat floor: a field moves out of the bounds.
        MigrationStep floorToTerrain = new MigrationStep() {
            @Override
            public int from() {
                return 1;
            }

            @Override
            public <T> Dynamic<T> apply(Dynamic<T> input) {
                Dynamic<T> bounds = input.get("config").get("bounds").orElseEmptyMap();
                Dynamic<T> floor = bounds.get("flatFloorY").orElseEmptyMap();
                Dynamic<T> config = input.get("config")
                        .orElseEmptyMap()
                        .set("bounds", bounds.remove("flatFloorY"))
                        .set("terrain", input.emptyMap().set("flatFloorY", floor));
                return input.set("config", config);
            }
        };

        Tag migrated = new Migrations(List.of(floorToTerrain))
                .migrate(NbtBridge.dynamic(original.copy()), 2)
                .getOrThrow()
                .getValue();

        CompoundTag result = (CompoundTag) migrated;
        assertThat(result.getInt("v")).isEqualTo(2);
        assertThat(result.getCompound("config").getCompound("bounds").contains("flatFloorY"))
                .isFalse();
        assertThat(result.getCompound("config").getCompound("terrain").getDouble("flatFloorY"))
                .isEqualTo(floorY);
        assertThat(result.get("units")).as("the rest is carried over").isEqualTo(original.get("units"));
        assertThat(result.getLong("tick")).isEqualTo(original.getLong("tick"));
    }
}
