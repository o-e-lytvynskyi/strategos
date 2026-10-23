package dev.strategos.core.sim.econ;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.NoticeKind;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.Notice;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.unitai.UnitAi;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.TreeFixtures.Species;
import org.junit.jupiter.api.Test;

/**
 * ECO-05.3 in L1: workers take their loads to the nearest of their faction's dropsites that takes the resource,
 * measured to its footprint; an enemy's nearer centre and a building that takes other resources are passed by; with no
 * dropsite at all a worker keeps its load and idles with a notice.
 */
class DropsiteTest {
    /** The built-in set whose house takes in wood only, a storehouse for this test. */
    private static TemplateSet woodHouses() {
        return EconScene.edited((id, json) -> {
            if (id.equals(EconScene.HOUSE)) {
                JsonObject drop = new JsonObject();
                drop.addProperty("resources", "wood");
                json.add("Dropsite", drop);
            }
        });
    }

    @Test
    void theNearestOwnDropsiteThatTakesTheResource() {
        EconScene e = EconScene.flat(96, 64)
                .tree(Species.OAK, 30, 30)
                .bush(30, 40, 60, 0)
                .build(woodHouses(), SimHarness.DEFAULT_SEED);
        int far = e.civicCentre(0, 70, 25); // 40 blocks east
        int enemy = e.civicCentre(1, 5, 45); // nearer, the enemy's
        int house = e.building(EconScene.HOUSE, 0, 36, 33); // the nearest, wood only
        int[] woodcutter = e.workers(0, new Vec2d(34, 30), 1, 0);
        int[] picker = e.workers(0, new Vec2d(34, 40), 1, 0);
        e.gather(woodcutter, 30, 2, 30);
        e.gather(picker, 30, 1, 40);
        int w = e.index(woodcutter[0]);
        int p = e.index(picker[0]);
        int[] woodTo = {0};
        int[] foodTo = {0};
        assertThat(e.h.runUntil(
                        () -> {
                            if (e.s.ai().code(w) == UnitAi.GATHER_RETURNING.code()) {
                                woodTo[0] = e.s.ai().data0(w);
                            }
                            if (e.s.ai().code(p) == UnitAi.GATHER_RETURNING.code()) {
                                foodTo[0] = e.s.ai().data0(p);
                            }
                            return e.gathered(0, EconScene.WOOD) > 0 && e.gathered(0, EconScene.FOOD) > 0;
                        },
                        3_000))
                .isTrue();
        assertThat(woodTo[0]).as("wood to the house beside the tree").isEqualTo(house);
        assertThat(foodTo[0])
                .as("food past the house and the enemy's centre, to its own")
                .isEqualTo(far);
        assertThat(e.gathered(1, EconScene.FOOD)).as("nothing for the enemy").isZero();
        assertThat(enemy).isNotEqualTo(far);
    }

    @Test
    void withNoDropsiteTheWorkerKeepsItsLoadAndIdles() {
        EconScene e = EconScene.flat(48, 48).bush(20, 20, 60, 0).build();
        int worker = e.workers(0, new Vec2d(24, 24), 1, 0)[0];
        e.gather(new int[] {worker}, 20, 1, 20);
        int i = e.index(worker);
        assertThat(e.h.runUntil(() -> e.s.carry().amount(i) == 10_000, 1_000)).isTrue();
        e.h.step();
        assertThat(e.s.ai().state(i)).isEqualTo(UnitState.IDLE);
        assertThat(e.s.carry().amount(i)).as("kept").isEqualTo(10_000);
        assertThat(e.h.events(Notice.class))
                .filteredOn(n -> n.kind() == NoticeKind.IDLE_WORKER)
                .singleElement()
                .satisfies(n -> assertThat(n.detail()).isEqualTo("no dropsite"));
    }
}
