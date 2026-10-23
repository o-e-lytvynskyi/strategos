package dev.strategos.core.sim.econ;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.client.hud.Income;
import dev.strategos.core.client.hud.ResourceBar;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.FactionState;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.replicate.ReplicationService;
import dev.strategos.core.testkit.TreeFixtures.Species;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * UI-07.1: fed the faction states a player receives, four a second, the resource bar's income per minute is exactly
 * what the faction gathered over the last minute of the match, food from a bush and wood from an oak, over three seeds.
 */
class IncomeTest {
    @Test
    void perMinuteMatchesAGatheredMinute() {
        for (long seed : new long[] {1, 7, 42}) {
            EconScene e = EconScene.flat(64, 48)
                    .bush(14, 20, 3000, 0)
                    .tree(Species.OAK, 30, 24)
                    .build(TemplateSet.builtin(), seed);
            e.civicCentre(0, 2, 2);
            e.gather(e.workers(0, new Vec2d(14.5, 16.5), 3, 1), 14, 1, 20);
            e.gather(e.workers(0, new Vec2d(26.5, 20.5), 2, 1), 30, 1, 24);
            Income income = new Income();
            List<long[]> gathered = new ArrayList<>(); // per tick from the start of the feed: food, wood
            FactionState last = null;
            for (int t = 0; t < 2_000; t++) {
                e.h.step();
                gathered.add(new long[] {e.gathered(0, EconScene.FOOD), e.gathered(0, EconScene.WOOD)});
                if (t % 5 == 0) {
                    last = ReplicationService.factionState(e.h.core().queries(), 1, new FactionSlot(0));
                    income.accept(last);
                }
            }
            int newest = 1_995; // the last fed tick
            for (int r : new int[] {EconScene.FOOD, EconScene.WOOD}) {
                long minute = gathered.get(newest)[r] - gathered.get(newest - Income.MINUTE)[r];
                assertThat(minute)
                        .as("seed %d resource %d gathered something", seed, r)
                        .isPositive();
                assertThat(income.perMinute(r))
                        .as("seed %d resource %d", seed, r)
                        .isEqualTo(minute);
            }
            ResourceBar bar = ResourceBar.of(last, List.of("food", "wood", "stone", "iron"), income);
            assertThat(bar.resources().get(EconScene.FOOD).perMinute())
                    .isEqualTo(Math.floorDiv(income.perMinute(EconScene.FOOD), 1000));
            assertThat(bar.resources().get(EconScene.FOOD).workers())
                    .as("seed %d", seed)
                    .isEqualTo(3);
            assertThat(bar.resources().get(EconScene.WOOD).workers())
                    .as("seed %d", seed)
                    .isEqualTo(2);
        }
    }
}
