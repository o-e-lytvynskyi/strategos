package dev.strategos.core.sim;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.combat.CombatSystem;
import dev.strategos.core.sim.combat.Damage;
import dev.strategos.core.sim.combat.DamageType;
import dev.strategos.core.sim.combat.ShotStore;
import dev.strategos.core.sim.combat.ShotSystem;
import dev.strategos.core.sim.diplomacy.DiplomacyState;
import dev.strategos.core.testkit.SimHarness;
import java.util.List;
import org.junit.jupiter.api.Test;

/** T7.1: final application, not target selection, protects current allies; frozen shots retain their source. */
class AlliedDamageTest {
    private record Scene(SimHarness harness, MatchState state, int source, int ally, int enemy) {}

    private static Scene scene() {
        SimHarness h = SimHarness.builder().size(64, 64).factions(3).build();
        h.schedule(1, new DiplomacySetup(teams(true).snapshot()));
        int source = h.spawn(new FactionSlot(0), new Vec2d(4.5, 4.5), 1, 0)[0];
        int ally = h.spawn(new FactionSlot(1), new Vec2d(18.5, 4.5), 1, 0)[0];
        int enemy = h.spawn(new FactionSlot(2), new Vec2d(48.5, 48.5), 1, 0)[0];
        return new Scene(h, KernelAccess.state(h.core()), source, ally, enemy);
    }

    private static DiplomacyState teams(boolean allied) {
        return DiplomacyState.identity(3)
                .withTeam(new FactionSlot(1), allied ? 0 : 1)
                .lockTeams();
    }

    @Test
    void ownAndAlliedUnitsTakeNoHostileDamageWhileTheEnemyStillDoes() {
        Scene f = scene();
        MatchState s = f.state();
        double own = s.health().damage(Handle.index(f.source()));
        double allied = s.health().damage(Handle.index(f.ally()));
        double enemy = s.health().damage(Handle.index(f.enemy()));
        s.queueDamage(Damage.of(f.source(), f.source(), DamageType.HACK, 10));
        s.queueDamage(Damage.of(f.ally(), f.source(), DamageType.HACK, 10));
        s.queueDamage(Damage.of(f.enemy(), f.source(), DamageType.HACK, 10));
        new CombatSystem().run(s);

        assertThat(s.health().damage(Handle.index(f.source()))).isEqualTo(own);
        assertThat(s.health().damage(Handle.index(f.ally()))).isEqualTo(allied);
        assertThat(s.health().damage(Handle.index(f.enemy()))).isGreaterThan(enemy);
    }

    @Test
    void queuedHitsUseTheCurrentRelationInBothDirections() {
        Scene f = scene();
        MatchState s = f.state();
        double before = s.health().damage(Handle.index(f.ally()));
        s.restoreDiplomacy(teams(false));
        s.queueDamage(Damage.of(f.ally(), f.source(), DamageType.HACK, 10));
        s.restoreDiplomacy(teams(true));
        new CombatSystem().run(s);
        assertThat(s.health().damage(Handle.index(f.ally()))).isEqualTo(before);

        s.queueDamage(Damage.of(f.ally(), f.source(), DamageType.HACK, 10));
        s.restoreDiplomacy(teams(false));
        new CombatSystem().run(s);
        assertThat(s.health().damage(Handle.index(f.ally()))).isGreaterThan(before);
    }

    @Test
    void finalFireDamageProtectsOwnAndAlliedBuildingsWithoutProtectingTheEnemy() {
        Scene f = scene();
        int[] buildings = houses(f);
        MatchState s = f.state();
        double own = s.buildings().damage(buildings[0]);
        double allied = s.buildings().damage(buildings[1]);
        double enemy = s.buildings().damage(buildings[2]);
        for (int target : buildings) s.queueDamage(Damage.of(target, f.source(), DamageType.FIRE, 10));
        new CombatSystem().run(s);
        assertThat(s.buildings().damage(buildings[0])).isEqualTo(own);
        assertThat(s.buildings().damage(buildings[1])).isEqualTo(allied);
        assertThat(s.buildings().damage(buildings[2])).isGreaterThan(enemy);
    }

    private static int[] houses(Scene f) {
        for (int faction = 0; faction < 3; faction++) {
            f.harness()
                    .submit(
                            PlayerSlot.ADMIN,
                            new SpawnBuilding(
                                    new TemplateId("strategos:building/house"),
                                    new FactionSlot(faction),
                                    8 + faction * 16,
                                    24,
                                    0));
        }
        f.harness().step();
        assertThat(f.state().buildings().handles()).hasSize(3);
        int[] result = new int[3];
        for (int handle : f.state().buildings().handles())
            result[f.state().buildings().faction(handle)] = handle;
        return result;
    }

