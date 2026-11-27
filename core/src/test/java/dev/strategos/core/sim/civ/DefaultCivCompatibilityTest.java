package dev.strategos.core.sim.civ;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.CivId;
import dev.strategos.core.shared.MatchId;
import dev.strategos.core.sim.CorePorts;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchBounds;
import dev.strategos.core.sim.MatchConfig;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchRules;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.persist.MatchSnapshotCodec;
import dev.strategos.core.sim.persist.Migrations;
import dev.strategos.core.testkit.SimHarness;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** M10 §0.2: the default civilisation is invisible. Old saves bind as it, the config's list is optional, hashes hold. */
class DefaultCivCompatibilityTest {
    private static JsonElement fixture(String name) throws IOException {
        try (InputStream in = DefaultCivCompatibilityTest.class.getResourceAsStream("/" + name)) {
            assertThat(in).as("the committed fixture " + name).isNotNull();
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void anOldSaveWithoutCivsDecodesAndBindsAsTheDefault() throws IOException {
        JsonObject json = fixture("fixtures/match_v3.json").getAsJsonObject();
        assertThat(json.getAsJsonObject("config").has("civs"))
                .as("a save from before civilisations")
                .isFalse();
        MatchSnapshot snapshot = MatchSnapshotCodec.decode(
                        new Dynamic<>(JsonOps.INSTANCE, json), Migrations.production())
                .getOrThrow();
        assertThat(snapshot.config().civs()).isEmpty();

        MatchCore core = MatchCore.restore(snapshot, TemplateSet.builtin(), CorePorts.of(() -> 0L));
        for (int f = 0; f < snapshot.config().factions(); f++) {
            assertThat(snapshot.config().civ(f)).isEqualTo(CivId.DEFAULT);
            assertThat(KernelAccess.state(core).civs().of(f).id()).isEqualTo(CivId.DEFAULT);
        }
        JsonElement again = MatchSnapshotCodec.encode(JsonOps.INSTANCE, snapshot);
        assertThat(again.getAsJsonObject().getAsJsonObject("config").has("civs"))
                .as("an empty list is omitted on encode")
                .isFalse();
    }

    @Test
    void aConfigWithCivsRoundTrips() {
        MatchConfig config = new MatchConfig(
                new MatchId(3),
                9L,
                new MatchBounds(32, 32, 0, 16, 1.0),
                2,
                MatchRules.DEFAULTS,
                List.of(),
                List.of(CivScene.LEAN, CivId.DEFAULT));
        JsonElement json =
                MatchConfig.CODEC.encodeStart(JsonOps.INSTANCE, config).getOrThrow();
        assertThat(json.getAsJsonObject().getAsJsonArray("civs")).hasSize(2);
        assertThat(MatchConfig.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow()).isEqualTo(config);
    }

    @Test
    void aConfigWithOnlyDefaultIdsHashesAsOneWithout() {
        SimHarness none = CivScene.match(TemplateSet.builtin(), List.of());
        SimHarness explicit = CivScene.match(TemplateSet.builtin(), List.of(CivId.DEFAULT, CivId.DEFAULT));
        none.runTicks(10);
        explicit.runTicks(10);
        assertThat(explicit.stateHash()).isEqualTo(none.stateHash());
    }

    @Test
    void aNonDefaultCivilisationChangesTheHashAndItsIdsAreMixedAsStrings() {
        TemplateSet set = CivScene.lean("[]");
        SimHarness defaults = CivScene.match(set, List.of(CivId.DEFAULT, CivId.DEFAULT));
        SimHarness lean = CivScene.match(set, List.of(CivScene.LEAN, CivId.DEFAULT));
        SimHarness swapped = CivScene.match(set, List.of(CivId.DEFAULT, CivScene.LEAN));
        assertThat(lean.stateHash()).isNotEqualTo(defaults.stateHash());
        assertThat(swapped.stateHash()).as("in faction order").isNotEqualTo(lean.stateHash());
    }
}
