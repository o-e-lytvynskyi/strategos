package dev.strategos.core.sim.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.TechResearched;
import dev.strategos.core.sim.TickOutputs;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.BuildingView;
import dev.strategos.core.testkit.ProbeCiv;
import dev.strategos.core.testkit.SimHarness;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * CIV-05, AI-09: the AI plays the probe civilisation (a fixed population of 60, direct delivery, self-building
 * foundations, citizen-soldiers, no house and no storehouse) against the default AI on the first skirmish map. It reaches
 * Town with no house, no dropsite building and no builder assigned to a foundation; its infantry gathers; the faction's
 * gather bonus shows in the rate of the worker and of the infantry; and, as in {@code AiHintsTest}, it keeps growing and
 * never stalls.
 */
class AiProbeTest {
    private static final long MINUTE = 1_200;
    private static final long LIMIT = 16 * MINUTE;
    private static final long[] SEEDS = {1, 2};
    private static final TemplateId HOUSE = new TemplateId("strategos:building/house");
    private static final TemplateId STOREHOUSE = new TemplateId("strategos:building/storehouse");
    private static final TemplateId INFANTRY = new TemplateId("strategos:unit/probe_infantry");
    private static final String WOOD_RATE = "ResourceGatherer/rates/wood/tree";
    private static final String TOWN = "strategos:phase_town";

    /** What the probe (faction 0) did over a run. */
    private static final class Log implements HeadlessMatch.Watcher {
        long town = -1;
        long longestSilence;
        int foreign; // buildings of a template its roster lacks
        int houses;
        int storehouses;
        int builders; // sampled units on a build order
        int gatheringInfantry; // sampled infantry on a gather order
        int unfinishedLate; // sampled foundations still waiting after minute 3
        int popPeak;
        final List<Integer> workers = new ArrayList<>(); // every minute
        final List<Integer> soldiers = new ArrayList<>();
        final List<Integer> gathering = new ArrayList<>();

        @Override
        public void tick(SimHarness h, TickOutputs out) {
            long tick = out.tick();
            MatchState s = KernelAccess.state(h.core());
            for (var e : out.events()) {
                if (e instanceof TechResearched t
                        && t.faction().value() == 0
                        && t.tech().value().equals(TOWN)) {
                    town = tick;
                }
            }
            ComputerPlayer p = s.aiSlots().get(0);
            if (p != null && !s.outcome().defeated(0)) {
                longestSilence = Math.max(longestSilence, tick - Math.max(p.lastCommand(), 1));
            }
            if (tick % 20 != 0) {
                return;
            }
            int w = 0;
            int soldier = 0;
            int g = 0;
            int used = 0;
            for (int handle : h.core().queries().unitHandles()) {
                UnitView u = h.core().queries().unit(handle);
                if (u == null || u.faction().value() != 0) {
                    continue;
                }
                used++;
                boolean infantry = u.template().equals(INFANTRY);
                w += u.template().equals(TemplateSet.WORKER) ? 1 : 0;
                soldier += infantry ? 1 : 0;
                if (u.state() == UnitState.GATHERING) {
                    g++;
                    gatheringInfantry += infantry ? 1 : 0;
                }
                builders += u.state() == UnitState.BUILDING ? 1 : 0;
            }
            popPeak = Math.max(popPeak, used);
            for (BuildingView b : h.core().queries().buildings()) {
                if (b.faction() != 0) {
                    continue;
                }
                houses += b.template().equals(HOUSE) ? 1 : 0;
                storehouses += b.template().equals(STOREHOUSE) ? 1 : 0;
                foreign +=
                        s.civs().hasTemplate(0, s.templates().byId(b.template()).netIndex()) ? 0 : 1;
                unfinishedLate += tick > 3 * MINUTE && b.state() == BuildingStore.FOUNDATION ? 1 : 0;
            }
            if (tick % MINUTE == 0) {
                workers.add(w);
                soldiers.add(soldier);
                gathering.add(g);
            }
        }
    }

    private static Log play(long seed) {
        Log log = new Log();
        HeadlessMatch.Summary m = ProbeMatch.run(seed, LIMIT, log).summary();
        assertThat(m.failure()).as("seed " + seed).isEmpty();
        return log;
    }

    private static double rate(SimHarness h, TemplateId template, int faction) {
        MatchState s = KernelAccess.state(h.core());
        int index = s.templates().byId(template).netIndex();
        return s.stats().blockValue(index, faction, s.stats().path(WOOD_RATE));
    }

    @Test
    void theAiReachesTownWithNoHouseNoDropsiteAndNoBuilderAndKeepsGrowing() {
        for (long seed : SEEDS) {
            Log log = play(seed);
            assertThat(log.town).as("seed %d: the probe's Town", seed).isPositive();
            assertThat(log.houses).as("seed %d: a house", seed).isZero();
            assertThat(log.storehouses).as("seed %d: a storehouse", seed).isZero();
            assertThat(log.foreign)
                    .as("seed %d: a building outside the roster", seed)
                    .isZero();
            assertThat(log.builders).as("seed %d: a builder built", seed).isZero();
            assertThat(log.unfinishedLate)
                    .as("seed %d: a foundation waited for a builder after minute 3", seed)
                    .isZero();
            assertThat(log.popPeak).as("seed %d: the fixed cap of 60", seed).isBetween(30, 60);
            keepsGrowing(seed, log);
            assertThat(log.gatheringInfantry)
                    .as("seed %d: infantry samples on a gather order", seed)
                    .isPositive();
        }
    }

    /** AiHintsTest's checks for the probe: the workers reach the script's number, the army grows, it gathers, it never stalls. */
    private static void keepsGrowing(long seed, Log log) {
        int last = log.workers.size() - 1;
        assertThat(log.workers.get(last))
                .as("seed %d workers %s", seed, log.workers)
                .isGreaterThanOrEqualTo(20);
        assertThat(log.soldiers.subList(5, last + 1).stream()
                        .mapToInt(Integer::intValue)
                        .max()
                        .orElse(0))
                .as("seed %d soldiers %s", seed, log.soldiers)
                .isGreaterThan(log.soldiers.get(4));
        for (int minute = 3; minute <= last; minute++) {
            assertThat(log.gathering.get(minute))
                    .as("seed %d gatherers at minute %d: %s", seed, minute + 1, log.gathering)
                    .isPositive();
        }
        assertThat(log.longestSilence)
                .as("seed %d: the probe sent no command for so long", seed)
                .isLessThanOrEqualTo(HeadlessMatch.STALL);
    }

    @Test
    void theInfantryIsTheGathererClassOfCitizenSoldiers() {
        assertThat(ProbeMatch.SET.byId(INFANTRY).classes()).contains("Gatherer", "Infantry", "Spearman");
        assertThat(ProbeMatch.SET.civ(ProbeCiv.ID).roles().get("infantry")).isEqualTo(INFANTRY);
    }

    @Test
    void theBonusShowsInTheGatherRateOfItsFactionOnly() {
        SimHarness h = SimHarness.builder()
                .templates(ProbeMatch.SET)
                .civs(ProbeMatch.CIVS)
                .factions(2)
                .size(64, 64)
                .yBand(0, 31, 1)
                .build();
        h.step();
        assertThat(rate(h, TemplateSet.WORKER, 0)).isCloseTo(0.6 * 1.1, within(1e-9));
        assertThat(rate(h, INFANTRY, 0)).isCloseTo(0.4 * 1.1, within(1e-9));
        assertThat(rate(h, TemplateSet.WORKER, 1))
                .as("the default has no bonus")
                .isEqualTo(0.6);
    }
}
