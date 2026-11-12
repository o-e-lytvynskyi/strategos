package dev.strategos.core.sim.combat;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.Phase;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.UnitHurt;
import dev.strategos.core.testkit.Seeds;
import dev.strategos.core.testkit.SimHarness;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShotSystemTest {
    private static final TemplateId HORSEMAN = new TemplateId("strategos:unit/horseman");

    @Test
    void preferredLiveTargetWinsThenLowestIndexFriendlyCollateral() {
        SimHarness h = SimHarness.builder().build();
        int[] units = h.spawn(new FactionSlot(0), new Vec2d(10.5, 10.5), 3, 0);
        MatchState s = KernelAccess.state(h.core());
        for (int handle : units) s.motion().set(Handle.index(handle), 10.5, 1, 10.5, 0);
        s.spatial().rebuild(s);
        Damage frozen = new Damage(units[2], units[1], 1, 2, 3, 4, List.of(new Damage.Bonus("Infantry", 2)), true);
        ShotStore shots = rows(
                shot(1, s.tick(), 10.5, 1.9, 10.5, frozen),
                shot(
                        2,
                        s.tick(),
                        10.5,
                        1.9,
                        10.5,
                        new Damage(
                                0,
                                frozen.attacker(),
                                frozen.hack(),
                                frozen.pierce(),
                                frozen.crush(),
                                frozen.fire(),
                                frozen.bonuses(),
                                frozen.lethal())));
        ShotSystem system = new ShotSystem(shots);
        assertThat(system.phase()).isEqualTo(Phase.COMBAT);
        system.run(s);
        var damage = s.takeDamage();
        assertThat(damage).hasSize(2);
        assertThat(damage.get(0)).isEqualTo(frozen);
        assertThat(damage.get(1))
                .isEqualTo(new Damage(
                        units[0],
                        frozen.attacker(),
                        frozen.hack(),
                        frozen.pierce(),
                        frozen.crush(),
                        frozen.fire(),
                        frozen.bonuses(),
                        frozen.lethal()));
        system.run(s);
        assertThat(s.takeDamage()).isEmpty();
    }

    @Test
    void circularFootprintRejectsBoxCornersAndHeightSeparatesBridgeLayers() {
        SimHarness h = SimHarness.builder().build();
        int[] units = h.spawn(new FactionSlot(0), new Vec2d(10.5, 10.5), 2, 0);
        MatchState s = KernelAccess.state(h.core());
        s.motion().set(Handle.index(units[0]), 10.5, 1, 10.5, 0);
        s.motion().set(Handle.index(units[1]), 10.5, 5, 10.5, 0);
        s.spatial().rebuild(s);
        Damage d = Damage.of(units[0], units[1], DamageType.PIERCE, 1);
        ShotStore shots = rows(
                shot(1, s.tick(), 10.8, 1.9, 10.8, d),
                shot(2, s.tick(), 10.5, 5.9, 10.5, d),
                shot(3, s.tick(), 10.5, 4, 10.5, d));
        new ShotSystem(shots).run(s);
        assertThat(s.takeDamage()).extracting(Damage::target).containsExactly(units[1]);
    }

    @Test
    void deadShooterAndReusedTargetGenerationDoNotCancelOrPreferAReplacement() {
        SimHarness h = SimHarness.builder().build();
        int[] units = h.spawn(new FactionSlot(0), new Vec2d(10.5, 10.5), 3, 0);
        MatchState s = KernelAccess.state(h.core());
        Damage d = Damage.of(units[2], units[0], DamageType.PIERCE, 3);
        ShotStore shots = rows(shot(1, s.tick(), 10.5, 1.9, 10.5, d));
        s.removeUnit(units[2]);
        int replacement = h.spawn(new FactionSlot(0), new Vec2d(10.5, 10.5), 1, 0)[0];
        assertThat(Handle.index(replacement)).isEqualTo(Handle.index(units[2]));
        assertThat(replacement).isNotEqualTo(units[2]);
        s.removeUnit(units[0]);
        for (int handle : new int[] {units[1], replacement}) s.motion().set(Handle.index(handle), 10.5, 1, 10.5, 0);
        s.spatial().rebuild(s);
        new ShotSystem(shots).run(s);
        assertThat(s.takeDamage()).containsExactly(Damage.of(units[1], units[0], DamageType.PIERCE, 3));
    }

    @Test
    void fiveHundredStationaryShotsFollowThePreselectedExactBinomialModel() {
        MatchState s = volley(0);
        var damage = s.takeDamage();
        double sigma = 2.25 * 20 / 100;
        double p = 1 - StrictMath.exp(-0.35 * 0.35 / (2 * sigma * sigma));
        int[] interval = exactInterval(500, p, 0.001);
        assertThat(damage.size()).isBetween(interval[0], interval[1]);
        assertThat(damage).allSatisfy(d -> {
            assertThat(d.target()).isEqualTo(1048577);
            assertThat(d.pierce()).isZero();
        });
    }

    @Test
    void theIndependentlyVerifiedVolleyPinsHitsAndExactHealthDamage() {
        MatchState s = volley(1f / 256);
        new CombatSystem().run(s);
        int target = 1048577;
        assertThat(s.entities().isAlive(target)).isTrue();
        assertThat(s.health().damage(Handle.index(target))).isEqualTo(0.4921875);
        assertThat(s.outputs().stream()
                        .filter(UnitHurt.class::isInstance)
                        .map(UnitHurt.class::cast)
                        .toList())
                .hasSize(126)
                .allSatisfy(hit -> {
                    assertThat(hit.handle()).isEqualTo(target);
                    assertThat(hit.attacker()).isEqualTo(1048576);
                    assertThat(hit.amount()).isEqualTo(1f / 256);
                });
    }

    @Test
    void aShotLandingInAHorsemansWiderBodyHitsItWhereInfantryWouldBeMissed() {
        SimHarness h = SimHarness.builder().build();
        int shooter = h.spawn(new FactionSlot(0), new Vec2d(5.5, 10.5), 1, 0)[0];
        int gone = h.spawn(new FactionSlot(0), new Vec2d(6.5, 10.5), 1, 0)[0];
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(HORSEMAN, new FactionSlot(0), new Vec2d(20.5, 10.5), 1, 0));
        int horseman = h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .map(UnitCreated.class::cast)
                .findFirst()
                .orElseThrow()
                .handle();
        MatchState s = KernelAccess.state(h.core());
        s.removeUnit(gone);
        s.motion().set(Handle.index(horseman), 20.5, 1, 10.5, 0);
        s.spatial().rebuild(s);
        Damage d = Damage.of(gone, shooter, DamageType.PIERCE, 1);
        new ShotSystem(rows(shot(1, s.tick(), 21.1, 1.9, 10.5, d))).run(s);
        assertThat(s.takeDamage()).extracting(Damage::target).containsExactly(horseman);
    }

    @Test
    void withoutALiveHorsemanTheSameLandingBesideAnInfantryMissesAsBefore() {
        SimHarness h = SimHarness.builder().build();
        int shooter = h.spawn(new FactionSlot(0), new Vec2d(5.5, 10.5), 1, 0)[0];
        int gone = h.spawn(new FactionSlot(0), new Vec2d(6.5, 10.5), 1, 0)[0];
        int foot = h.spawn(new FactionSlot(0), new Vec2d(20.5, 10.5), 1, 0)[0];
        MatchState s = KernelAccess.state(h.core());
        s.removeUnit(gone);
        s.motion().set(Handle.index(foot), 20.5, 1, 10.5, 0);
        s.spatial().rebuild(s);
        Damage d = Damage.of(gone, shooter, DamageType.PIERCE, 1);
        new ShotSystem(rows(shot(1, s.tick(), 21.1, 1.9, 10.5, d))).run(s);
        assertThat(s.takeDamage()).isEmpty();
    }

    private static MatchState volley(float amount) {
        SimHarness h = SimHarness.builder().build();
        int shooter = h.spawn(new FactionSlot(0), new Vec2d(5.5, 10.5), 1, 0)[0];
        int target = h.spawn(new FactionSlot(0), new Vec2d(25.5, 10.5), 1, 0)[0];
        MatchState s = KernelAccess.state(h.core());
        ShotStore shots = new ShotStore();
        for (int i = 0; i < 500; i++)
            shots.launch(
                    Seeds.first("canary"),
                    s.tick(),
                    new ShotStore.Launch(
                            5.5,
                            10.5,
                            25.5,
                            1.9,
                            10.5,
                            0,
                            0,
                            1,
                            2.25,
                            Damage.of(target, shooter, DamageType.PIERCE, amount)));
        h.runTicks(20);
        new ShotSystem(shots).run(s);
        assertThat(shooter).isEqualTo(1048576);
        assertThat(target).isEqualTo(1048577);
        assertThat(s.tick()).isEqualTo(22);
        assertThat(shots.nextId()).isEqualTo(501);
        return s;
    }

    /** Equal-tail exact binomial interval, alpha selected before sampling; no normal approximation or fitted bound. */
    private static int[] exactInterval(int n, double p, double alpha) {
        double probability = StrictMath.pow(1 - p, n);
        double cumulative = 0;
        int lower = -1;
        for (int k = 0; k <= n; k++) {
            cumulative += probability;
            if (lower < 0 && cumulative >= alpha / 2) lower = k;
            if (cumulative >= 1 - alpha / 2) return new int[] {lower, k};
            probability *= (n - k) * p / ((k + 1) * (1 - p));
        }
        throw new AssertionError("binomial mass did not reach its upper quantile");
    }

    private static ShotStore rows(ShotStore.Shot... rows) {
        ShotStore store = new ShotStore();
        store.restore(new ShotStore.Snapshot(rows[rows.length - 1].id() + 1, List.of(rows)));
        return store;
    }

    private static ShotStore.Shot shot(long id, long tick, double x, double y, double z, Damage damage) {
        return new ShotStore.Shot(id, damage.attacker(), damage.target(), tick, x, y, z, damage);
    }
}
