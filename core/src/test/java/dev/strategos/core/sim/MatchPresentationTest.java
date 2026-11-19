package dev.strategos.core.sim;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.FactionPresentation;
import dev.strategos.core.sim.journal.JournalExport;
import dev.strategos.core.sim.journal.JournalFormat;
import dev.strategos.core.sim.journal.ReplayDriver;
import dev.strategos.core.sim.persist.MatchSnapshotCodec;
import dev.strategos.core.sim.persist.Migrations;
import dev.strategos.core.sim.persist.SchemaVersion;
import dev.strategos.core.testkit.SimHarness;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchPresentationTest {
    private static FactionPresentation chosen() {
        return new FactionPresentation(List.of(
                new FactionPresentation.Style("strategos:placeholder", 14),
                new FactionPresentation.Style("strategos:placeholder", 11)));
    }

    @Test
    void aFirstTickSetupInstallsDetachedPresentationOnceAndChangesTheHash() {
        var normal = SimHarness.builder().factions(2).build();
        var selected = SimHarness.builder().factions(2).build();
        var old = selected.queries().presentation();
        selected.schedule(1, new PresentationSetup(chosen()));
        selected.step();
        normal.step();
        assertThat(selected.queries().presentation()).isEqualTo(chosen());
        assertThat(old).isEqualTo(FactionPresentation.legacy(2));
        assertThat(selected.stateHash()).isNotEqualTo(normal.stateHash());
        selected.schedule(2, new PresentationSetup(FactionPresentation.legacy(2)));
        selected.step();
        assertThat(selected.queries().presentation()).isEqualTo(chosen());
    }

    @Test
    void duplicateAndOutOfMatchSetupsCannotReplaceTheFirstValidPresentation() {
        var match = SimHarness.builder().factions(2).build();
        match.schedule(1, new PresentationSetup(FactionPresentation.legacy(3)));
        match.schedule(1, new PresentationSetup(chosen()));
        match.schedule(1, new PresentationSetup(FactionPresentation.legacy(2)));
        match.step();
        assertThat(match.queries().presentation()).isEqualTo(chosen());
        assertThat(match.replay().matches()).isTrue();
    }

    @Test
    void currentSaveAndBinaryJournalRetainPresentationAndExactContinuation() {
        var match = SimHarness.builder().factions(2).build();
        match.schedule(1, new PresentationSetup(chosen()));
        match.runTicks(3);
        JsonObject json = MatchSnapshotCodec.encode(
                        JsonOps.INSTANCE, match.core().snapshot())
                .getAsJsonObject();
        assertThat(json.get("v").getAsInt()).isEqualTo(SchemaVersion.CURRENT);
        var decoded = MatchSnapshotCodec.decode(new Dynamic<>(JsonOps.INSTANCE, json), Migrations.production())
                .getOrThrow();
        var restored = SimHarness.restore(decoded, TemplateSet.builtin());
        assertThat(restored.queries().presentation()).isEqualTo(chosen());
        assertThat(restored.stateHash()).isEqualTo(match.stateHash());
        byte[] bytes = JournalFormat.encode(new JournalExport(match.core().journal(), match.tick(), match.stateHash()));
        var replay = ReplayDriver.replay(JournalFormat.decode(bytes).journal(), TemplateSet.builtin());
        assertThat(replay.matches()).isTrue();
        assertThat(replay.finalHash()).isEqualTo(match.stateHash());
        match.runTicks(5);
        restored.runTicks(5);
        assertThat(restored.stateHash()).isEqualTo(match.stateHash());
    }

    @Test
    void schemaSixMigratesToLegacyAppearanceAndCurrentSavesCannotSilentlyLoseIt() {
        var match = SimHarness.builder().factions(2).build();
        match.runTicks(2);
        JsonObject legacy = MatchSnapshotCodec.encode(
                        JsonOps.INSTANCE, match.core().snapshot())
                .getAsJsonObject();
        legacy.addProperty("v", 6);
        legacy.getAsJsonObject("play").remove("presentation");
        var decoded = MatchSnapshotCodec.decode(new Dynamic<>(JsonOps.INSTANCE, legacy), Migrations.production())
                .getOrThrow();
        var restored = SimHarness.restore(decoded, TemplateSet.builtin());
        assertThat(restored.queries().presentation()).isEqualTo(FactionPresentation.legacy(2));
        assertThat(restored.stateHash()).isEqualTo(match.stateHash());
        legacy.addProperty("v", SchemaVersion.CURRENT);
        assertThat(MatchSnapshotCodec.decode(new Dynamic<>(JsonOps.INSTANCE, legacy), Migrations.production())
                        .error())
                .isPresent();
    }

    @Test
    void malformedCurrentPresentationAndForeignFactionCountsAreRejected() {
        var match = SimHarness.builder().factions(2).build();
        match.schedule(1, new PresentationSetup(chosen()));
        match.step();
        JsonObject original = MatchSnapshotCodec.encode(
                        JsonOps.INSTANCE, match.core().snapshot())
                .getAsJsonObject();
        var invalidColour = original.deepCopy();
        invalidColour
                .getAsJsonObject("play")
                .getAsJsonArray("presentation")
                .get(0)
                .getAsJsonObject()
                .addProperty("colour", 16);
        assertThat(MatchSnapshotCodec.decode(new Dynamic<>(JsonOps.INSTANCE, invalidColour), Migrations.production())
                        .error())
                .isPresent();
        var invalidCivilisation = original.deepCopy();
        invalidCivilisation
                .getAsJsonObject("play")
                .getAsJsonArray("presentation")
                .get(0)
                .getAsJsonObject()
                .addProperty("civilisation", "strategos:Bad");
        assertThat(MatchSnapshotCodec.decode(
                                new Dynamic<>(JsonOps.INSTANCE, invalidCivilisation), Migrations.production())
                        .error())
                .isPresent();
        var foreignCount = original.deepCopy();
        foreignCount.getAsJsonObject("play").getAsJsonArray("presentation").remove(1);
        assertThat(MatchSnapshotCodec.decode(new Dynamic<>(JsonOps.INSTANCE, foreignCount), Migrations.production())
                        .error())
                .isPresent();
        var replacedAuthority = match.core()
                .snapshot()
                .play()
                .withDiplomacy(dev.strategos.core.sim.diplomacy.DiplomacyState.identity(2)
                        .lockTeams());
        assertThat(replacedAuthority.presentation()).isEqualTo(chosen());
    }
}