    @Test
    void environmentalDamageAndTrustedStructuralDeathsAreNotHostileFriendlyFire() {
        Scene f = scene();
        MatchState s = f.state();
        double before = s.health().damage(Handle.index(f.ally()));
        s.queueDamage(Damage.of(f.ally(), Handle.NONE, DamageType.HACK, 10));
        new CombatSystem().run(s);
        assertThat(s.health().damage(Handle.index(f.ally()))).isGreaterThan(before);

        s.queueDamage(Damage.lethal(f.ally(), f.source()));
        new CombatSystem().run(s);
        assertThat(s.entities().isAlive(f.ally())).isFalse();
    }

    @Test
    void externalAttributionSurvivesIngestWithoutTurningWorldDamageIntoFriendlyFire() {
        Scene f = scene();
        double allied = f.state().health().damage(Handle.index(f.ally()));
        double enemy = f.state().health().damage(Handle.index(f.enemy()));
        f.harness()
                .damage(new ExternalDamage(
                        f.ally(), ExternalDamage.Kind.HACK, 10, ExternalDamage.SourceKind.PLAYER, 0));
        f.harness()
                .damage(new ExternalDamage(
                        f.enemy(), ExternalDamage.Kind.HACK, 10, ExternalDamage.SourceKind.PLAYER, 0));
        f.harness().step();
        assertThat(f.state().health().damage(Handle.index(f.ally()))).isEqualTo(allied);
        assertThat(f.state().health().damage(Handle.index(f.enemy()))).isGreaterThan(enemy);
        f.harness().damage(new ExternalDamage(f.ally(), ExternalDamage.Kind.HACK, 10, ExternalDamage.SourceKind.MOB));
        f.harness().step();
        assertThat(f.state().health().damage(Handle.index(f.ally()))).isGreaterThan(allied);
    }

    @Test
    void anUnprovenDeadHostileSourceIsNotReinterpretedAsEnvironmentalDamage() {
        Scene f = scene();
        double before = f.state().health().damage(Handle.index(f.enemy()));
        f.state().removeUnit(f.source());
        f.state().queueDamage(Damage.of(f.enemy(), f.source(), DamageType.HACK, 10));
        new CombatSystem().run(f.state());
        assertThat(f.state().health().damage(Handle.index(f.enemy()))).isEqualTo(before);
    }

    @Test
    void productionShotReleaseFreezesTheActualSourceFaction() {
        Scene f = scene();
        f.state()
                .bus()
                .publish(new ShotRequested(
                        f.source(), f.enemy(), Damage.of(f.enemy(), f.source(), DamageType.PIERCE, 10), 100, 1, 0));
        f.harness().step();
        assertThat(f.state().shots().count()).isEqualTo(1);
        assertThat(f.state().shots().shot(0).damage().sourceFaction()).isZero();
    }

    @Test
    void productionBuildingArrowReleaseUsesTheBuildingOwner() {
        Scene f = scene();
        int source = houses(f)[0];
        f.state()
                .bus()
                .publish(new ShotRequested(
                        source, f.enemy(), Damage.of(f.enemy(), source, DamageType.PIERCE, 10), 100, 1, 0));
        f.harness().step();
        assertThat(f.state().shots().count()).isEqualTo(1);
        assertThat(f.state().shots().shot(0).damage().sourceFaction()).isZero();
    }

    @Test
    void collateralRetargetingKeepsTheFrozenFactionAfterTheShooterDiesAndItsIndexIsReused() {
        Scene f = scene();
        MatchState s = f.state();
        Damage frozen = Damage.of(f.enemy(), f.source(), DamageType.PIERCE, 10).withSourceFaction(0);
        s.removeUnit(f.enemy());
        s.removeUnit(f.source());
        int replacement = f.harness().spawn(new FactionSlot(2), new Vec2d(4.5, 4.5), 1, 0)[0];
        assertThat(Handle.index(replacement)).isEqualTo(Handle.index(f.source()));
        assertThat(replacement).isNotEqualTo(f.source());
        s.motion().set(Handle.index(f.ally()), 18.5, 1, 4.5, 0);
        s.spatial().rebuild(s);
        ShotStore shots = new ShotStore();
        shots.restore(new ShotStore.Snapshot(
                2, List.of(new ShotStore.Shot(1, f.source(), f.enemy(), s.tick(), 18.5, 1.9, 4.5, frozen))));
        new ShotSystem(shots).run(s);
        var impacts = s.takeDamage();
        assertThat(impacts).hasSize(1);
        assertThat(impacts.getFirst().target()).isEqualTo(f.ally());
        assertThat(impacts.getFirst().sourceFaction()).isZero();
        double allied = s.health().damage(Handle.index(f.ally()));
        double hostile = s.health().damage(Handle.index(replacement));
        impacts.forEach(s::queueDamage);
        s.queueDamage(Damage.of(replacement, f.source(), DamageType.HACK, 10).withSourceFaction(0));
        new CombatSystem().run(s);
        assertThat(s.health().damage(Handle.index(f.ally()))).isEqualTo(allied);
        assertThat(s.health().damage(Handle.index(replacement))).isGreaterThan(hostile);
    }
}
