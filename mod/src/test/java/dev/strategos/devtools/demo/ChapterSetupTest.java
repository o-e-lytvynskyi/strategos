package dev.strategos.devtools.demo;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.content.TechDef;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.TemplateId;
import org.junit.jupiter.api.Test;

/** The M6 chapters' set-ups, checked against the content they play on (T25), where the window run found them. */
class ChapterSetupTest {
    private static final TemplateSet SET = TemplateSet.builtin();

    @Test
    void theTownChapterGrantsWhatTheTownPhaseCosts() {
        TechDef town = SET.tech(Ids.PHASE_TOWN);
        assertThat(town).isNotNull();
        town.cost()
                .forEach((resource, cost) -> assertThat(TownChapter.GRANTS.getOrDefault(resource, 0))
                        .as("the grant of %s", resource)
                        .isGreaterThanOrEqualTo((int) Math.ceil(cost)));
    }

    @Test
    void theNoticesWorkersStartOnOpenGroundNotInsideTheHouseTheyBuild() {
        Footprint house = footprint(Ids.HOUSE, 0, NoticesChapter.HOUSE_DZ);
        assertThat(inside(house, NoticesChapter.WORKERS_DX, NoticesChapter.WORKERS_DZ))
                .as("the workers' start is on the house's footprint")
                .isFalse();
        assertThat(inside(footprint(Ids.CIVIC_CENTRE, NoticesChapter.CENTRE_DX, -6), 0, NoticesChapter.WORKERS_DZ))
                .isFalse();
        assertThat(inside(footprint(Ids.STOREHOUSE, NoticesChapter.STOREHOUSE_DX, -6), 0, NoticesChapter.WORKERS_DZ))
                .isFalse();
    }

    @Test
    void theTowerSceneSitsWellInsideTheMatch() {
        int edge = DemoStage.SIZE / 2 - 8;
        for (int dx : new int[] {TowerChapter.TOWER_DX - 4, TowerChapter.ENEMY_DX, TowerChapter.LOOK_DX + 37}) {
            assertThat(Math.abs(dx)).as("x %s", dx).isLessThanOrEqualTo(edge);
        }
    }

    private static Footprint footprint(TemplateId id, int dx, int dz) {
        Blueprint bp = SET.blueprint(SET.byId(id).netIndex());
        return Footprint.of(bp, dx - bp.sizeX() / 2, dz - bp.sizeZ() / 2, 0);
    }

    private static boolean inside(Footprint f, int x, int z) {
        return x >= f.anchorX() && x < f.anchorX() + f.spanX() && z >= f.anchorZ() && z < f.anchorZ() + f.spanZ();
    }
}
