package dev.strategos.core.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.MatchRules;
import dev.strategos.core.sim.OutputEvent;
import dev.strategos.core.sim.PlayRules;
import dev.strategos.core.sim.TickOutputs;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.ai.Levels;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.BuildingView;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * PERF-09's scene (M6 spec §6) on the core alone: on the first skirmish map under fog and {@code Victory.NONE}, each
 * faction holds its population and its finished buildings from the first tick, both are handed to the computer at
 * Hard, no command of the setup is refused, and a short run is the same every time.
 */
final class MvpSceneTest {
    private static final long SHORT = 200;
    private static FirstSkirmish map;
    private static String skirmish;

    @BeforeAll
    static void load() throws IOException {
        map = FirstSkirmish.generate();
        skirmish = Files.readString(Path.of(System.getProperty("strategos.repoRoot"))
                .resolve("mod/src/main/resources/data/strategos/strategos/ai/skirmish.json"));
    }

    private record Run(SimHarness h, List<CommandResult> results) {}

    /** The scene's setup played on MAP-02 under the skirmish rules, {@code ticks} ticks. */
    private static Run run(MvpScene.Config config, long ticks) {
        SimHarness h = SimHarness.builder()
                .size(FirstSkirmish.SIZE, FirstSkirmish.SIZE)
                .yBand(0, FirstSkirmish.HEIGHT, 16)
                .seed(SimHarness.DEFAULT_SEED)
                .rules(MatchRules.DEFAULTS.withPlay(PlayRules.DEFAULTS))
                .starts(map.startSlots())
                .build();
        h.publishAll(map.voxels());
        h.step();
        MvpScene scene = new MvpScene(config.withAi(skirmish), map.startSlots());
        for (SoakScene.Issue i : scene.setup(TemplateSet.builtin())) {
            h.submit(i.issuer(), i.command());
        }
        List<CommandResult> results = new ArrayList<>();
        for (long t = 0; t < ticks; t++) {
            TickOutputs out = h.step();
            for (OutputEvent e : out.events()) {
                if (e instanceof CommandResult r) {
                    results.add(r);
                }
            }
            for (SoakScene.Issue i : scene.next(h.core().queries())) {
                h.submit(i.issuer(), i.command());
            }
        }
        return new Run(h, results);
    }

    private static void holdsItsScale(MvpScene.Config config) {
        Run run = run(config, 5);
        assertThat(run.results())
                .as("no command of the setup is refused")
                .allSatisfy(r -> assertThat(r.outcome()).as(r.toString()).isEqualTo(CommandOutcome.ACCEPTED));
        MatchQueries q = run.h().core().queries();
        for (int f = 0; f < 2; f++) {
            FactionSlot faction = new FactionSlot(f);
            Map<String, Integer> census = new TreeMap<>();
            int units = 0;
            for (int handle : q.unitHandlesOf(faction)) {
                UnitView u = q.unit(handle);
                census.merge(u.template().value(), 1, Integer::sum);
                units++;
            }
            assertThat(q.populationUsed(faction))
                    .as("population of faction " + f + ": " + census)
                    .isEqualTo(config.population());
            assertThat(units).as("units of faction " + f).isEqualTo(config.units() / 2);
            assertThat(census.get("strategos:unit/worker")).as("workers").isEqualTo(config.population() * 2 / 5);
            assertThat(census)
                    .as("the M5 roster and rams")
                    .containsKeys(
                            "strategos:unit/infantry",
                            "strategos:unit/archer",
                            "strategos:unit/horseman",
                            "strategos:unit/ram");
            assertThat(q.buildings().stream()
                            .filter(b -> b.faction() == faction.value() && b.state() == BuildingStore.COMPLETE)
                            .count())
                    .as("finished buildings of faction " + f)
                    .isEqualTo(config.buildings());
            assertThat(KernelAccess.state(run.h().core()).aiSlots().get(f))
                    .as("faction " + f + " is played by the computer")
                    .isNotNull();
        }
        assertThat(q.unitCount()).as("every unit of the match").isEqualTo(config.units());
        assertThat(config.units()).isEqualTo(2 * config.unitsAtStart() / 2);
    }

    @Test
    void theSoakHolds150PopulationAnd30BuildingsAFactionWithTwoAis() {
        assertThat(MvpScene.Config.SOAK.population()).isEqualTo(150);
        assertThat(MvpScene.Config.SOAK.buildings()).isEqualTo(30);
        holdsItsScale(MvpScene.Config.SOAK);
    }

    @Test
    void theStressPassHoldsTwiceTheScale() {
        assertThat(MvpScene.Config.STRESS.population()).isEqualTo(2 * MvpScene.Config.SOAK.population());
        assertThat(MvpScene.Config.STRESS.buildings()).isEqualTo(2 * MvpScene.Config.SOAK.buildings());
        holdsItsScale(MvpScene.Config.STRESS);
    }

    @Test
    void theMatchRunsUnderFogAndCannotEndEarly() {
        Run run = run(MvpScene.Config.SOAK, SHORT);
        PlayRules play = run.h().core().queries().config().rules().play();
        assertThat(play.victory()).isEqualTo(PlayRules.Victory.NONE);
        assertThat(play.explored()).as("fog: no cell starts explored").isFalse();
        assertThat(play.revealMap()).isFalse();
        assertThat(KernelAccess.state(run.h().core()).aiSlots().get(0).profile())
                .isEqualTo(Levels.PROFILE);
        assertThat(run.h().core().queries().buildings().stream()
                        .map(BuildingView::faction)
                        .distinct())
                .as("both factions' buildings stand")
                .contains(0, 1);
    }

    @Test
    void aShortRunIsTheSameEveryTime() {
        Run first = run(MvpScene.Config.SOAK, SHORT);
        Run second = run(MvpScene.Config.SOAK, SHORT);
        assertThat(first.h().stateHash()).isEqualTo(second.h().stateHash());
        assertThat(first.h().core().queries().tick()).isEqualTo(SHORT + 1);
    }
}
