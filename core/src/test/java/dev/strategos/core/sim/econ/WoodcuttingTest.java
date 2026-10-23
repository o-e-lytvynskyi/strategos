package dev.strategos.core.sim.econ;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.PlaceholderBlueprints;
import dev.strategos.core.content.TemplateCompiler;
import dev.strategos.core.content.TemplateIdTable;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.balance.BalanceSheet;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.OutputEvent;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitStateChanged;
import dev.strategos.core.testkit.BalanceData;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.TreeFixtures.Species;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * ECO-03.1 in L1: with the mod's own templates, one worker at a tree gathers the balance sheet's load in the sheet's
 * time, and takes that much home.
 */
class WoodcuttingTest {
    private static final Path REPO = Path.of(System.getProperty("strategos.repoRoot", ".."));

    @Test
    void oneWorkerGathersTheSheetAmountInTheSheetTime() {
        BalanceData data = BalanceData.read(REPO.resolve("mod/src/main/resources/data"));
        BalanceSheet.Load load = data.sheet().loads().stream()
                .filter(l ->
                        l.template().equals(TemplateSet.WORKER) && l.resource().equals("wood"))
                .findFirst()
                .orElseThrow();
        TemplateCompiler.Result bundled =
                TemplateCompiler.compile(data.raws(), TemplateIdTable.EMPTY, PlaceholderBlueprints.byId());
        assertThat(bundled.problems()).isEmpty();
        EconScene e = EconScene.flat(64, 48).tree(Species.OAK, 16, 24).build(bundled.set(), SimHarness.DEFAULT_SEED);
        e.civicCentre(0, 40, 19);
        int worker = e.workers(0, new Vec2d(30, 24), 1, 0)[0];
        e.gather(new int[] {worker}, 16, 2, 24);

        long start = -1;
        long end = -1;
        for (int t = 0; t < 2_000 && e.gathered(0, EconScene.WOOD) == 0; t++) {
            for (OutputEvent ev : e.h.step().events()) {
                if (ev instanceof UnitStateChanged c && c.handle() == worker) {
                    if (c.to() == UnitState.GATHERING && start < 0) {
                        start = e.h.tick();
                    } else if (c.from() == UnitState.GATHERING && end < 0) {
                        end = e.h.tick();
                    }
                }
            }
        }
        assertThat(start).as("it reached the tree").isPositive();
        assertThat(end - start).as("the sheet's time for a load").isEqualTo(load.ticks());
        assertThat(e.gathered(0, EconScene.WOOD))
                .as("the sheet's load, home")
                .isEqualTo(Amount.of(load.capacity().doubleValue()));
    }
}
