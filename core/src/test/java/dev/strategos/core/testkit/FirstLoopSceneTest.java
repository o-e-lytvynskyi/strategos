package dev.strategos.core.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.OutputEvent;
import dev.strategos.core.sim.TickOutputs;
import dev.strategos.core.sim.UnitDied;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.BuildingView;
import org.junit.jupiter.api.Test;

/**
 * PERF-06's scene (M3 spec §6) on the core alone: the first loop as the server's soak plays it, its 15 game minutes on
 * the proving ground. The 20 buildings stand from the first tick, four in five workers gather, the three constructions
 * run in at least 95 % of the seconds after the first half minute, and the waves fight and die.
 */
class FirstLoopSceneTest {
    private static final int TICKS = 18_000;

    @Test
    void theFirstLoopRunsAsTheSoakNeedsIt() {
        ProvingGround ground = ProvingGround.generate();
        SimHarness h = SimHarness.builder()
                .size(ProvingGround.SIZE, ProvingGround.SIZE)
                .yBand(0, ProvingGround.HEIGHT, 16)
                .seed(SimHarness.DEFAULT_SEED) // the server soak's match
                .factions(2)
                .build();
        h.publishAll(ground.voxels());
        h.step();
        FirstLoopScene scene = new FirstLoopScene(FirstLoopScene.Config.SOAK, ground.startSlots());
        for (SoakScene.Issue i : scene.setup(TemplateSet.builtin())) {
            h.submit(i.issuer(), i.command());
        }
        long deaths = 0;
        int sampled = 0; // seconds after the first half minute
        int full = 0;
        long[] gatherers = new long[2];
        int buildingsAtStart = -1;
        for (int t = 0; t < TICKS; t++) {
            TickOutputs out = h.step();
            MatchQueries q = h.core().queries();
            for (OutputEvent e : out.events()) {
                if (e instanceof UnitDied) {
                    deaths++;
                } else if (e instanceof CommandResult r && r.kind().equals("strategos:place_building")) {
                    scene.placementResult(r.outcome() == CommandOutcome.ACCEPTED);
                }
            }
            if (buildingsAtStart < 0 && t == 2) {
                buildingsAtStart = (int) q.buildings().stream()
                        .filter(b -> b.state() == BuildingStore.COMPLETE)
                        .count();
            }
            if (t > 600 && t % 20 == 0) {
                sampled++;
                int now = 0;
                for (BuildingView b : q.buildings()) {
                    if (b.state() != BuildingStore.COMPLETE) {
                        now++;
                    }
                }
                if (now >= FirstLoopScene.Config.SOAK.constructions()) {
                    full++;
                }
                for (int f = 0; f < 2; f++) {
                    int[] w = q.workers(new FactionSlot(f));
                    for (int k = 0; k < w.length - 1; k++) { // the last counts the idle
                        gatherers[f] += w[k];
                    }
                }
            }
            for (SoakScene.Issue i : scene.next(q)) {
                h.submit(i.issuer(), i.command());
            }
        }
        assertThat(buildingsAtStart).as("the 20 buildings").isEqualTo(20);
        assertThat(full).as("seconds with every construction running").isGreaterThanOrEqualTo(sampled * 95 / 100);
        assertThat(scene.placed()).as("constructions finished and placed again").isGreaterThanOrEqualTo(20);
        for (int f = 0; f < 2; f++) {
            assertThat((double) gatherers[f] / sampled)
                    .as("faction " + f + "'s gatherers on average")
                    .isGreaterThanOrEqualTo(0.8 * FirstLoopScene.Config.SOAK.workers());
        }
        assertThat(scene.waves()).as("waves").isGreaterThanOrEqualTo(25);
        assertThat(deaths).as("deaths in the waves").isGreaterThan(100);
        assertThat(h.core().queries().unitCount()).isBetween(150, FirstLoopScene.Config.SOAK.units());
    }
}
