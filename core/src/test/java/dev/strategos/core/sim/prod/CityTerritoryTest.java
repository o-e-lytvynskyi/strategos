package dev.strategos.core.sim.prod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.strategos.core.content.CorePaths;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.Research;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TechId;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.OutputEvent;
import dev.strategos.core.sim.TechResearched;
import java.util.List;
import org.junit.jupiter.api.Test;

/** TECH-04 in L1: what City does to the civic centre's territory radius and to the arrows of the buildings that shoot. */
class CityTerritoryTest {
    static final TechId TOWN = new TechId("strategos:phase_town");
    static final TechId CITY = new TechId("strategos:phase_city");

    private static int net(TemplateSet set, String id) {
        return set.byId(new TemplateId(id)).netIndex();
    }

    private static void spawn(ProdScene p, String template, int k) {
        int x = k < 5 ? 14 + 10 * (k % 4) : 8 + 14 * (k - 5); // the houses' grid, then a row along the far edge
        int z = k < 5 ? 14 + 10 * (k / 4) : 50;
        p.h.submit(PlayerSlot.ADMIN, new SpawnBuilding(new TemplateId(template), new FactionSlot(0), x, z, 0));
        p.h.step();
    }

    private static void research(ProdScene p, TechId tech, int limit) {
        p.h.submit(ProdScene.P0, new Research(p.centre, tech));
        p.h.step();
        assertThat(p.h.results().get(p.h.results().size() - 1).outcome()).isEqualTo(CommandOutcome.ACCEPTED);
        for (int t = 0; t < limit; t++) {
            for (OutputEvent e : p.h.step().events()) {
                if (e instanceof TechResearched) {
                    return;
                }
            }
        }
        throw new AssertionError(tech + " was not researched in " + limit + " ticks");
    }

    @Test
    void theCentresRadiusGrowsByAThirdAndTheArrowsByTwoWhereBuildingsShoot() {
        TemplateSet set = TemplateSet.builtin();
        ProdScene p = ProdScene.of(5_000, 5_000, set);
        for (String r : List.of("stone", "iron")) {
            p.h.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(0), r, 5_000));
        }
        p.h.step();
        for (int k = 0; k < 5; k++) {
            spawn(p, "strategos:building/house", k);
        }
        int radius = p.s.stats().path(CorePaths.TERRITORY_RADIUS);
        int arrows = p.s.stats().path(CorePaths.BUILDING_ARROWS);
        int centre = net(set, "strategos:building/civic_centre");
        int tower = net(set, "strategos:building/tower");
        int sentry = net(set, "strategos:building/sentry_tower");
        int house = net(set, "strategos:building/house");
        int farmstead = net(set, "strategos:building/farmstead");
        double centreRadius = p.s.stats().blockValue(centre, 0, radius);
        double houseRadius = p.s.stats().blockValue(house, 0, radius);
        double farmsteadRadius = p.s.stats().blockValue(farmstead, 0, radius);
        assertThat(centreRadius).isEqualTo(56);
        assertThat(p.s.stats().blockValue(centre, 0, arrows)).isEqualTo(3);
        assertThat(p.s.stats().blockValue(tower, 0, arrows)).isEqualTo(1);
        assertThat(p.s.stats().blockValue(sentry, 0, arrows)).isEqualTo(1);

        research(p, TOWN, 1_300);
        assertThat(p.s.stats().blockValue(centre, 0, radius))
                .as("Town does not touch the radius")
                .isEqualTo(centreRadius);
        spawn(p, "strategos:building/tower", 5);
        spawn(p, "strategos:building/arsenal", 6);
        spawn(p, "strategos:building/temple", 7);
        research(p, CITY, 1_900);

        assertThat(p.s.stats().blockValue(centre, 0, radius)).isCloseTo(centreRadius * 1.3, within(1e-9));
        assertThat(p.s.stats().blockValue(house, 0, radius))
                .as("only the centre")
                .isEqualTo(houseRadius);
        assertThat(p.s.stats().blockValue(farmstead, 0, radius)).isEqualTo(farmsteadRadius);
        assertThat(p.s.stats().blockValue(centre, 0, arrows)).isEqualTo(5);
        assertThat(p.s.stats().blockValue(tower, 0, arrows)).isEqualTo(3);
        assertThat(p.s.stats().blockValue(sentry, 0, arrows)).isEqualTo(3);
        assertThat(p.s.stats().blockValue(house, 0, arrows))
                .as("a building that does not shoot stays unarmed (no such stat)")
                .isNaN();
        assertThat(p.s.stats().blockValue(tower, 1, arrows))
                .as("the other faction's")
                .isEqualTo(1);
        assertThat(p.s.stats().blockValue(centre, 1, radius)).isEqualTo(centreRadius);
    }
}
