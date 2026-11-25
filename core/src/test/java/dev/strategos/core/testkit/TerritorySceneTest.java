package dev.strategos.core.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.MatchRules;
import dev.strategos.core.sim.OutputEvent;
import dev.strategos.core.sim.PlayRules;
import dev.strategos.core.sim.TickOutputs;
import dev.strategos.core.sim.territory.TerritorySystem;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * PERF-16's scene (M9 spec §2.13) on the core alone, a few game minutes headless: four factions of 150 population come
 * in without a refusal, the churn driver completes, destroys and transfers buildings, units sit garrisoned, and the
 * territory recomputes, never twice within {@value TerritorySystem#CADENCE} ticks.
 */
final class TerritorySceneTest {
    private static final int CHURN = 100;
    private static final long TICKS = 1_800;
    private static FirstSkirmish map;
    private static String skirmish;

    @BeforeAll
    static void load() throws IOException {
        map = FirstSkirmish.generate();
        skirmish = Files.readString(Path.of(System.getProperty("strategos.repoRoot"))
                .resolve("mod/src/main/resources/data/strategos/strategos/ai/skirmish.json"));
    }

    private record Run(
            SimHarness h,
            TerritoryScene scene,
            List<CommandResult> results,
            int mostGarrisoned,
            long closestRecomputes) {}

    private static Run run(TerritoryScene.Config config, long ticks) {
        SimHarness h = SimHarness.builder()
                .size(FirstSkirmish.SIZE, FirstSkirmish.SIZE)
                .yBand(0, FirstSkirmish.HEIGHT, 16)
                .seed(SimHarness.DEFAULT_SEED)
                .factions(TerritoryScene.FACTIONS)
                .rules(MatchRules.DEFAULTS.withPlay(PlayRules.DEFAULTS))
                .starts(map.startSlots())
                .build();
        h.publishAll(map.voxels());
        h.step();
        TerritoryScene scene = new TerritoryScene(config.withAi(skirmish), map.startSlots());
        for (SoakScene.Issue i : scene.setup(TemplateSet.builtin())) {
            h.submit(i.issuer(), i.command());
        }
        List<CommandResult> results = new ArrayList<>();
        int most = 0;
        long lastCount = 0;
        long lastAt = -1_000;
        long closest = Long.MAX_VALUE;
        for (long t = 0; t < ticks; t++) {
            TickOutputs out = h.step();
            for (OutputEvent e : out.events()) {
                if (e instanceof CommandResult r) {
                    results.add(r);
                }
            }
            MatchQueries q = h.core().queries();
            most = Math.max(most, TerritoryScene.garrisoned(q));
            if (q.territoryRecomputes() != lastCount) {
                if (lastAt >= 0) {
                    closest = Math.min(closest, q.tick() - lastAt);
                }
                lastCount = q.territoryRecomputes();
                lastAt = q.tick();
            }
            for (SoakScene.Issue i : scene.next(q)) {
                h.submit(i.issuer(), i.command());
            }
        }
        return new Run(h, scene, results, most, closest);
    }

    @Test
    void fourFactionsOf150PopulationComeInWithoutARefusalAndPlayWithTheComputer() {
        Run run = run(new TerritoryScene.Config(150, 40, CHURN, ""), 5);
        assertThat(run.results())
                .allSatisfy(r -> assertThat(r.outcome()).as(r.toString()).isEqualTo(CommandOutcome.ACCEPTED));
        MatchQueries q = run.h().core().queries();
        for (int f = 0; f < TerritoryScene.FACTIONS; f++) {
            assertThat(q.populationUsed(new FactionSlot(f)))
                    .as("population of faction " + f)
                    .isEqualTo(150);
        }
        assertThat(q.unitCount()).isEqualTo(TerritoryScene.Config.SOAK.units());
    }

    @Test
    void theChurnHappensUnitsAreGarrisonedAndTheTerritoryRecomputesOnItsCadence() {
        Run run = run(new TerritoryScene.Config(150, 40, CHURN, ""), TICKS);
        TerritoryScene scene = run.scene();
        assertThat(scene.churnEvents()).as("churn events").isGreaterThanOrEqualTo(TICKS / CHURN - 1);
        assertThat(scene.completed()).as("completed").isPositive();
        assertThat(scene.destroyed()).as("destroyed").isPositive();
        assertThat(scene.transferred()).as("transferred").isPositive();
        assertThat(run.mostGarrisoned())
                .as("most units inside a building at once")
                .isGreaterThanOrEqualTo(40);
        assertThat(run.h().core().queries().territoryRecomputes())
                .as("recomputes")
                .isGreaterThan(5);
        assertThat(run.closestRecomputes())
                .as("ticks between two deliveries")
                .isGreaterThanOrEqualTo(TerritorySystem.CADENCE);
        assertThat(run.results())
                .filteredOn(r -> r.issuer().isAdmin()
                        && r.outcome() != CommandOutcome.ACCEPTED
                        && (r.kind().equals("strategos:spawn_building")
                                || r.kind().equals("strategos:demolish")))
                .as("churn commands refused")
                .isEmpty();
    }

    @Test
    void aShortRunIsTheSameEveryTime() {
        TerritoryScene.Config config = new TerritoryScene.Config(150, 40, CHURN, "");
        assertThat(run(config, 700).h().stateHash())
                .isEqualTo(run(config, 700).h().stateHash());
    }
}
