package dev.strategos.core.sim;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.combat.Damage;
import dev.strategos.core.sim.combat.DamageType;
import dev.strategos.core.sim.combat.ShotStore;
import dev.strategos.core.sim.diplomacy.DiplomacyState;
import dev.strategos.core.sim.persist.MatchSnapshotCodec;
import dev.strategos.core.sim.persist.Migrations;
import dev.strategos.core.testkit.SimHarness;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Current origins are required for locked-team shots; legacy provenance is recovered only when it is knowable. */
class FrozenDamagePersistenceTest {
    private static SimHarness shot(boolean locked, boolean deadSource) {
        SimHarness h = SimHarness.builder().build();
        if (locked)
            h.schedule(
                    1,
                    new DiplomacySetup(DiplomacyState.identity(2)
                            .withTeam(new FactionSlot(1), 0)
                            .lockTeams()
                            .snapshot()));
        int source = h.spawn(new FactionSlot(0), new Vec2d(8.5, 8.5), 1, 0)[0];
        int target = h.spawn(new FactionSlot(1), new Vec2d(20.5, 8.5), 1, 0)[0];
        MatchState s = KernelAccess.state(h.core());
        Damage damage = Damage.of(target, source, DamageType.PIERCE, 10);
        if (locked) damage = damage.withSourceFaction(0);
        s.shots()
                .restore(new ShotStore.Snapshot(
                        2, List.of(new ShotStore.Shot(1, source, target, s.tick() + 4, 20.5, 1.9, 8.5, damage))));
        if (deadSource) s.removeUnit(source);
        return h;
    }

    private static JsonObject json(SimHarness h) {
        return MatchSnapshotCodec.encode(JsonOps.INSTANCE, h.core().snapshot()).getAsJsonObject();
    }

    private static JsonObject damage(JsonObject root) {
        return root.getAsJsonObject("play")
                .getAsJsonObject("shots")
                .getAsJsonArray("rows")
                .get(0)
                .getAsJsonObject()
                .getAsJsonObject("damage");
    }

    private static MatchCore restore(JsonObject root) {
        MatchSnapshot decoded = MatchSnapshotCodec.decode(
                        new Dynamic<>(JsonOps.INSTANCE, root), Migrations.production())
                .getOrThrow();
        return MatchCore.restore(decoded, TemplateSet.builtin(), CorePorts.of(() -> 0L));
    }

    @Test
    void currentSnapshotRetainsDeadSourceProvenanceAndContinuesWithEqualHashes() {
        SimHarness h = shot(true, true);
        JsonObject encoded = json(h);
        assertThat(encoded.get("v").getAsInt()).isEqualTo(5);
        assertThat(damage(encoded).get("sourceFaction").getAsInt()).isZero();
        MatchCore back = restore(encoded);
        assertThat(back.stateHash()).isEqualTo(h.stateHash());
        assertThat(back.snapshot().play().shots().rows().getFirst().damage().sourceFaction())
                .isZero();
        for (int tick = 0; tick < 6; tick++) {
            h.step();
            back.step(List.of());
            assertThat(back.stateHash()).as("continuation tick %s", tick).isEqualTo(h.stateHash());
        }
    }

    @Test
    void currentLockedShotCannotInferMissingOriginFromALiveSource() {
        JsonObject missing = json(shot(true, false));
        damage(missing).remove("sourceFaction");
        var decoded = MatchSnapshotCodec.decode(new Dynamic<>(JsonOps.INSTANCE, missing), Migrations.production());
        assertThat(decoded.error()).isPresent();
        assertThat(decoded.error().orElseThrow().message()).contains("source");
    }

    @Test
    void aLegacyLockedShotRecoversOnlyTheMatchingLiveSourceGeneration() {
        SimHarness h = shot(true, false);
        JsonObject old = json(h);
        old.addProperty("v", 4);
        damage(old).remove("sourceFaction");
        MatchCore back = restore(old);
        assertThat(back.snapshot().play().shots().rows().getFirst().damage().sourceFaction())
                .isZero();
        assertThat(back.stateHash()).isEqualTo(h.stateHash());
    }

    @Test
    void aLegacyLockedShotWithADeadSourceFailsRatherThanGuessingOrDroppingTheShot() {
        JsonObject old = json(shot(true, true));
        old.addProperty("v", 4);
        damage(old).remove("sourceFaction");
        var decoded = MatchSnapshotCodec.decode(new Dynamic<>(JsonOps.INSTANCE, old), Migrations.production());
        assertThat(decoded.error()).isPresent();
        assertThat(decoded.error().orElseThrow().message()).contains("source");
    }

    @Test
    void unlockedLegacyShotHashesAndMeaningArePreserved() {
        SimHarness h = shot(false, true);
        JsonObject old = json(h);
        old.addProperty("v", 4);
        damage(old).remove("sourceFaction");
        MatchCore back = restore(old);
        assertThat(back.stateHash()).isEqualTo(h.stateHash());
        for (int tick = 0; tick < 6; tick++) {
            h.step();
            back.step(List.of());
            assertThat(back.stateHash()).isEqualTo(h.stateHash());
        }
    }

    @Test
    void frozenSourceFactionContributesToTheStateHash() {
        SimHarness h = shot(true, true);
        MatchState s = KernelAccess.state(h.core());
        long before = h.stateHash();
        ShotStore.Shot original = s.shots().shot(0);
        s.shots()
                .restore(new ShotStore.Snapshot(
                        2,
                        List.of(new ShotStore.Shot(
                                original.id(),
                                original.shooter(),
                                original.target(),
                                original.landTick(),
                                original.landingX(),
                                original.landingY(),
                                original.landingZ(),
                                original.damage().withSourceFaction(1),
                                original.release()))));
        assertThat(h.stateHash()).isNotEqualTo(before);
    }
}
