package dev.strategos.core.sim.unitai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.ShotRequested;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.UnitHurt;
import dev.strategos.core.sim.UnitStruck;
import dev.strategos.core.sim.combat.Damage;
import dev.strategos.core.sim.combat.DamageType;
import dev.strategos.core.sim.combat.ShotStore;
import dev.strategos.core.testkit.SimHarness;
import org.junit.jupiter.api.Test;

class MovingShotTest {
    private static final FactionSlot F0 = new FactionSlot(0);
    private static final FactionSlot F1 = new FactionSlot(1);

    @Test
    void releaseUsesPostMovementTargetAndActualFlatDisplacementForExactLead() {
        SimHarness h = RangedOrdersTest.harness(30, 1, 20, 0);
        int shooter = RangedOrdersTest.spawn(h, RangedOrdersTest.RANGER, F0, 5.5, 10.5);
        int target = RangedOrdersTest.spawn(h, RangedOrdersTest.DUMMY, F1, 20.5, 10.5);
        MatchState s = KernelAccess.state(h.core());
        double beforeX = s.motion().x(Handle.index(target));
        double beforeZ = s.motion().z(Handle.index(target));
        h.submit(new PlayerSlot(0), Move.replace(UnitSet.of(shooter), new Vec2d(30.5, 10.5)));
        h.submit(new PlayerSlot(1), Move.replace(UnitSet.of(target), new Vec2d(20.5, 45.5)));
        s.bus().publish(request(shooter, target, 30, 0));
        h.step();
        assertThat(s.shots().count()).isEqualTo(1);
        ShotStore.Shot shot = s.shots().shot(0);
        double afterX = s.motion().x(Handle.index(target));
        double afterZ = s.motion().z(Handle.index(target));
        assertThat(afterX).isEqualTo(beforeX);
        assertThat(afterZ).isGreaterThan(beforeZ);
        assertThat(s.motion().x(Handle.index(shooter))).isGreaterThan(5.5);
        long flight = shot.landTick() - s.tick();
        assertThat(flight).isEqualTo((long) Math.ceil(Math.hypot(
                        afterX - s.motion().x(Handle.index(shooter)),
                        afterZ - s.motion().z(Handle.index(shooter)))
                / 0.5));
        assertThat(shot.landingX()).isEqualTo(afterX + (afterX - beforeX) * flight);
        assertThat(shot.landingY())
                .isEqualTo(s.motion().y(Handle.index(target))
                        + s.templates()
                                        .byIndex(s.identity().templateIndex(Handle.index(target)))
                                        .height()
                                / 2);
        assertThat(shot.landingZ()).isEqualTo(afterZ + (afterZ - beforeZ) * flight);
        while (h.tick() < shot.landTick()) h.step();
        assertThat(s.motion().x(Handle.index(target))).isEqualTo(shot.landingX());
        assertThat(s.motion().z(Handle.index(target))).isEqualTo(shot.landingZ());
        assertThat(h.events(UnitHurt.class)).anySatisfy(hit -> {
            assertThat(hit.handle()).isEqualTo(target);
            assertThat(hit.attacker()).isEqualTo(shooter);
        });
    }

    @Test
    void directionChangingLongRangeTargetHasMuchLowerHitRateThanStationary() {
        int stationary = volley(false);
        int changed = volley(true);
        assertThat(stationary).isGreaterThanOrEqualTo(25);
        assertThat(changed).isLessThan(stationary / 2);
    }

    @Test
    void releasedPacketStillHitsAfterShooterDies() {
        SimHarness h = RangedOrdersTest.harness(30, 1, 20, 0);
        int shooter = RangedOrdersTest.spawn(h, RangedOrdersTest.RANGER, F0, 5.5, 10.5);
        int target = RangedOrdersTest.spawn(h, RangedOrdersTest.DUMMY, F1, 20.5, 10.5);
        MatchState s = KernelAccess.state(h.core());
        s.bus().publish(request(shooter, target, 30, 0));
        h.step();
        long land = s.shots().shot(0).landTick();
        s.removeUnit(shooter);
        while (h.tick() < land) h.step();
        assertThat(h.events(UnitHurt.class)).anySatisfy(hit -> {
            assertThat(hit.handle()).isEqualTo(target);
            assertThat(hit.attacker()).isEqualTo(shooter);
            assertThat(hit.amount()).isEqualTo(1f / 256);
        });
    }

