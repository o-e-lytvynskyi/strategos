package dev.strategos.core.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.OutputEvent;
import dev.strategos.core.sim.ShotFired;
import dev.strategos.core.sim.TickOutputs;
import dev.strategos.core.sim.UnitDied;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.BuildingView;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * PERF-08's scene (M5 spec §6) on the core alone: the battle as the server's soak plays it, its 5 game minutes on the
 * proving ground. About 300 fighters of the three classes are alive all along, a garrison sits inside each civic
 * centre from the first seconds, the centres' arrows and the archers' shots fly, the waves fight and die, and both
 * centres stand at the end.
 */
@Tag("slow")
class BattleSceneTest {
    private static final int TICKS = 6_000;

    @Test
    void theBattleRunsAsTheSoakNeedsIt() {
        ProvingGround ground = ProvingGround.generate();
        SimHarness h = SimHarness.builder()
                .size(ProvingGround.SIZE, ProvingGround.SIZE)
                .yBand(0, ProvingGround.HEIGHT, 16)
                .seed(SimHarness.DEFAULT_SEED) // the server soak's match
                .factions(2)
                .build();
        h.publishAll(ground.voxels());
        h.step();
        BattleScene.Config config = BattleScene.Config.SOAK;
        BattleScene scene = new BattleScene(config, ground.startSlots());
        for (SoakScene.Issue i : scene.setup(TemplateSet.builtin())) {
            h.submit(i.issuer(), i.command());
        }
        long deaths = 0;
        long townShots = 0;
        long unitShots = 0;
        long fighterTicks = 0;
        int garrisoned = 0;
        int sampled = 0;
        Map<String, Long> died = new TreeMap<>();
        for (int t = 0; t < TICKS; t++) {
            TickOutputs out = h.step();
            MatchQueries q = h.core().queries();
            for (OutputEvent e : out.events()) {
                if (e instanceof UnitDied d) {
                    deaths++;
                    died.merge(String.valueOf(d.faction().value()), 1L, Long::sum);
                } else if (e instanceof ShotFired s) {
                    if (BuildingStore.isBuilding(s.shooter())) {
                        townShots++;
                    } else {
                        unitShots++;
                    }
                }
            }
            if (t >= 600) { // the first wave is in
                sampled++;
                int fighters = 0;
                for (int f = 0; f < 2; f++) {
                    for (int handle : q.unitHandlesOf(new FactionSlot(f))) {
                        UnitView u = q.unit(handle);
                        if (u != null && !u.template().equals(TemplateSet.WORKER)) {
                            fighters++;
                        }
                    }
                }
                fighterTicks += fighters;
            }
            if (t == 600) {
                for (int handle : q.unitHandles()) {
                    UnitView u = q.unit(handle);
                    if (u != null && u.state() == UnitState.GARRISONED) {
                        garrisoned++;
                    }
                }
            }
            for (SoakScene.Issue i : scene.next(q)) {
                h.submit(i.issuer(), i.command());
            }
        }
        MatchQueries q = h.core().queries();
        assertThat(fighterTicks / (double) sampled)
                .as("fighters alive on average")
                .isGreaterThanOrEqualTo(270);
        assertThat(garrisoned).as("workers inside the centres").isEqualTo(2 * config.garrison());
        assertThat(townShots).as("the centres' arrows").isGreaterThan(50);
        assertThat(unitShots).as("the archers' shots").isGreaterThan(500);
        assertThat(deaths).as("deaths in the waves, by faction " + died).isGreaterThan(300);
        assertThat(scene.waves()).as("waves").isGreaterThanOrEqualTo(4);
        assertThat(q.buildings().stream()
                        .filter(b -> b.state() == BuildingStore.COMPLETE && b.faction() < 2)
                        .map(BuildingView::template))
                .as("both centres stand")
                .containsExactly(BattleScene.CIVIC_CENTRE, BattleScene.CIVIC_CENTRE);
    }
}
