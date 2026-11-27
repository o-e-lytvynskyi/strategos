package dev.strategos.core.sim.econ;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.BuildingDestroyed;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.UnitDied;
import dev.strategos.core.sim.combat.Damage;
import dev.strategos.core.sim.combat.DamageType;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import org.junit.jupiter.api.Test;

/**
 * CMB-13.1: a kill pays its killer's faction the victim's {@code Loot/resources} from data, in the treasury's own
 * {@code looted} counter, and a unit victim's carry moves with it; no kill by Gaia, by a friend or by nobody pays, and
 * nothing is ever created beyond the loot itself (the core's FINAL checks both balances every step).
 */
class LootTest {
    private static final int FOOD = 0;
    private static final int WOOD = 1;
    private static final int STONE = 2;
    private static final int IRON = 3;
    private static final TemplateId SHEEP = new TemplateId("strategos:unit/sheep");
    private static final TemplateId HOUSE = new TemplateId("strategos:building/house");
    private static final TemplateId CIVIC_CENTRE = new TemplateId("strategos:building/civic_centre");

    private final SimHarness h;
    private final MatchState s;

    LootTest() {
        h = SimHarness.builder().size(64, 64).yBand(0, 31, 1).factions(2).build();
        h.publishAll(VoxelFixtures.flat(64, 64, 32, 1));
        h.step();
        s = KernelAccess.state(h.core());
    }