    @Test
    void targetWalkingOutOfRangeCancelsTheIntentBeforeLaunch() {
        SimHarness h = RangedOrdersTest.harness(30, 1, 20, 0);
        int shooter = RangedOrdersTest.spawn(h, RangedOrdersTest.RANGER, F0, 5.5, 10.5);
        int target = RangedOrdersTest.spawn(h, RangedOrdersTest.DUMMY, F1, 10.5, 10.5);
        MatchState s = KernelAccess.state(h.core());
        h.submit(new PlayerSlot(1), Move.replace(UnitSet.of(target), new Vec2d(20.5, 10.5)));
        s.bus().publish(request(shooter, target, 4.4, 0));
        h.step();
        assertThat(s.shots().pristine()).isTrue();
        assertThat(h.events(UnitStruck.class)).isEmpty();
    }

    @Test
    void deadShooterAndRecycledTargetAreRejectedBeforeRelease() {
        SimHarness h = RangedOrdersTest.harness(30, 1, 20, 0);
        int shooter = RangedOrdersTest.spawn(h, RangedOrdersTest.RANGER, F0, 5.5, 10.5);
        int target = RangedOrdersTest.spawn(h, RangedOrdersTest.DUMMY, F1, 20.5, 10.5);
        MatchState s = KernelAccess.state(h.core());
        s.bus().publish(request(shooter, target, 30, 0));
        s.removeUnit(shooter);
        h.step();
        assertThat(s.shots().pristine()).isTrue();

        int nextShooter = RangedOrdersTest.spawn(h, RangedOrdersTest.RANGER, F0, 5.5, 10.5);
        s.bus().publish(request(nextShooter, target, 30, 0));
        s.removeUnit(target);
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(RangedOrdersTest.DUMMY, F1, new Vec2d(20.5, 10.5), 1, 0));
        int replacement = h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .map(UnitCreated.class::cast)
                .mapToInt(UnitCreated::handle)
                .findFirst()
                .orElseThrow();
        assertThat(Handle.index(replacement)).isEqualTo(Handle.index(target));
        assertThat(replacement).isNotEqualTo(target);
        assertThat(s.shots().pristine()).isTrue();
    }

    private static int volley(boolean reverse) {
        SimHarness h = RangedOrdersTest.harness(30, 1, 20, 2.25);
        int shooter = RangedOrdersTest.spawn(h, RangedOrdersTest.RANGER, F0, 5.5, 10.5);
        int target = RangedOrdersTest.spawn(h, RangedOrdersTest.DUMMY, F1, 25.5, 10.5);
        MatchState s = KernelAccess.state(h.core());
        if (reverse) h.submit(new PlayerSlot(1), Move.replace(UnitSet.of(target), new Vec2d(45.5, 10.5)));
        for (int i = 0; i < 200; i++) s.bus().publish(request(shooter, target, 30, 2.25));
        h.step();
        long land = s.shots().shot(0).landTick();
        if (reverse) {
            h.runTicks(10);
            h.submit(new PlayerSlot(1), Move.replace(UnitSet.of(target), new Vec2d(5.5, 10.5)));
        }
        while (h.tick() <= land) h.step();
        return (int) h.events(UnitHurt.class).stream()
                .filter(hit -> hit.handle() == target)
                .count();
    }

    private static ShotRequested request(int shooter, int target, double range, double spread) {
        return new ShotRequested(
                shooter, target, Damage.of(target, shooter, DamageType.PIERCE, 1f / 256), range, 0.5, spread);
    }
}
