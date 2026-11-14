package dev.strategos.core.sim.combat;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.content.balance.BalanceSheet;
import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.AttackSpread;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.BuildingDestroyed;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.FlameShown;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.OutputEvent;
import dev.strategos.core.sim.TickOutputs;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.UnitGear;
import dev.strategos.core.sim.UnitHurt;
import dev.strategos.core.sim.UnitStruck;
import dev.strategos.core.sim.unitai.UnitAi;
import dev.strategos.core.testkit.BalanceData;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * CMB-09 in L1, with the mod's own templates: two rams raze a stone civic centre in the balance sheet's time, under a
 * minute; the same price in torch-bearers takes over five minutes on stone (spec row 14); and a ram never takes a unit
 * or a field as a target, whoever strikes it.
 */
class SiegeTest {
    static final Path REPO = Path.of(System.getProperty("strategos.repoRoot", ".."));
    static final TemplateId RAM = new TemplateId("strategos:unit/ram");
    static final TemplateId CENTRE = new TemplateId("strategos:building/civic_centre");
    static final TemplateId HOUSE = new TemplateId("strategos:building/house");
    static final TemplateId FIELD = new TemplateId("strategos:building/field");
    static final PlayerSlot P0 = new PlayerSlot(0);
    static final PlayerSlot P1 = new PlayerSlot(1);

    /** Row 14's bounds: two rams under a minute, the same price in torches over five. */
    static final long RAMS_AT_MOST = 60 * 20;

    static final long TORCHES_AT_LEAST = 5 * 60 * 20;

    final BalanceData data = BalanceData.read(REPO.resolve("mod/src/main/resources/data"));
    final TemplateSet set = data.compile().set();
    final SimHarness h;
    final MatchState s;

