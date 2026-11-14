package dev.strategos.core.sim.combat;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.PlaceBuilding;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TechId;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.ShotFired;
import dev.strategos.core.sim.ShotLanded;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.UnitHurt;
import dev.strategos.core.sim.econ.Amount;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * BLD-17 in L1: the defence tower's arrows are the sheet's (one every 40 ticks, ten pierce, twenty-eight blocks), it is
 * placed only in Town and costs stone, and it shoots only enemy units.
 */
class TowerTest {
    static final TemplateId TOWER = new TemplateId("strategos:building/tower");
    static final TemplateId CENTRE = new TemplateId("strategos:building/civic_centre");
    static final TemplateId HOUSE = new TemplateId("strategos:building/house");
    static final TemplateId CHICKEN = new TemplateId("strategos:unit/chicken");
    static final int WOOD = 1; // the resources' order: food, wood, stone, iron
    static final int STONE = 2;
    static final TechId TOWN = new TechId("strategos:phase_town");

    /** The sheet's tower: one arrow of ten pierce every 40 ticks, 28 blocks. */
    static final int ARROWS = 1;

    static final int INTERVAL = 40;
    static final double PIERCE = 10;
    static final double RANGE = 28;

    final SimHarness h =
            SimHarness.builder().size(96, 64).yBand(0, 31, 1).factions(3).build();
    final MatchState s;

    TowerTest() {
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

    List<ShotFired> volleys(int tower) {
        return h.events(ShotFired.class).stream()
                .filter(e -> e.shooter() == tower)
                .toList();
    }

    double sheet(String path) {
        TemplateSet set = TemplateSet.builtin();
        UnitTemplate template = java.util.Objects.requireNonNull(set.byId(TOWER));
        return set.value(template.netIndex(), set.valuePaths().byPath(path).id());
    }

    @Test
    void arrowsAndDpsAreTheSheets() {
        assertThat(sheet("Attack/Building/arrows")).isEqualTo(ARROWS);
        assertThat(sheet("Attack/Building/interval")).isEqualTo(INTERVAL);
        assertThat(sheet("Attack/Building/pierce")).isEqualTo(PIERCE);
        assertThat(sheet("Attack/Building/range")).isEqualTo(RANGE);
        assertThat(sheet("Vision/range")).isEqualTo(24);
        assertThat(TemplateSet.builtin().byId(TOWER).classes()).doesNotContain("GarrisonHolder");
        int tower = building(TOWER, 1, 40, 30);
        int target = unit(TemplateSet.INFANTRY, 0, 52.5, 33.5); // nine blocks from the middle, clear of its walls
        double[] resistance = new double[3];
        for (int type = 0; type < 3; type++) {
            resistance[type] = s.stats().resistance(Handle.index(target), type);
        }
        double perArrow = DamageFormula.dealt(
                Damage.of(target, tower, DamageType.PIERCE, (float) PIERCE),
                resistance,
                s.stats().classes(Handle.index(target)));
        long start = s.tick();
        int shots = 20;
        run(shots * INTERVAL);
        Map<Long, Integer> perVolley = new TreeMap<>();
        for (ShotFired e : volleys(tower)) perVolley.merge(e.launchTick(), 1, Integer::sum);
        assertThat(perVolley).hasSize(shots);
        assertThat(perVolley.values()).containsOnly(ARROWS);
        List<Long> ticks = List.copyOf(perVolley.keySet());
        for (int k = 1; k < ticks.size(); k++) {
            assertThat(ticks.get(k) - ticks.get(k - 1)).isEqualTo(INTERVAL);
        }
        assertThat(ticks.get(0)).isBetween(start + 1, start + INTERVAL);
        long landed = h.events(ShotLanded.class).stream()
                .filter(e -> e.shooter() == tower && e.hit() == target)
                .count();
        assertThat(landed).as("the spread lets some arrows miss").isGreaterThanOrEqualTo(shots / 2);
        double dealt = h.events(UnitHurt.class).stream()
                .filter(e -> e.attacker() == tower && e.handle() == target)
                .mapToDouble(UnitHurt::amount)
                .sum();
        assertThat(dealt).isCloseTo(landed * perArrow, org.assertj.core.data.Offset.offset(1e-3));
        assertThat(dealt / (shots * INTERVAL / 20.0))
                .as("damage a second for the arrows that land: pierce over the interval")
                .isCloseTo(
                        landed / (double) shots * perArrow / (INTERVAL / 20.0),
                        org.assertj.core.data.Offset.offset(1e-3));
    }

    @Test
    void townOnlyAndStone() {
        assertThat(sheet("Cost/resources/stone")).as("stone is its price").isPositive();
        assertThat(sheet("Placement/minPhase")).as("Town, phase one").isEqualTo(1);
        building(CENTRE, 0, 20, 20);
        h.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(0), "wood", 1_000));
        h.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(0), "stone", 1_000));
        h.step();
        PlayerSlot p0 = new PlayerSlot(0);
        PlaceBuilding place = new PlaceBuilding(TOWER, 40, 40, 0, UnitSet.of(), QueueMode.REPLACE);
        h.submit(p0, place);
        h.step();
        CommandResult village = h.results().get(h.results().size() - 1);
        assertThat(village.outcome()).isEqualTo(CommandOutcome.REJECTED);
        assertThat(village.firstReason()).as("not in Village").isEqualTo(RejectReason.REQUIREMENTS);
        int before = s.buildings().handles().length;
        s.progress().complete(0, TOWN);
        assertThat(s.treasury().spent(0, STONE)).isZero();
        h.submit(p0, place);
        h.step();
        CommandResult town = h.results().get(h.results().size() - 1);
        assertThat(town.outcome()).as("in Town").isEqualTo(CommandOutcome.ACCEPTED);
        assertThat(s.buildings().handles()).hasSize(before + 1);
        assertThat(s.treasury().spent(0, STONE)).isEqualTo(Amount.of(sheet("Cost/resources/stone")));
        assertThat(s.treasury().spent(0, WOOD)).isEqualTo(Amount.of(sheet("Cost/resources/wood")));
    }

    @Test
    void onlyEnemies() {
        int tower = building(TOWER, 1, 40, 30);
        building(HOUSE, 0, 50, 40); // an enemy building in range is never aimed at
        unit(TemplateSet.INFANTRY, 1, 50, 33); // an own unit
        unit(CHICKEN, s.gaia(), 48, 33); // Gaia's animal
        unit(TemplateSet.INFANTRY, 0, 74, 33); // an enemy beyond the range of 28
        run(3 * INTERVAL);
        assertThat(volleys(tower)).as("nothing in range to shoot").isEmpty();
        int enemy =
                unit(TemplateSet.INFANTRY, 2, 55, 33); // 28 blocks and under: in range, which the centre's 24 is not
        run(3 * INTERVAL);
        assertThat(volleys(tower))
                .hasSize(3 * ARROWS)
                .extracting(ShotFired::target)
                .containsOnly(enemy);
    }
}
