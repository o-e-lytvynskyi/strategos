package dev.strategos.core.sim.combat;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.protocol.Garrison;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.ShotFired;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.garrison.Garrisons;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * BLD-12.1 in L1: both towers' arrows, garrisons and costs are the data's (the sheet): the sentry tower fires one arrow
 * and one more for each of its three garrisoned infantry, the defence tower one and one more for each of five.
 */
class TowerDataTest {
    static final TemplateId SENTRY = new TemplateId("strategos:building/sentry_tower");
    static final TemplateId TOWER = new TemplateId("strategos:building/tower");

    final SimHarness h =
            SimHarness.builder().size(96, 64).yBand(0, 31, 1).factions(3).build();
    final MatchState s;

    TowerDataTest() {
        h.publishAll(VoxelFixtures.flat(96, 64, 32, 1));
        h.step();
        s = KernelAccess.state(h.core());
    }

    static double sheet(TemplateId id, String path) {
        TemplateSet set = TemplateSet.builtin();
        UnitTemplate template = Objects.requireNonNull(set.byId(id));
        return set.value(template.netIndex(), set.valuePaths().byPath(path).id());
    }

    @Test
    void theSentryTowerIsTheSheetsAndTheDefenceTowerIsDearer() {
        assertThat(sheet(SENTRY, "Health/max")).isEqualTo(400);
        assertThat(sheet(SENTRY, "Attack/Building/arrows")).isEqualTo(1);
        assertThat(sheet(SENTRY, "Attack/Building/perGarrisoned")).isEqualTo(1);
        assertThat(sheet(SENTRY, "Attack/Building/interval")).isEqualTo(40);
        assertThat(sheet(SENTRY, "Attack/Building/range")).isEqualTo(24);
        assertThat(sheet(SENTRY, "Vision/range")).isEqualTo(20);
        assertThat(sheet(SENTRY, "GarrisonHolder/capacity")).isEqualTo(3);
        assertThat(sheet(SENTRY, "Capturable/points")).isEqualTo(400);
        assertThat(sheet(SENTRY, "Capturable/regen")).isEqualTo(5);
        assertThat(sheet(SENTRY, "TerritoryInfluence/weight")).isEqualTo(400);
        assertThat(sheet(SENTRY, "TerritoryInfluence/radius")).isEqualTo(20);
        assertThat(sheet(SENTRY, "Cost/resources/wood")).isEqualTo(100);
        assertThat(sheet(SENTRY, "Cost/resources/stone")).as("wood alone").isNaN();
        assertThat(sheet(TOWER, "GarrisonHolder/capacity")).isEqualTo(5);
        assertThat(sheet(TOWER, "Attack/Building/perGarrisoned")).isEqualTo(1);
        assertThat(sheet(TOWER, "Cost/resources/stone")).isPositive();
        assertThat(sheet(SENTRY, "Upgrade/time")).isEqualTo(50);
        assertThat(sheet(SENTRY, "Upgrade/Cost/resources/wood")).isEqualTo(50);
        assertThat(sheet(SENTRY, "Upgrade/Cost/resources/stone")).isEqualTo(100);
    }

    private int building(TemplateId template, int faction, int x, int z) {
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(template, new FactionSlot(faction), x, z, 0));
        h.step();
        int[] handles = s.buildings().handles();
        return handles[handles.length - 1];
    }

    private int[] units(int faction, double x, double z, int count) {
        h.submit(
                PlayerSlot.ADMIN,
                new SpawnUnits(TemplateSet.INFANTRY, new FactionSlot(faction), new Vec2d(x, z), count, 1.5));
        return h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .mapToInt(e -> ((UnitCreated) e).handle())
                .toArray();
    }

    /** The arrows of each volley of {@code tower} over a stretch long enough for a few of them, by launch tick. */
    private Map<Long, Integer> volleys(int tower, int ticks) {
        Map<Long, Integer> perVolley = new TreeMap<>();
        for (int t = 0; t < ticks; t++) {
            h.step();
            for (int i = 0; i < s.entities().highWater(); i++) {
                s.health().setDamage(i, 0); // nobody dies, so the volleys' size stays the garrison's
            }
        }
        for (ShotFired e : h.events(ShotFired.class)) {
            if (e.shooter() == tower) {
                perVolley.merge(e.launchTick(), 1, Integer::sum);
            }
        }
        return perVolley;
    }

    private void fireWithGarrison(TemplateId template, int garrison) {
        int tower = building(template, 1, 40, 30);
        units(0, 52.5, 33.5, 1); // an enemy within both towers' range, clear of their walls
        int[] door = Garrisons.door(s, tower);
        int[] inside = units(1, door[0] + 0.5, door[1] + 4.5, garrison);
        h.submit(new PlayerSlot(1), new Garrison(UnitSet.of(inside), tower));
        h.step();
        assertThat(h.runUntil(() -> s.garrison().count(tower) == garrison, 400)).isTrue();
        long now = s.tick();
        Map<Long, Integer> perVolley = volleys(tower, 6 * 40);
        assertThat(perVolley.entrySet().stream().filter(e -> e.getKey() > now).map(Map.Entry::getValue))
                .as("one arrow and one for each garrisoned unit, every volley")
                .containsOnly(1 + garrison)
                .hasSize(6);
    }

    @Test
    void theSentryTowersVolleyGrowsByOneArrowForEachOfItsThreeGarrisoned() {
        fireWithGarrison(SENTRY, 3);
    }

    @Test
    void theDefenceTowersVolleyGrowsByOneArrowForEachOfItsFiveGarrisoned() {
        fireWithGarrison(TOWER, 5);
    }
}