    SiegeTest() {
        h = SimHarness.builder()
                .size(80, 80)
                .yBand(0, 31, 1)
                .templates(set)
                .factions(2)
                .build();
        h.publishAll(VoxelFixtures.flat(80, 80, 32, 1));
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

    /** The ticks from the order to the building's fall, and the blows struck; -1 for a building standing at the end. */
    record Fall(long ticks, long blows, boolean flames) {}

    Fall run(int building, long order, long most, int... healed) {
        long blows = 0;
        boolean flames = false;
        for (long t = 0; t < most; t++) {
            TickOutputs out = h.step();
            for (int unit : healed) {
                s.health().setDamage(Handle.index(unit), 0); // the centre's arrows would only slow the torches
            }
            for (OutputEvent e : out.events()) {
                if (e instanceof UnitStruck st && st.target() == building) {
                    blows++;
                } else if (e instanceof FlameShown) {
                    flames = true;
                } else if (e instanceof BuildingDestroyed d && d.handle() == building) {
                    return new Fall(out.tick() - order, blows, flames);
                }
            }
        }
        return new Fall(-1, blows, flames);
    }

    double value(TemplateId id, String path) {
        UnitTemplate t = set.byId(id);
        return set.value(t.netIndex(), set.valuePaths().byPath(path).id());
    }

    double totalCost(TemplateId id) {
        double sum = 0;
        for (String r : List.of("food", "wood", "stone", "iron")) {
            double v = value(id, "Cost/resources/" + r);
            sum += Double.isNaN(v) ? 0 : v;
        }
        return sum;
    }

    @Test
    void twoRamsRazeACentreInTheSheetTime() {
        BalanceSheet.Raze sheet = data.sheet().razes().stream()
                .filter(r -> r.attacker().equals(RAM) && r.building().equals(CENTRE))
                .findFirst()
                .orElseThrow();
        assertThat(sheet.twoTicks()).as("the sheet: under a minute").isLessThanOrEqualTo(RAMS_AT_MOST);
        int centre = building(CENTRE, 1, 40, 40);
        int[] rams = units(RAM, 0, new Vec2d(38, 30), 2);
        assertThat(attack(rams, centre).accepted()).isEqualTo(2);
        long order = h.tick();
        Fall fall = run(centre, order, 2 * RAMS_AT_MOST);
        assertThat(fall.ticks()).as("the centre falls").isPositive();
        assertThat(fall.blows()).as("on the sheet's blow, not before it").isEqualTo(sheet.hits());
        assertThat(fall.ticks())
                .as("in the sheet's two-ram time: its wind-up and rounds, and the walk to the wall")
                .isBetween(sheet.twoTicks(), sheet.twoTicks() + 200);
        assertThat(fall.flames()).as("a log shows no flame").isFalse();
        assertThat(h.events(UnitGear.class)).as("a ram carries no torch").noneMatch(g -> g.tool() == UnitGear.TORCH);
    }

    /**
     * QA-12: a ram's body (radius 1) does not fit the ring cell half a block off the wall that its place is, so one
     * kept short of it by a house or a crowd wobbled there WALKING for good. Within a block of the wall past its radius
     * it strikes from where it stands.
     */
    @Test
    void aRamThatCannotReachItsPlaceStrikesFromTheWall() {
        int centre = building(CENTRE, 1, 40, 40); // x 40..51, z 40..51
        int[] rams = units(RAM, 0, new Vec2d(45.5, 52.4), 1); // 1.4 blocks south of the wall
        assertThat(attack(rams, centre).accepted()).isEqualTo(1);
        int i = Handle.index(rams[0]);
        s.orders()
                .setPlace(i, 45.5, 45.5); // its place lies behind the wall: out of reach, as the ring cell is to a ram
        assertThat(h.runUntil(() -> UnitAi.leaf(s.ai().code(i)) == UnitAi.BURNING, 40))
                .as("it strikes from the wall")
                .isTrue();
    }

    /**
     * QA-12: a burner held off its place by the crowd at the wall, within reach of it, never landed on it and drifted
     * there for good; once held up {@code Burning.PATIENCE} ticks it burns from where it is.
     */
    @Test
    void aBurnerHeldNearItsPlaceBurnsAfterItsPatience() {
        int centre = building(CENTRE, 1, 40, 40);
        int[] spearmen = units(TemplateSet.INFANTRY, 0, new Vec2d(45.5, 52.0), 1);
        assertThat(attack(spearmen, centre).accepted()).isEqualTo(1);
        int i = Handle.index(spearmen[0]);
        assertThat(h.runUntil(() -> UnitAi.leaf(s.ai().code(i)) == UnitAi.BURNING, 200))
                .isTrue();
        // the crowd pushes it 1.2 blocks off its place, every tick, so that it never lands on it again
        double px = s.orders().targetX(i);
        double pz = s.orders().targetZ(i);
        s.ai().set(i, UnitAi.BURN_APPROACHING);
        long since = h.tick();
        while (UnitAi.leaf(s.ai().code(i)) != UnitAi.BURNING && h.tick() < since + 400) {
            s.motion().set(i, px + 1.2, s.motion().y(i), pz, s.motion().yaw(i));
            h.step();
        }
        assertThat(h.tick() - since).as("it burns once it is held up").isBetween(5L, 60L);
    }

    @Test
    void torchesTakeFiveMinutesOnStone() {
        int bearers = (int) Math.floor(2 * totalCost(RAM) / totalCost(TemplateSet.INFANTRY));
        assertThat(bearers).as("what two rams cost, in spearmen").isBetween(2, 8);
        int centre = building(CENTRE, 1, 40, 40);
        int[] spearmen = units(TemplateSet.INFANTRY, 0, new Vec2d(38, 30), bearers);
        assertThat(attack(spearmen, centre).accepted()).isEqualTo(bearers);
        long order = h.tick();
        Fall fall = run(centre, order, 4 * TORCHES_AT_LEAST, spearmen);
        assertThat(fall.ticks()).as("the centre falls to them in the end").isPositive();
        assertThat(fall.ticks())
                .as("but not in under five minutes: stone needs the ram")
                .isGreaterThanOrEqualTo(TORCHES_AT_LEAST);
    }

    @Test
    void ramsNeverAttackUnits() {
        int[] rams = units(RAM, 0, new Vec2d(30, 30), 2);
        int[] spearmen = units(TemplateSet.INFANTRY, 1, new Vec2d(33, 30), 3);
        int field = building(FIELD, 1, 50, 50);
        int house = building(HOUSE, 1, 50, 20);
        // the rules refuse a unit and a field
        for (int target : new int[] {spearmen[0], field}) {
            CommandResult refused = attack(rams, target);
            assertThat(refused.accepted()).as("an attack on %x", target).isZero();
            assertThat(refused.firstReason()).isEqualTo(RejectReason.UNSUPPORTED);
        }
        h.submit(P0, new AttackSpread(UnitSet.of(rams), spearmen[0], QueueMode.REPLACE));
        h.step();
        assertThat(h.results().get(h.results().size() - 1).accepted())
                .as("a spread")
                .isZero();
        // and on its own it never picks one, nor answers a blow: three spearmen hit it for ten seconds
        List<Integer> hurtBy = new ArrayList<>();
        h.submit(P1, Attack.replace(UnitSet.of(spearmen), rams[0]));
        for (int t = 0; t < 200; t++) {
            for (OutputEvent e : h.step().events()) {
                if (e instanceof UnitStruck st && java.util.Arrays.stream(rams).anyMatch(r -> r == st.attacker())) {
                    hurtBy.add(st.attacker());
                } else if (e instanceof UnitHurt u
                        && java.util.Arrays.stream(rams).anyMatch(r -> r == u.attacker())) {
                    hurtBy.add(u.attacker());
                }
            }
        }
        assertThat(hurtBy).as("a blow from a ram on a unit").isEmpty();
        assertThat(h.events(UnitHurt.class)).as("the spearmen did hurt the ram").anyMatch(u -> u.handle() == rams[0]);
        // yet a house is its to strike
        assertThat(attack(rams, house).accepted()).isEqualTo(2);
    }
}
