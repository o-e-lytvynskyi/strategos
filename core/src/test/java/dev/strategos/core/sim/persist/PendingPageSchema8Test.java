package dev.strategos.core.sim.persist;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.sim.PageRef;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import org.junit.jupiter.api.Test;

class PendingPageSchema8Test {
    private static JsonObject pending() {
        var h = SimHarness.builder().size(16, 16).yBand(0, 31, 1).build();
        h.publishAll(VoxelFixtures.flat(16, 16, 32, 1));
        h.step();
        var back = SimHarness.restore(h.core().snapshot(), h.queries().templates());
        return MatchSnapshotCodec.encode(JsonOps.INSTANCE, back.core().snapshot())
                .getAsJsonObject();
    }

    @Test
    void schemaEightPreservesTheExplicitPendingReferenceWithoutMakingItPublished() {
        var json = pending();
        assertThat(json.get("v").getAsInt()).isEqualTo(8);
        var decoded = MatchSnapshotCodec.decode(new Dynamic<>(JsonOps.INSTANCE, json), Migrations.production())
                .getOrThrow();
        assertThat(decoded.pages()).hasSize(1).allMatch(PageRef::pending);
        var roundTrip = MatchSnapshotCodec.encode(JsonOps.INSTANCE, decoded);
        assertThat(roundTrip).isEqualTo(json);
    }

    @Test
    void legacySevenReferencesKeepTheirPublishedMeaningEvenWithAnUnknownFutureField() {
        var json = pending();
        json.addProperty("v", 7);
        // An unknown legacy field must never acquire a new meaning just because this reader supports schema 8.
        json.getAsJsonArray("pages").get(0).getAsJsonObject().addProperty("pending", true);
        var decoded = MatchSnapshotCodec.decode(new Dynamic<>(JsonOps.INSTANCE, json), Migrations.production())
                .getOrThrow();
        assertThat(decoded.pages()).hasSize(1).noneMatch(PageRef::pending);
    }

    @Test
    void migrationDoesNotRepairAMissingOrMalformedLegacyPageEnvelope() {
        var missing = pending();
        missing.addProperty("v", 7);
        missing.remove("pages");
        assertThat(MatchSnapshotCodec.decode(new Dynamic<>(JsonOps.INSTANCE, missing), Migrations.production())
                        .error())
                .isPresent();
        var malformed = pending();
        malformed.addProperty("v", 7);
        malformed.addProperty("pages", 42);
        assertThat(MatchSnapshotCodec.decode(new Dynamic<>(JsonOps.INSTANCE, malformed), Migrations.production())
                        .error())
                .isPresent();
    }

    @Test
    void anExplicitCurrentPendingFlagHasToBeABoolean() {
        var json = pending();
        json.getAsJsonArray("pages").get(0).getAsJsonObject().addProperty("pending", "yes");
        assertThat(MatchSnapshotCodec.decode(new Dynamic<>(JsonOps.INSTANCE, json), Migrations.production())
                        .error())
                .isPresent();
    }
}
