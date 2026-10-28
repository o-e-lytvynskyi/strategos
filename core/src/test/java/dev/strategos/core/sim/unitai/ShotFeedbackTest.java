package dev.strategos.core.sim.unitai;

import static org.assertj.core.api.Assertions.assertThat;

import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.CorePorts;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.ShotFired;
import dev.strategos.core.sim.ShotLanded;
import dev.strategos.core.sim.ShotRequested;
import dev.strategos.core.sim.TickInputs;
import dev.strategos.core.sim.TickOutputs;
import dev.strategos.core.sim.combat.Damage;
import dev.strategos.core.sim.combat.DamageType;
import dev.strategos.core.sim.combat.ShotStore;
import dev.strategos.core.sim.persist.MatchSnapshotCodec;
import dev.strategos.core.sim.persist.Migrations;
import dev.strategos.core.testkit.SimHarness;
import java.util.List;
import org.junit.jupiter.api.Test;

/** CMB-04 feedback: a shot freezes its release, announces its launch, and always announces its landing. */
class ShotFeedbackTest {
    private static final FactionSlot F0 = new FactionSlot(0);
    private static final FactionSlot F1 = new FactionSlot(1);

    @Test
    void aLaunchFreezesTheShootersPositionAndTickAndIsAnnounced() {
        SimHarness h = RangedOrdersTest.harness(30, 1, 20, 0);
        int shooter = RangedOrdersTest.spawn(h, RangedOrdersTest.RANGER, F0, 5.5, 10.5);
        int target = RangedOrdersTest.spawn(h, RangedOrdersTest.DUMMY, F1, 20.5, 10.5);
        MatchState s = KernelAccess.state(h.core());
        s.bus().publish(request(shooter, target));
        h.step();
        ShotStore.Shot shot = s.shots().shot(0);
        int i = Handle.index(shooter);
        double height = s.templates().byIndex(s.identity().templateIndex(i)).height();
        assertThat(shot.release())
                .isEqualTo(new ShotStore.Release(
                        s.motion().x(i),
                        s.motion().y(i) + height * 0.75,
                        s.motion().z(i),
                        s.tick()));
        assertThat(h.events(ShotFired.class))
                .containsExactly(new ShotFired(
                        shooter,
                        target,
                        shot.release().x(),
                        shot.release().y(),
                        shot.release().z(),
                        shot.landingX(),
                        shot.landingY(),
                        shot.landingZ(),
                        s.tick(),
                        shot.landTick()));
    }

    @Test
    void aHitNamesTheStruckUnitAndAMissNamesNone() {
        SimHarness h = RangedOrdersTest.harness(30, 1, 20, 0);
        int shooter = RangedOrdersTest.spawn(h, RangedOrdersTest.RANGER, F0, 5.5, 10.5);
        int target = RangedOrdersTest.spawn(h, RangedOrdersTest.DUMMY, F1, 20.5, 10.5);
        MatchState s = KernelAccess.state(h.core());
        s.bus().publish(request(shooter, target));
        h.step();
        long land = s.shots().shot(0).landTick();
        while (h.tick() < land) h.step();
        assertThat(h.events(ShotLanded.class)).singleElement().satisfies(landed -> {
            assertThat(landed.hit()).isEqualTo(target);
            assertThat(landed.landTick()).isEqualTo(land);
            assertThat(landed.from()).isNotNull();
        });

        // a second shot at a target that has since walked off its landing point: a miss
        s.bus().publish(request(shooter, target));
        h.step();
        ShotStore.Shot second = s.shots().shot(0);
        s.motion().set(Handle.index(target), 40.5, s.motion().y(Handle.index(target)), 40.5, 0);
        s.spatial().rebuild(s);
        while (h.tick() < second.landTick()) h.step();
        assertThat(h.events(ShotLanded.class)).hasSize(2);
        assertThat(h.events(ShotLanded.class).get(1).hit()).isEqualTo(Handle.NONE);
    }

    @Test
    void aDeadShooterStillYieldsTheLandingWithItsFrozenRelease() {
        SimHarness h = RangedOrdersTest.harness(30, 1, 20, 0);
        int shooter = RangedOrdersTest.spawn(h, RangedOrdersTest.RANGER, F0, 5.5, 10.5);
        int target = RangedOrdersTest.spawn(h, RangedOrdersTest.DUMMY, F1, 20.5, 10.5);
        MatchState s = KernelAccess.state(h.core());
        s.bus().publish(request(shooter, target));
        h.step();
        ShotStore.Shot shot = s.shots().shot(0);
        s.removeUnit(shooter);
        while (h.tick() < shot.landTick()) h.step();
        assertThat(h.events(ShotLanded.class)).singleElement().satisfies(landed -> {
            assertThat(landed.shooter()).isEqualTo(shooter);
            assertThat(landed.from()).isEqualTo(shot.release());
        });
    }

    @Test
    void aSaveAndRestoreInFlightGivesTheSameLanding() {
        SimHarness h = RangedOrdersTest.harness(30, 1, 20, 0);
        int shooter = RangedOrdersTest.spawn(h, RangedOrdersTest.RANGER, F0, 5.5, 10.5);
        int target = RangedOrdersTest.spawn(h, RangedOrdersTest.DUMMY, F1, 20.5, 10.5);
        MatchState s = KernelAccess.state(h.core());
        s.bus().publish(request(shooter, target));
        h.step();
        long land = s.shots().shot(0).landTick();
        h.step();
        MatchSnapshot saved = MatchSnapshotCodec.decode(
                        new Dynamic<>(
                                JsonOps.INSTANCE,
                                MatchSnapshotCodec.encode(
                                        JsonOps.INSTANCE, h.core().snapshot())),
                        Migrations.production())
                .getOrThrow();
        MatchCore resumed = MatchCore.restore(saved, RangedOrdersTest.templates(30, 1, 20, 0), CorePorts.of(() -> 0L));
        while (h.tick() < land) h.step();
        TickOutputs out = null;
        for (long t = saved.tick() + 1; t <= land; t++) out = resumed.step(new TickInputs(t, List.of()));
        assertThat(out).isNotNull();
        assertThat(out.events().stream().filter(ShotLanded.class::isInstance).toList())
                .isEqualTo(h.events(ShotLanded.class));
        assertThat(h.events(ShotLanded.class)).hasSize(1);
    }

    private static ShotRequested request(int shooter, int target) {
        return new ShotRequested(shooter, target, Damage.of(target, shooter, DamageType.PIERCE, 1f / 256), 30, 0.5, 0);
    }
}
