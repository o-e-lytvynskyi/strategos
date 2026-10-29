package dev.strategos.core.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Hasher;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.sim.diplomacy.DiplomacyState;
import dev.strategos.core.sim.diplomacy.DiplomacyView;
import dev.strategos.core.sim.journal.JournalExport;
import dev.strategos.core.sim.journal.JournalFormat;
import dev.strategos.core.sim.journal.ReplayDriver;
import dev.strategos.core.sim.persist.MatchSnapshotCodec;
import dev.strategos.core.sim.persist.Migrations;
import dev.strategos.core.sim.persist.SchemaVersion;
import dev.strategos.core.testkit.SimHarness;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchDiplomacyTest {
    private static final PlayerSlot P0 = new PlayerSlot(0);
    private static final PlayerSlot P1 = new PlayerSlot(1);
    private static final PlayerSlot P2 = new PlayerSlot(2);
    private static final PlayerSlot P3 = new PlayerSlot(3);
    private static final FactionSlot F0 = new FactionSlot(0);
    private static final FactionSlot F2 = new FactionSlot(2);

    @Test
    void legacyMatchesExplicitlyStartWithIdentityBindings() {
        SimHarness h = match();
        assertThat(h.queries().diplomacy().factionOf(P0)).contains(F0);
        assertThat(h.queries().diplomacy().controls(P0, F2)).isFalse();
        assertThat(h.queries().diplomacy().factionOf(P3)).contains(new FactionSlot(3));
        assertThat(h.core().snapshot().diplomacy().snapshot())
                .isEqualTo(DiplomacyState.identity(4).snapshot());
    }

    @Test
    void firstTickSetupInstallsCoopBindingsAndGrantsWithoutMutatingTheOldView() {
        SimHarness h = match();
        DiplomacyView old = h.queries().diplomacy();
        h.schedule(1, new DiplomacySetup(setup().snapshot()));
        h.step();
        assertThat(h.queries().diplomacy().factionOf(P0)).contains(F2);
        assertThat(h.queries().diplomacy().factionOf(P1)).contains(F2);
        assertThat(h.queries().diplomacy().controls(P0, F0)).isTrue();
        assertThat(h.queries().diplomacy().controls(P1, F0)).isFalse();
        assertThat(h.queries().diplomacy().factionOf(P3)).isEmpty();
        assertThat(h.queries().diplomacy().teamsLocked()).isTrue();
        assertThat(old.factionOf(P0)).contains(F0);
        assertThat(old.teamsLocked()).isFalse();
    }

    @Test
    void aLateSetupOrAnotherSetupInTheSameTickCannotReplaceAuthority() {
        SimHarness late = match();
        late.schedule(2, new DiplomacySetup(setup().snapshot()));
        late.runTicks(3);
        assertThat(late.queries().diplomacy().factionOf(P0)).contains(F0);

        SimHarness duplicate = match();
        duplicate.schedule(1, new DiplomacySetup(setup().snapshot()));
        duplicate.schedule(
                1, new DiplomacySetup(DiplomacyState.identity(4).lockTeams().snapshot()));
        duplicate.runTicks(3);
        assertThat(duplicate.queries().diplomacy().factionOf(P0)).contains(F2);
        assertThat(duplicate.replay().matches()).isTrue();
    }

    @Test
    void anOutOfMatchSetupCannotChangeFactionCountOrAuthority() {
        SimHarness h = match();
        h.schedule(1, new DiplomacySetup(DiplomacyState.identity(2).lockTeams().snapshot()));
        h.step();
        assertThat(h.queries().diplomacy().factions()).isEqualTo(4);
        assertThat(h.queries().diplomacy().factionOf(P0)).contains(F0);
        assertThat(h.queries().diplomacy().teamsLocked()).isFalse();
    }

    @Test
    void setupRequiresValidAndLockedAuthorityAndCanonicalizesSnapshotOrder() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DiplomacySetup(DiplomacyState.identity(4).snapshot()));
        var original = setup().snapshot();
        var reordered = new DiplomacyState.Snapshot(
                original.teams(),
                original.bindings().reversed(),
                original.grants().reversed(),
                true);
        assertThat(new DiplomacySetup(reordered).snapshot()).isEqualTo(original);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DiplomacySetup(new DiplomacyState.Snapshot(
                        original.teams(), original.bindings(), List.of(new DiplomacyState.Grant(F0, P3)), true)));
    }

    @Test
    void authoritySurvivesSnapshotJsonAndFurtherSimulationWithTheSameHash() {
        SimHarness h = configured();
        JsonObject encoded =
                MatchSnapshotCodec.encode(JsonOps.INSTANCE, h.core().snapshot()).getAsJsonObject();
        assertThat(encoded.get("v").getAsInt()).isEqualTo(4).isEqualTo(SchemaVersion.CURRENT);
        MatchSnapshot decoded = MatchSnapshotCodec.decode(
                        new Dynamic<>(JsonOps.INSTANCE, encoded), Migrations.production())
                .getOrThrow();
        assertThat(decoded.diplomacy().snapshot()).isEqualTo(setup().snapshot());
        assertThat(decoded.play()).isEqualTo(h.core().snapshot().play());
        SimHarness restored = SimHarness.restore(decoded, TemplateSet.builtin());
        assertThat(restored.stateHash()).isEqualTo(h.stateHash());
        assertThat(restored.queries().diplomacy().controls(P0, F0)).isTrue();
        h.runTicks(5);
        restored.runTicks(5);
        assertThat(restored.stateHash()).isEqualTo(h.stateHash());
    }

    @Test
    void schemaFourCannotSilentlyLoseDiplomacyOrAcceptDuplicateAndForeignBindings() {
        JsonObject encoded = json(configured());
        encoded.getAsJsonObject("play").remove("diplomacy");
        assertThat(MatchSnapshotCodec.decode(new Dynamic<>(JsonOps.INSTANCE, encoded), Migrations.production())
                        .error())
                .isPresent();
        JsonObject malformed = json(configured());
        var bindings =
                malformed.getAsJsonObject("play").getAsJsonObject("diplomacy").getAsJsonArray("bindings");
        bindings.add(bindings.get(0).deepCopy());
        assertThat(MatchSnapshotCodec.decode(new Dynamic<>(JsonOps.INSTANCE, malformed), Migrations.production())
                        .error())
                .isPresent();
        JsonObject foreign = json(configured());
        foreign.getAsJsonObject("play")
                .getAsJsonObject("diplomacy")
                .getAsJsonArray("teams")
                .remove(3);
        assertThat(MatchSnapshotCodec.decode(new Dynamic<>(JsonOps.INSTANCE, foreign), Migrations.production())
                        .error())
                .isPresent();
    }

    @Test
    void schemaThreeWithoutDiplomacyMigratesToExplicitIdentityInsteadOfImplicitNumericFallback() {
        SimHarness h = match();
        h.runTicks(2);
        JsonObject legacy = json(h);
        legacy.addProperty("v", 3);
        legacy.getAsJsonObject("play").remove("diplomacy");
        MatchSnapshot migrated = MatchSnapshotCodec.decode(
                        new Dynamic<>(JsonOps.INSTANCE, legacy), Migrations.production())
                .getOrThrow();
        assertThat(migrated.diplomacy().snapshot())
                .isEqualTo(DiplomacyState.identity(4).snapshot());
        assertThat(SimHarness.restore(migrated, TemplateSet.builtin()).stateHash())
                .isEqualTo(h.stateHash());
    }

    @Test
    void binaryJournalCarriesTheSetupAndReplaysToTheRecordedHash() {
        SimHarness h = configured();
        h.runTicks(8);
        var original = new JournalExport(h.core().journal(), h.tick(), h.stateHash());
        byte[] bytes = JournalFormat.encode(original);
        var decoded = JournalFormat.decode(bytes);
        assertThat(JournalFormat.encode(decoded)).isEqualTo(bytes);
        var replayed = ReplayDriver.replay(decoded.journal(), TemplateSet.builtin());
        assertThat(replayed.matches()).isTrue();
        assertThat(replayed.finalHash()).isEqualTo(h.stateHash());
    }

    @Test
    void grantsAndLocksAffectTheMatchHashAndThePrimitiveHashMatchesTheObjectHasher() {
        SimHarness granted = configured();
        SimHarness revoked = match();
        revoked.schedule(1, new DiplomacySetup(setup().revoke(P2, P0).snapshot()));
        revoked.step();
        assertThat(revoked.stateHash()).isNotEqualTo(granted.stateHash());
        DiplomacyState state = setup();
        Hasher object = new Hasher(0);
        state.hashInto(object);
        assertThat(Hasher.finish(state.mixHash(Hasher.start(0)))).isEqualTo(object.finish());
        assertThat(DiplomacyState.identity(4).isLegacyIdentity()).isTrue();
        assertThat(new DiplomacyState(4).isLegacyIdentity()).isFalse();
        assertThat(DiplomacyState.identity(4).lockTeams().isLegacyIdentity()).isFalse();
    }

    private static SimHarness match() {
        return SimHarness.builder().factions(4).build();
    }

    private static DiplomacyState setup() {
        return new DiplomacyState(4)
                .withTeam(F2, 0)
                .bind(P0, F2)
                .bind(P1, F2)
                .bind(P2, F0)
                .grant(P2, P0)
                .lockTeams();
    }

    private static SimHarness configured() {
        SimHarness h = match();
        h.schedule(1, new DiplomacySetup(setup().snapshot()));
        h.step();
        return h;
    }

    private static JsonObject json(SimHarness h) {
        JsonElement json = MatchSnapshotCodec.encode(JsonOps.INSTANCE, h.core().snapshot());
        return json.getAsJsonObject();
    }
}