    private int unit(TemplateId template, int faction, double x) {
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(template, new FactionSlot(faction), new Vec2d(x, 20), 1, 0));
        return h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .mapToInt(e -> ((UnitCreated) e).handle())
                .findFirst()
                .orElseThrow();
    }

    private int building(TemplateId template, int faction, int x, int z) {
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(template, new FactionSlot(faction), x, z, 0));
        h.step();
        int[] handles = s.buildings().handles();
        return handles[handles.length - 1];
    }

    private void kill(int victim, int attacker) {
        s.queueDamage(Damage.of(victim, attacker, DamageType.CRUSH, 100_000f));
        h.step();
    }

    private void carry(int unit, int resource, long amount) {
        s.ledger().found(resource, amount);
        s.ledger().taken(resource, amount);
        s.carry().ensureCapacity(Handle.index(unit) + 1);
        s.carry().add(Handle.index(unit), resource, 0, amount);
    }

    private long looted(int faction, int resource) {
        return s.treasury().looted(faction, resource);
    }

    private long stock(int faction, int resource) {
        return s.treasury().stock(faction, resource);
    }

    @Test
    void aKillPaysTheVictimsLootFromDataToTheKillersFaction() {
        int killer = unit(TemplateSet.INFANTRY, 0, 10);
        int victim = unit(TemplateSet.INFANTRY, 1, 12);
        long food = stock(0, FOOD);
        kill(victim, killer);
        assertThat(h.events(UnitDied.class)).hasSize(1);
        assertThat(looted(0, FOOD)).as("food: five plus a tenth of sixty").isEqualTo(11_000);
        assertThat(looted(0, WOOD)).isEqualTo(4_000);
        assertThat(looted(0, STONE)).isZero();
        assertThat(looted(0, IRON)).isZero();
        assertThat(stock(0, FOOD) - food).isEqualTo(11_000);
        assertThat(looted(1, FOOD)).as("the fallen's faction gains nothing").isZero();
        assertThat(s.treasury().broken()).isNull();
    }

    @Test
    void theLootIsWhatTheTemplatesDataSays() {
        int victim = unit(TemplateSet.WORKER, 1, 12);
        int path = s.costs().lootPath(FOOD);
        assertThat(path).isNotNegative();
        long expected = Amount.of(s.stats().read(Handle.index(victim), path));
        kill(victim, unit(TemplateSet.INFANTRY, 0, 10));
        assertThat(looted(0, FOOD)).isEqualTo(expected).isEqualTo(3_000);
    }

    @Test
    void theCarryMovesToTheKillerAndNothingDoubles() {
        int killer = unit(TemplateSet.INFANTRY, 0, 10);
        int victim = unit(TemplateSet.WORKER, 1, 12);
        carry(victim, WOOD, 20_000);
        kill(victim, killer);
        assertThat(stock(0, WOOD)).as("the carried wood").isEqualTo(20_000);
        assertThat(s.treasury().gathered(0, WOOD)).isEqualTo(20_000);
        assertThat(looted(0, WOOD))
                .as("the worker's loot has no wood, so the carry is not counted twice")
                .isZero();
        assertThat(looted(0, FOOD)).isEqualTo(3_000);
        assertThat(s.carry().total(WOOD, s.entities().highWater())).isZero();
        assertThat(s.ledger().dropped(WOOD))
                .as("not dropped, taken by the killer")
                .isZero();
        assertThat(stock(1, WOOD)).isZero();
    }

    @Test
    void gaiaNobodyAndAFriendPayNothing() {
        int worker = unit(TemplateSet.WORKER, 1, 12);
        carry(worker, WOOD, 7_000);
        int sheep = unit(SHEEP, 2, 14);
        kill(worker, sheep); // by Gaia
        assertThat(allLooted()).isZero();
        assertThat(stock(0, WOOD) + stock(1, WOOD)).isZero();
        assertThat(s.ledger().dropped(WOOD))
                .as("the load is lost with its carrier")
                .isEqualTo(7_000);

        int friend = unit(TemplateSet.INFANTRY, 0, 16);
        int victim = unit(TemplateSet.INFANTRY, 0, 18);
        kill(victim, friend); // by the same faction
        assertThat(allLooted()).isZero();

        int lone = unit(TemplateSet.INFANTRY, 1, 20);
        kill(lone, Handle.NONE); // by no one
        assertThat(allLooted()).isZero();

        int prey = unit(SHEEP, 2, 22);
        kill(prey, unit(TemplateSet.INFANTRY, 0, 24)); // a kill of Gaia's pays no loot either
        assertThat(allLooted()).isZero();
        assertThat(s.treasury().broken()).isNull();
    }

    @Test
    void aBuildingsDestructionPaysItsLoot() {
        int killer = unit(TemplateSet.INFANTRY, 0, 4);
        int house = building(HOUSE, 1, 30, 30);
        kill(house, killer);
        assertThat(h.events(BuildingDestroyed.class)).hasSize(1);
        assertThat(looted(0, WOOD)).isEqualTo(16_000);
        int centre = building(CIVIC_CENTRE, 1, 50, 50);
        kill(centre, killer);
        assertThat(looted(0, WOOD)).isEqualTo(76_000);
        assertThat(looted(0, STONE)).isEqualTo(60_000);
        assertThat(looted(0, IRON)).isEqualTo(50_000);
        assertThat(allLooted(1)).as("its owner gains nothing").isZero();
    }

    @Test
    void aBuildingsArrowsPayItsOwner() {
        int tower = building(CIVIC_CENTRE, 0, 10, 10);
        int victim = unit(TemplateSet.INFANTRY, 1, 40);
        kill(victim, tower);
        assertThat(looted(0, FOOD)).isEqualTo(11_000);
    }

    @Test
    void killsOfOneTickEachPayOnceInHandleOrder() {
        int killer = unit(TemplateSet.INFANTRY, 0, 10);
        int a = unit(TemplateSet.INFANTRY, 1, 12);
        int b = unit(TemplateSet.WORKER, 1, 14);
        s.queueDamage(Damage.of(b, killer, DamageType.CRUSH, 100_000f));
        s.queueDamage(Damage.of(a, killer, DamageType.CRUSH, 100_000f));
        h.step();
        assertThat(h.events(UnitDied.class)).hasSize(2);
        assertThat(looted(0, FOOD)).isEqualTo(14_000);
    }

    private long allLooted() {
        return allLooted(0) + allLooted(1);
    }

    private long allLooted(int faction) {
        long sum = 0;
        for (int r = 0; r < s.treasury().resources(); r++) {
            sum += looted(faction, r);
        }
        return sum;
    }
}
