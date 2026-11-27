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
import dev.strategos.core.sim.UnitView;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * PERF-17's scene (M10 spec §2.20) on the core alone: four factions come in without a refusal, each with its formation
 * of 40, the herds of wild boar are in every quadrant, the horsemen's charge cools down and every faction reaches City
 * (played by no computer: its armies' fights in a four-way match end an L1 run on the harness's stuck-unit invariant).
 */
final class M10SceneTest {
    private static FirstSkirmish map;
    private static String skirmish;

    @BeforeAll
    static void load() throws IOException {
        map = FirstSkirmish.generate();
        skirmish = Files.readString(Path.of(System.getProperty("strategos.repoRoot"))
                .resolve("mod/src/main/resources/data/strategos/strategos/ai/skirmish.json"));
    }

    private record Run(SimHarness h, M10Scene scene, List<CommandResult> results) {}

    /** The scene on MAP-02 for {@code ticks}, its factions played by the computer when {@code ai}. */
    private static Run run(M10Scene.Config config, long ticks, boolean ai) {
        SimHarness h = SimHarness.builder()
                .size(FirstSkirmish.SIZE, FirstSkirmish.SIZE)
                .yBand(0, FirstSkirmish.HEIGHT, 16)
                .seed(SimHarness.DEFAULT_SEED)
                .factions(M10Scene.FACTIONS)
                .rules(MatchRules.DEFAULTS.withPlay(PlayRules.DEFAULTS))
                .starts(map.startSlots())
                .build();
        h.publishAll(map.voxels());
        h.step();
        M10Scene scene = new M10Scene(ai ? config.withAi(skirmish) : config, map.startSlots());
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
        return new Run(h, scene, results);
    }

    private static void assertAccepted(Run run) {
        assertThat(run.results())
                .filteredOn(r -> r.issuer().isAdmin() && r.outcome() != CommandOutcome.ACCEPTED)
                .as("the scene's commands refused") // the computer's own are its business
                .extracting(r -> r.kind() + " " + r.firstReason())
                .isEmpty();
    }

    @Test
    void fourFormationsOf40AndTheHerdsComeInWithoutARefusal() {
        Run run = run(M10Scene.Config.SOAK, 10, true);
        assertAccepted(run);
        MatchQueries q = run.h().core().queries();
        assertThat(run.scene().formationCount()).isEqualTo(M10Scene.FACTIONS);
        assertThat(run.scene().formationSize()).isEqualTo(40);
        Map<Integer, Integer> sizes = new HashMap<>();
        for (int f = 0; f < M10Scene.FACTIONS; f++) {
            for (int handle : q.unitHandlesOf(new FactionSlot(f))) {
                int id = q.formationOf(handle);
                if (id != 0) {
                    sizes.merge(id, 1, Integer::sum);
                }
            }
        }
        assertThat(sizes.values()).as("formations by size").containsExactly(40, 40, 40, 40);
        int boars = 0;
        int[] quadrants = new int[4];
        for (int handle : q.unitHandlesOf(new FactionSlot(M10Scene.FACTIONS))) {
            UnitView u = q.unit(handle);
            assertThat(u).isNotNull();
            assertThat(u.template()).isEqualTo(M10Scene.BOAR);
            boars++;
            quadrants[(u.x() < FirstSkirmish.SIZE / 2.0 ? 0 : 1) + (u.z() < FirstSkirmish.SIZE / 2.0 ? 0 : 2)]++;
        }
        assertThat(boars).isEqualTo(M10Scene.Config.SOAK.animals());
        assertThat(quadrants).as("two herds of 6 in each quadrant").containsExactly(12, 12, 12, 12);
        assertThat(q.unitCount()).isEqualTo(M10Scene.Config.SOAK.units());
    }

    @Test
    void theFormationsStandFormedWithTheComputerPlaying() {
        Run run = run(M10Scene.Config.SOAK, 100, true);
        MatchQueries q = run.h().core().queries();
        Map<Integer, Integer> sizes = new HashMap<>();
        for (int f = 0; f < M10Scene.FACTIONS; f++) {
            for (int handle : q.unitHandlesOf(new FactionSlot(f))) {
                int id = q.formationOf(handle);
                if (id != 0) {
                    sizes.merge(id, 1, Integer::sum);
                }
            }
        }
        assertThat(sizes).as("the four formations").hasSize(4);
        assertThat(sizes.values()).allSatisfy(n -> assertThat(n).isGreaterThanOrEqualTo(36));
        Run longer = run(M10Scene.Config.SOAK, 1_500, true);
        assertThat(longer.scene().formedFraction())
                .as("the share of the members standing formed over 1,500 ticks")
                .isGreaterThan(0.75);
    }

    @Test
    void theChargeCoolsDownAndEveryFactionReachesCity() {
        Run run = run(M10Scene.Config.SOAK, 4_300, false);
        assertAccepted(run);
        MatchQueries q = run.h().core().queries();
        assertThat(run.scene().marches()).as("marches").isPositive();
        assertThat(run.scene().charges()).as("charges").isPositive();
        long cooling = 0;
        for (int f = 0; f < M10Scene.FACTIONS; f++) {
            FactionSlot faction = new FactionSlot(f);
            assertThat(q.researched(faction)).as("faction " + f).contains(M10Scene.TOWN, M10Scene.CITY);
            for (int handle : q.unitHandlesOf(faction)) {
                cooling += q.abilityCooldown(handle, "charge") > 0 ? 1 : 0;
            }
        }
        assertThat(cooling).as("horsemen with charge on cooldown").isPositive();
    }

    @Test
    void aShortRunIsTheSameEveryTime() {
        assertThat(run(M10Scene.Config.SOAK, 700, true).h().stateHash())
                .isEqualTo(run(M10Scene.Config.SOAK, 700, true).h().stateHash());
    }
}
