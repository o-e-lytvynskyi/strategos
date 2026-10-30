package dev.strategos.mod.server.persist;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.Hex64;
import dev.strategos.core.sim.CorePorts;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.persist.MatchSnapshotCodec;
import dev.strategos.core.sim.persist.Migrations;
import dev.strategos.core.sim.persist.SchemaVersion;
import dev.strategos.core.testkit.FixtureMatches;
import dev.strategos.core.testkit.SimHarness;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * CORE-07.4 for schema 3 (#25): {@link FixtureMatches#schema3} as NBT still decodes to its recorded
 * hash, the same as the core's JSON fixture of the same match. {@code -Dstrategos.fixtures.update=true} writes the two
 * files once; they are released-schema fixtures, never changed or deleted, so an existing file is kept.
 */
class MatchSnapshotSchema3NbtFixtureTest {
    private static final String FIXTURE = "fixtures/match_v3.nbt";
    private static final String EXPECTED = "fixtures/match_v3.expected.json";

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
        SimHarness h = FixtureMatches.schema3();
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

    private static JsonObject facts(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    @Test
    void theReleasedVersion3SaveDecodesToItsRecordedHash() throws IOException {
        CompoundTag tag = NbtIo.readCompressed(repo(FIXTURE), NbtAccounter.unlimitedHeap());
        JsonObject expected = facts(repo(EXPECTED));
        assertThat(tag.getInt("v")).isEqualTo(3);

        MatchSnapshot snapshot = MatchSnapshotCodec.decode(NbtBridge.dynamic(tag), Migrations.production())
                .getOrThrow();
        MatchCore core = MatchCore.restore(snapshot, TemplateSet.builtin(), CorePorts.of(() -> 0L));

        assertThat(core.queries().tick())
                .isEqualTo(expected.get("tick").getAsLong())
                .isEqualTo(FixtureMatches.SCHEMA_3_TICK);
        assertThat(core.queries().unitCount()).isEqualTo(expected.get("units").getAsInt());
        assertThat(Hex64.format(core.stateHash()))
                .isEqualTo(expected.get("stateHash").getAsString());
        assertThat(expected.get("stateHash"))
                .as("the same match as the core's JSON fixture: NBT and JSON hold the same state")
                .isEqualTo(facts(core(EXPECTED)).get("stateHash"));
        assertThat(snapshot.templateBindings()).isNotEmpty();
        assertThat(core.queries().buildings()).hasSize(2);
        assertThat(core.queries().buildings().getFirst().queue()).hasSize(2);
        assertThat(core.queries().buildings().getFirst().trained()).isPositive();
    }
}
