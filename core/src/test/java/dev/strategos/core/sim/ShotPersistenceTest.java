package dev.strategos.core.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonObject;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.combat.Damage;
import dev.strategos.core.sim.combat.DamageType;
import dev.strategos.core.sim.combat.ShotStore;
import dev.strategos.core.sim.persist.MatchSnapshotCodec;
import dev.strategos.core.sim.persist.Migrations;
import dev.strategos.core.testkit.Seeds;
import dev.strategos.core.testkit.SimHarness;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShotPersistenceTest {
    @Test
    void impactRunsAtLandingTickOnceThroughTheKernel() {
        SimHarness h = SimHarness.builder().seed(Seeds.first("canary")).build();
        int[] units = h.spawn(new FactionSlot(0), new Vec2d(10.5, 10.5), 2, 0);
        MatchState state = KernelAccess.state(h.core());
        state.motion().set(Handle.index(units[1]), 10.5, 1, 10.5, 0);
        long due = state.tick() + 2;
        state.shots().restore(new ShotStore.Snapshot(2, List.of(shot(1, units[0], units[1], due))));
        h.runTicks(1);
        assertThat(state.health().damage(Handle.index(units[1]))).isZero();
        h.runTicks(1);
        assertThat(state.health().damage(Handle.index(units[1]))).isPositive();
        double damage = state.health().damage(Handle.index(units[1]));
        h.runTicks(1);
        assertThat(state.health().damage(Handle.index(units[1]))).isEqualTo(damage);
        assertThat(state.shots().count()).isZero();
    }

    @Test
    void shotStateRoundTripsAndContinuesWithEqualHashes() {
        SimHarness h = SimHarness.builder().seed(Seeds.first("canary")).build();
        int[] units = h.spawn(new FactionSlot(0), new Vec2d(10.5, 10.5), 2, 0);
        MatchState state = KernelAccess.state(h.core());
        state.motion().set(Handle.index(units[1]), 10.5, 1, 10.5, 0);
        Damage frozen = new Damage(
                units[1], units[0], 0.5f, 1, 0.25f, 0.125f, List.of(new Damage.Bonus("Infantry", 1.5f)), false);
        state.shots()
                .restore(new ShotStore.Snapshot(
                        3,
                        List.of(new ShotStore.Shot(2, units[0], units[1], state.tick() + 2, 10.5, 1.9, 10.5, frozen))));
        MatchSnapshot saved = roundTrip(h.core().snapshot());
        MatchCore resumed = MatchCore.restore(saved, TemplateSet.builtin(), CorePorts.of(() -> 0L));
        assertThat(saved.play().shots()).isEqualTo(state.shots().save());
        assertThat(saved.play().shots().rows().get(0).damage()).isEqualTo(frozen);
        assertThat(resumed.stateHash()).isEqualTo(h.stateHash());
        for (int i = 0; i < 3; i++) {
            long tick = h.tick() + 1;
            h.core().step(new TickInputs(tick, List.of()));
            resumed.step(new TickInputs(tick, List.of()));
            assertThat(resumed.stateHash()).isEqualTo(h.stateHash());
        }
        assertThat(KernelAccess.state(resumed).health().damage(Handle.index(units[1])))
                .isEqualTo(state.health().damage(Handle.index(units[1])))
                .isPositive();
    }

    @Test
    void allocatorHistoryAffectsHashAndSurvivesAnEmptySave() {
        SimHarness h = SimHarness.builder().seed(Seeds.first("canary")).build();
        long pristine = h.stateHash();
        JsonObject old =
                MatchSnapshotCodec.encode(JsonOps.INSTANCE, h.core().snapshot()).getAsJsonObject();
        assertThat(old.has("play")).isFalse();
        MatchState state = KernelAccess.state(h.core());
        state.shots().restore(new ShotStore.Snapshot(5, List.of()));
        assertThat(h.stateHash()).isNotEqualTo(pristine);
        MatchSnapshot saved = roundTrip(h.core().snapshot());
        assertThat(saved.play().shots().nextId()).isEqualTo(5);
        MatchCore resumed = MatchCore.restore(saved, TemplateSet.builtin(), CorePorts.of(() -> 0L));
        assertThat(resumed.stateHash()).isEqualTo(h.stateHash());
        ShotStore.Launch launch =
                new ShotStore.Launch(0, 0, 10, 1, 0, 0, 0, 1, 2.25, Damage.of(0, 0, DamageType.PIERCE, 1));
        assertThat(KernelAccess.state(resumed).shots().launch(Seeds.first("canary"), 7, launch))
                .isEqualTo(state.shots().launch(Seeds.first("canary"), 7, launch));
    }

    @Test
    void savedRowsAreImmutableAndDeadShooterCanBeRestored() {
        SimHarness h = SimHarness.builder().seed(Seeds.first("canary")).build();
        int[] units = h.spawn(new FactionSlot(0), new Vec2d(10.5, 10.5), 2, 0);
        MatchState state = KernelAccess.state(h.core());
        state.motion().set(Handle.index(units[1]), 10.5, 1, 10.5, 0);
        state.shots().restore(new ShotStore.Snapshot(2, List.of(shot(1, units[0], units[1], state.tick() + 2))));
        state.removeUnit(units[0]);
        MatchSnapshot saved = roundTrip(h.core().snapshot());
        assertThatThrownBy(() -> saved.play().shots().rows().add(shot(1, 0, 0, 1)))
                .isInstanceOf(UnsupportedOperationException.class);
        MatchCore resumed = MatchCore.restore(saved, TemplateSet.builtin(), CorePorts.of(() -> 0L));
        assertThat(KernelAccess.state(resumed).shots().shot(0).shooter()).isEqualTo(units[0]);
        resumed.step(new TickInputs(saved.tick() + 1, List.of()));
        TickOutputs landing = resumed.step(new TickInputs(saved.tick() + 2, List.of()));
        assertThat(landing.events())
                .filteredOn(UnitHurt.class::isInstance)
                .containsExactly(new UnitHurt(units[1], units[0], 1));
        assertThat(KernelAccess.state(resumed).health().damage(Handle.index(units[1])))
                .isEqualTo(1);
    }

    @Test
    void resolvedThenRestoredStoreKeepsAllocatorAimAndNextImpact() {
        long seed = Seeds.first("canary");
        SimHarness h = SimHarness.builder().seed(seed).build();
        int[] units = h.spawn(new FactionSlot(0), new Vec2d(10.5, 10.5), 2, 0);
        MatchState original = KernelAccess.state(h.core());
        original.motion().set(Handle.index(units[1]), 10.5, 1, 10.5, 0);
        ShotStore.Launch first = new ShotStore.Launch(
                8.5, 10.5, 10.5, 1.9, 10.5, 0, 0, 1, 0, Damage.of(units[1], units[0], DamageType.PIERCE, 1));
        assertThat(original.shots().launch(seed, original.tick(), first).id()).isEqualTo(1);
        h.runTicks(2);
        assertThat(original.shots().count()).isZero();
        assertThat(original.shots().nextId()).isEqualTo(2);
        assertThat(original.health().damage(Handle.index(units[1]))).isEqualTo(1);
        MatchSnapshot saved = roundTrip(h.core().snapshot());
        assertThat(saved.play().shots()).isEqualTo(new ShotStore.Snapshot(2, List.of()));
        MatchCore resumed = MatchCore.restore(saved, TemplateSet.builtin(), CorePorts.of(() -> 0L));
        MatchState resumedState = KernelAccess.state(resumed);
        assertThat(resumed.stateHash()).isEqualTo(h.stateHash());

        ShotStore.Launch second = new ShotStore.Launch(
                8.5, 10.5, 10.5, 1.9, 10.5, 0, 0, 1, 0.5, Damage.of(units[1], units[0], DamageType.PIERCE, 1));
        ShotStore.Shot launched = original.shots().launch(seed, original.tick(), second);
        assertThat(launched.id()).isEqualTo(2);
        assertThat(resumedState.shots().launch(seed, resumedState.tick(), second))
                .isEqualTo(launched);
        assertThat(resumed.stateHash()).isEqualTo(h.stateHash());
        for (int i = 0; i < 3; i++) {
            long tick = h.tick() + 1;
            h.core().step(new TickInputs(tick, List.of()));
            resumed.step(new TickInputs(tick, List.of()));
            assertThat(resumed.stateHash()).as("tick %d", tick).isEqualTo(h.stateHash());
            assertThat(resumedState.health().damage(Handle.index(units[1])))
                    .isEqualTo(original.health().damage(Handle.index(units[1])));
        }
        assertThat(original.health().damage(Handle.index(units[1]))).isEqualTo(2);
        assertThat(original.shots().nextId()).isEqualTo(3);
        assertThat(resumedState.shots().nextId()).isEqualTo(3);
    }

    @Test
    void malformedRowsDecodeAsErrors() {
        SimHarness h = SimHarness.builder().build();
        int[] units = h.spawn(new FactionSlot(0), new Vec2d(10.5, 10.5), 2, 0);
        MatchState state = KernelAccess.state(h.core());
        state.shots()
                .restore(new ShotStore.Snapshot(
                        3, List.of(shot(1, units[0], units[1], 5), shot(2, units[0], units[1], 6))));
        JsonObject good =
                MatchSnapshotCodec.encode(JsonOps.INSTANCE, h.core().snapshot()).getAsJsonObject();
        List<JsonObject> bad = new ArrayList<>();
        JsonObject duplicate = good.deepCopy();
        duplicate
                .getAsJsonObject("play")
                .getAsJsonObject("shots")
                .getAsJsonArray("rows")
                .get(1)
                .getAsJsonObject()
                .addProperty("id", 1);
        bad.add(duplicate);
        JsonObject mismatch = good.deepCopy();
        mismatch.getAsJsonObject("play")
                .getAsJsonObject("shots")
                .getAsJsonArray("rows")
                .get(0)
                .getAsJsonObject()
                .addProperty("target", 0);
        bad.add(mismatch);
        JsonObject invalid = good.deepCopy();
        invalid.getAsJsonObject("play").getAsJsonObject("shots").addProperty("nextId", 0);
        bad.add(invalid);
        for (JsonObject json : bad) {
            assertThat(MatchSnapshotCodec.decode(new Dynamic<>(JsonOps.INSTANCE, json), Migrations.production())
                            .error())
                    .isPresent();
        }
    }

    private static MatchSnapshot roundTrip(MatchSnapshot snapshot) {
        return MatchSnapshotCodec.decode(
                        new Dynamic<>(JsonOps.INSTANCE, MatchSnapshotCodec.encode(JsonOps.INSTANCE, snapshot)),
                        Migrations.production())
                .getOrThrow();
    }

    private static ShotStore.Shot shot(long id, int shooter, int target, long landTick) {
        Damage damage = Damage.of(target, shooter, DamageType.PIERCE, 1);
        return new ShotStore.Shot(id, shooter, target, landTick, 10.5, 1.9, 10.5, damage);
    }
}
