package dev.strategos.core.sim.combat;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.ShotFired;
import dev.strategos.core.sim.ShotLanded;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.UnitHurt;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * CMB-07 in L1: a civic centre's volleys are the sheet's arrows every sheet interval and deal the sheet's damage a
 * second; only enemy units are ever aimed at, and a centre under construction or fallen shoots nothing.
 */
class TownArrowsTest {
    static final TemplateId CENTRE = new TemplateId("strategos:building/civic_centre");
    static final TemplateId HOUSE = new TemplateId("strategos:building/house");
    static final TemplateId CHICKEN = new TemplateId("strategos:unit/chicken");
    /** The sheet's civic centre: three arrows of eight pierce, every 80 ticks, 24 blocks. */
    static final int ARROWS = 3;

    static final int INTERVAL = 80;
    static final double PIERCE = 8;

    final SimHarness h =
            SimHarness.builder().size(96, 64).yBand(0, 31, 1).factions(3).build();
    final MatchState s;

    TownArrowsTest() {
        h.publishAll(VoxelFixtures.flat(96, 64, 32, 1));
        h.step();
        s = KernelAccess.state(h.core());
    }

    int building(TemplateId template, int faction, int x, int z) {
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(template, new FactionSlot(faction), x, z, 0));
        h.step();
        int[] handles = s.buildings().handles();
        return handles[handles.length - 1];
    }

    int unit(TemplateId template, int faction, double x, double z) {
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(template, new FactionSlot(faction), new Vec2d(x, z), 1, 0));
        return h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .mapToInt(e -> ((UnitCreated) e).handle())
                .findFirst()
                .orElseThrow();
    }

    /** Runs {@code ticks} ticks, healing every unit each tick so none dies and every arrow's damage is its own. */
    void run(int ticks) {
        for (int t = 0; t < ticks; t++) {
            h.step();
            for (int i = 0; i < s.entities().highWater(); i++) {
                s.health().setDamage(i, 0);
            }
        }
    }

    List<ShotFired> volleys(int centre) {
        return h.events(ShotFired.class).stream()
                .filter(e -> e.shooter() == centre)
                .toList();
    }

    @Test
    void countAndDpsAreTheSheets() {
        TemplateSet set = TemplateSet.builtin();
        UnitTemplate template = java.util.Objects.requireNonNull(set.byId(CENTRE));
        assertThat(set.value(
                        template.netIndex(),
                        set.valuePaths().byPath("Attack/Building/arrows").id()))
                .isEqualTo(ARROWS);
        assertThat(set.value(
                        template.netIndex(),
                        set.valuePaths().byPath("Attack/Building/interval").id()))
                .isEqualTo(INTERVAL);
        assertThat(set.value(
                        template.netIndex(),
                        set.valuePaths().byPath("Attack/Building/pierce").id()))
                .isEqualTo(PIERCE);
        int centre = building(CENTRE, 1, 40, 30);
        int target = unit(
                TemplateSet.INFANTRY,
                0,
                54.5,
                35.5); // 9 blocks from the footprint's middle, clear of its walls: every arrow hits
        double[] resistance = new double[3];
        for (int type = 0; type < 3; type++) {
            resistance[type] = s.stats().resistance(Handle.index(target), type);
        }
        double perArrow = DamageFormula.dealt(
                Damage.of(target, centre, DamageType.PIERCE, (float) PIERCE),
                resistance,
                s.stats().classes(Handle.index(target)));
        int from = h.events(ShotFired.class).size();
        long start = s.tick();
        int window = 10 * INTERVAL;
        run(window);
        Map<Long, Integer> perVolley = new TreeMap<>();
        for (ShotFired e : volleys(centre)) perVolley.merge(e.launchTick(), 1, Integer::sum);
        assertThat(from).isZero();
        assertThat(perVolley).hasSize(10);
        assertThat(perVolley.values()).containsOnly(ARROWS);
        List<Long> ticks = List.copyOf(perVolley.keySet());
        for (int k = 1; k < ticks.size(); k++) {
            assertThat(ticks.get(k) - ticks.get(k - 1)).isEqualTo(INTERVAL);
        }
        assertThat(ticks.get(0)).isBetween(start + 1, start + INTERVAL);
        // the shot model's spread lets an arrow miss now and then: the damage is the sheet's for the arrows that land
        long landed = h.events(ShotLanded.class).stream()
                .filter(e -> e.shooter() == centre && e.hit() == target)
                .count();
        assertThat(landed)
                .as("most arrows hit a stationary target nine blocks off (spread)")
                .isGreaterThanOrEqualTo(20);
        double dealt = h.events(UnitHurt.class).stream()
                .filter(e -> e.attacker() == centre && e.handle() == target)
                .mapToDouble(UnitHurt::amount)
                .sum();
        assertThat(dealt).isCloseTo(landed * perArrow, org.assertj.core.data.Offset.offset(1e-3));
        assertThat(dealt / (window / 20.0))
                .as("damage a second: the sheet's arrows x damage over its interval, for the arrows that land")
                .isCloseTo(
                        landed / (double) (ARROWS * 10) * ARROWS * perArrow / (INTERVAL / 20.0),
                        org.assertj.core.data.Offset.offset(1e-3));
        ShotFired first = volleys(centre).get(0);
        assertThat(first.fromX())
                .isEqualTo(
                        s.footprintOf(centre).anchorX() + s.footprintOf(centre).spanX() / 2.0);
        assertThat(first.fromZ())
                .isEqualTo(
                        s.footprintOf(centre).anchorZ() + s.footprintOf(centre).spanZ() / 2.0);
        assertThat(first.fromY())
                .isEqualTo(s.buildings().baseY(centre) + s.blueprintOf(centre).sizeY());
    }

    @Test
    void arrowsGoRoundTheEnemiesNearestFirst() {
        int centre = building(CENTRE, 1, 40, 30);
        int far = unit(TemplateSet.INFANTRY, 0, 60, 33);
        int near = unit(TemplateSet.INFANTRY, 0, 52, 33);
        run(INTERVAL);
        List<ShotFired> volley = volleys(centre);
        assertThat(volley).extracting(ShotFired::target).containsExactly(near, far, near);
    }

    @Test
    void onlyEnemies() {
        int centre = building(CENTRE, 1, 40, 30);
        building(HOUSE, 0, 50, 40); // an enemy building in range is never aimed at
        unit(TemplateSet.INFANTRY, 1, 50, 33); // an own unit
        unit(CHICKEN, s.gaia(), 48, 33); // Gaia's animal
        unit(TemplateSet.INFANTRY, 0, 70, 33); // an enemy beyond the range of 24
        run(2 * INTERVAL);
        assertThat(volleys(centre)).as("nothing in range to shoot").isEmpty();
        int enemy = unit(TemplateSet.INFANTRY, 2, 52, 33);
        run(2 * INTERVAL);
        assertThat(volleys(centre))
                .hasSize(2 * ARROWS)
                .extracting(ShotFired::target)
                .containsOnly(enemy);
    }

    @Test
    void aCentreUnderConstructionOrFallenShootsNothing() {
        int growing = building(CENTRE, 1, 40, 30);
        s.buildings().setState(growing, BuildingStore.CONSTRUCTING, 500);
        unit(TemplateSet.INFANTRY, 0, 52, 33);
        run(2 * INTERVAL);
        assertThat(volleys(growing)).isEmpty();
        s.buildings().setState(growing, BuildingStore.COMPLETE, BuildingStore.FULL);
        run(INTERVAL);
        assertThat(volleys(growing)).hasSize(ARROWS);
        s.destroyBuilding(growing);
        run(2 * INTERVAL);
        assertThat(volleys(growing)).hasSize(ARROWS);
    }
}
