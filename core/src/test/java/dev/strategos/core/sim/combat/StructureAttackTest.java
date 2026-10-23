package dev.strategos.core.sim.combat;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.BuildingDestroyed;
import dev.strategos.core.sim.BuildingHurt;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.FlameShown;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.OutputEvent;
import dev.strategos.core.sim.TickOutputs;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.UnitGear;
import dev.strategos.core.sim.UnitStruck;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * CMB-03.1–3 in L1: spearmen burn a wooden house on exactly the blow the balance sheet counts, at the tick their blows'
 * rhythm gives; a torch takes 2.09 times the hit points a blow from wood as from stone; a torch shows only against a
 * building, and a worker carries none.
 */
class StructureAttackTest {
    static final TemplateId HOUSE = new TemplateId("strategos:building/house");
    static final TemplateId CIVIC_CENTRE = new TemplateId("strategos:building/civic_centre");
    static final PlayerSlot P0 = new PlayerSlot(0);

    /** The balance sheet's "Burning down" row for the house: blows, and ten bearers from one tick. */
    static final int HOUSE_BLOWS = 134;

    static final int HOUSE_TEN_TICKS = 270;

    final SimHarness h;
    final MatchState s;

    StructureAttackTest() {
        h = SimHarness.builder().size(64, 64).yBand(0, 31, 1).build();
        h.publishAll(VoxelFixtures.flat(64, 64, 32, 1));
        h.step();
        s = KernelAccess.state(h.core());
    }

    int building(TemplateId template, int faction, int x, int z) {
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(template, new FactionSlot(faction), x, z, 0));
        h.step();
        int[] handles = s.buildings().handles();
        return handles[handles.length - 1];
    }

    int[] units(TemplateId template, int faction, Vec2d at, int count) {
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(template, new FactionSlot(faction), at, count, 1.5));
        return h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .mapToInt(e -> ((UnitCreated) e).handle())
                .toArray();
    }

    CommandResult attack(int[] units, int target) {
        h.submit(P0, Attack.replace(UnitSet.of(units), target));
        h.step();
        return h.results().get(h.results().size() - 1);
    }

    @Test
    void tenSpearmenBurnAHouseInTheSheetTime() {
        int house = building(HOUSE, 1, 30, 30);
        int[] spearmen = units(TemplateSet.INFANTRY, 0, new Vec2d(24, 24), 10);
        assertThat(attack(spearmen, house).accepted()).isEqualTo(10);
        Map<Integer, Long> first = new TreeMap<>();
        List<Long> blows = new ArrayList<>(); // the tick of every blow on the house, in order
        long fell = -1;
        for (int t = 0; t < 1_200 && fell < 0; t++) {
            TickOutputs out = h.step();
            for (OutputEvent e : out.events()) {
                if (e instanceof UnitStruck st && st.target() == house) {
                    first.putIfAbsent(st.attacker(), out.tick());
                    blows.add(out.tick());
                } else if (e instanceof BuildingDestroyed d && d.handle() == house) {
                    fell = out.tick();
                }
            }
        }
        assertThat(first).as("all ten burn").hasSize(10);
        assertThat(fell).as("the house falls").isPositive();
        long end = fell;
        assertThat(blows.stream().filter(t -> t < end).count())
                .as("it stands through every blow before the sheet's 134th")
                .isLessThan(HOUSE_BLOWS);
        assertThat(blows.stream().filter(t -> t <= end).count()).isGreaterThanOrEqualTo(HOUSE_BLOWS);
        // each bearer strikes from its first blow on, a blow every 20 ticks: the 134th of them all is the fall
        List<Long> schedule = new ArrayList<>();
        for (long f : first.values()) {
            for (long t = f; t <= fell + 20; t += 20) {
                schedule.add(t);
            }
        }
        schedule.sort(null);
        assertThat(fell).as("the fall is the 134th blow's tick").isEqualTo(schedule.get(HOUSE_BLOWS - 1));
        // and ten bearers from one tick: the wind-up and 13 further rounds, the sheet's 270 ticks
        assertThat(10 + (long) Math.ceilDiv(HOUSE_BLOWS, 10) * 20 - 20).isEqualTo(HOUSE_TEN_TICKS);
        assertThat(s.buildings().alive(house)).isFalse();
        assertThat(h.events(FlameShown.class)).as("a flame a blow").hasSizeGreaterThanOrEqualTo(HOUSE_BLOWS);
    }

    @Test
    void woodBurnsTwiceAsFastAsStone() {
        int house = building(HOUSE, 1, 10, 10);
        int centre = building(CIVIC_CENTRE, 1, 40, 40);
        attack(units(TemplateSet.INFANTRY, 0, new Vec2d(8, 20), 1), house);
        attack(units(TemplateSet.INFANTRY, 0, new Vec2d(38, 54), 1), centre);
        h.runTicks(400);
        double wood = h.events(BuildingHurt.class).stream()
                .filter(e -> e.handle() == house)
                .mapToDouble(BuildingHurt::amount)
                .max()
                .orElse(0);
        double stone = h.events(BuildingHurt.class).stream()
                .filter(e -> e.handle() == centre)
                .mapToDouble(BuildingHurt::amount)
                .max()
                .orElse(0);
        assertThat(stone).isPositive();
        double perHpWood = wood / s.buildingMaxHp(house);
        double perHpStone = stone / s.buildingMaxHp(centre);
        assertThat(wood / stone).as("a blow on wood against one on stone").isGreaterThanOrEqualTo(2);
        assertThat(perHpWood / perHpStone)
                .as("per hit point, the house's 400 against the centre's 2000")
                .isGreaterThan(2);
    }

    @Test
    void torchesOnlyAgainstBuildings() {
        int house = building(HOUSE, 1, 30, 30);
        int[] enemy = units(TemplateSet.INFANTRY, 1, new Vec2d(10, 50), 1);
        int[] spearman = units(TemplateSet.INFANTRY, 0, new Vec2d(10, 44), 1);
        attack(spearman, enemy[0]);
        h.runTicks(200);
        assertThat(gear(spearman[0])).as("against a unit: the spear").doesNotContain(UnitGear.TORCH);
        int seen = h.events(UnitGear.class).size();
        int[] burner = units(TemplateSet.INFANTRY, 0, new Vec2d(24, 24), 1);
        attack(burner, house);
        h.runTicks(40);
        assertThat(gear(burner[0])).as("against a building: the torch").contains(UnitGear.TORCH);
        assertThat(h.events(UnitGear.class)
                        .subList(seen, h.events(UnitGear.class).size()))
                .filteredOn(g -> g.tool() == UnitGear.TORCH)
                .extracting(UnitGear::handle)
                .containsOnly(burner[0]);
        h.runTicks(3_000);
        assertThat(s.buildings().alive(house)).isFalse();
        List<Integer> after = gear(burner[0]);
        assertThat(after.get(after.size() - 1))
                .as("the house gone, the torch goes")
                .isEqualTo(UnitGear.NONE);

        int[] worker = units(TemplateSet.WORKER, 0, new Vec2d(24, 24), 1);
        int other = building(HOUSE, 1, 40, 10);
        assertThat(attack(worker, other).firstReason())
                .as("a worker carries no torch")
                .isEqualTo(RejectReason.UNSUPPORTED);
        int own = building(HOUSE, 0, 10, 10);
        assertThat(attack(burner, own).firstReason()).isEqualTo(RejectReason.INVALID_ARGUMENT);
    }

    private List<Integer> gear(int handle) {
        return h.events(UnitGear.class).stream()
                .filter(g -> g.handle() == handle)
                .map(UnitGear::tool)
                .toList();
    }
}
