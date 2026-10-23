package dev.strategos.core.sim.econ;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.UnitGear;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitStateChanged;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.TreeFixtures.Species;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * ECO-05.1 and .5 in L1: a worker's trip is its load gathered in exactly ceil(20 × carried ÷ rate) ticks, the two walks
 * at its walk speed, and a delivery of exactly the load; over seeded runs. A spearman gathers once its template says
 * so, at its own rate, and not before. A match saved mid-trip resumes to the same hashes.
 */
class GatherTimingTest {
    /** A trip's pieces, from the unit's state changes: gathering's ticks and each walk's ticks. */
    record Trip(long gathering, long home, long back) {}

    /** An oak at (16, 24), a civic centre east of it (x 40..50), a worker between them. */
    private static EconScene scene(TemplateSet templates, long seed) {
        EconScene e = EconScene.flat(64, 48).tree(Species.OAK, 16, 24).build(templates, seed);
        e.civicCentre(0, 40, 19);
        return e;
    }

    /** Runs until {@code trips} loads are home; each trip's pieces, from the GATHERING spans and the walks round them. */
    private static List<Trip> trips(EconScene e, int handle, int trips) {
        List<long[]> spans = new ArrayList<>(); // [start, end] of each GATHERING
        List<Long> deliveries = new ArrayList<>();
        long[] open = {-1};
        long lastGathered = e.gathered(0, EconScene.WOOD);
        int seen = e.h.stateChanges().size();
        for (int t = 0; t < 20_000 && deliveries.size() < trips; t++) {
            e.h.step();
            List<UnitStateChanged> changes = e.h.stateChanges();
            for (UnitStateChanged c : changes.subList(seen, changes.size())) {
                if (c.handle() != handle) {
                    continue;
                }
                if (c.to() == UnitState.GATHERING) {
                    open[0] = e.h.tick();
                } else if (c.from() == UnitState.GATHERING) {
                    spans.add(new long[] {open[0], e.h.tick()});
                }
            }
            seen = changes.size();
            long now = e.gathered(0, EconScene.WOOD);
            if (now != lastGathered) {
                assertThat(now - lastGathered).as("a delivery is a whole load").isEqualTo(10_000);
                deliveries.add(e.h.tick());
                lastGathered = now;
            }
        }
        assertThat(deliveries).hasSize(trips);
        List<Trip> out = new ArrayList<>();
        for (int k = 0; k < trips; k++) {
            long[] span = spans.get(k);
            long back = k + 1 < spans.size() ? spans.get(k + 1)[0] - deliveries.get(k) : -1;
            out.add(new Trip(span[1] - span[0], deliveries.get(k) - span[1], back));
        }
        return out;
    }

    @Test
    void aTripIsTheLoadAtTheRateAndTwoWalksOverSeededRuns() {
        long load = (20 * 10_000 + 600 - 1) / 600; // ceil(20 × 10 wood ÷ 0.6 a second), in ticks: 334
        for (long seed : new long[] {1, 7, 42}) {
            EconScene e = scene(TemplateSet.builtin(), seed);
            int worker = e.workers(0, new Vec2d(30, 24), 1, 2)[0];
            e.gather(new int[] {worker}, 16, 2, 24);
            List<Trip> trips = trips(e, worker, 3);
            int i = e.index(worker);
            double speed = e.s.stats().walkSpeed(i);
            // the slot nearest the worker, east of the trunk, and the cell beside the centre's west edge: 22 blocks
            long walk = (long) Math.ceil(DetMath.length(39.5 - 17.5, 0) / speed);
            for (Trip trip : trips) {
                assertThat(trip.gathering())
                        .as("seed %d: gathering a load", seed)
                        .isEqualTo(load);
                // the walk home starts in the tick the load fills, since the unit AI runs before movement; the walk
                // back the tick after the delivery, which is made on arriving, in movement
                assertThat(trip.home() + 1).as("seed %d: the walk home", seed).isEqualTo(walk);
                if (trip.back() >= 0) {
                    assertThat(trip.back()).as("seed %d: the walk back", seed).isEqualTo(walk);
                }
            }
            assertThat(e.s.nodes().amount(1))
                    .isEqualTo(100_000 - 3 * 10_000 - e.s.carry().amount(i));
        }
    }

    @Test
    void aSpearmanGathersWhenDataSaysSo() {
        EconScene plain = scene(TemplateSet.builtin(), SimHarness.DEFAULT_SEED);
        int spearman = plain.units(TemplateSet.INFANTRY, 0, new Vec2d(30, 24), 1, 0)[0];
        plain.gather(new int[] {spearman}, 16, 2, 24);
        plain.h.step();
        CommandResult refused = plain.h.results().get(plain.h.results().size() - 1);
        assertThat(refused.outcome()).isEqualTo(CommandOutcome.REJECTED);
        assertThat(refused.firstReason())
                .as("the bundled spearman does not gather (D-02)")
                .isEqualTo(RejectReason.UNSUPPORTED);

        EconScene citizen = scene(EconScene.gatheringSpearmen(0.4), SimHarness.DEFAULT_SEED);
        int soldier = citizen.units(TemplateSet.INFANTRY, 0, new Vec2d(30, 24), 1, 0)[0];
        citizen.gather(new int[] {soldier}, 16, 2, 24);
        List<Trip> trips = trips(citizen, soldier, 1);
        assertThat(trips.get(0).gathering())
                .as("ten wood at 0.4 a second: 500 ticks")
                .isEqualTo(500);
    }

    @Test
    void aMatchSavedMidTripResumesExactly() {
        EconScene e = scene(TemplateSet.builtin(), SimHarness.DEFAULT_SEED);
        int[] workers = e.workers(0, new Vec2d(30, 24), 3, 3);
        e.gather(workers, 16, 2, 24);
        e.h.runTicks(420); // the loads half gathered, some walking
        assertThat(e.s.carry().empty()).as("someone carries a load").isFalse();
        MatchSnapshot snapshot = e.h.core().snapshot();
        assertThat(snapshot.carried()).isNotEmpty();
        SimHarness restored = SimHarness.restore(
                snapshot, TemplateSet.builtin(), e.h.core().journal().segments().get(0)::slice);
        assertThat(restored.stateHash()).isEqualTo(e.h.stateHash());
        for (int k = 0; k < 900; k++) {
            e.h.step();
            restored.step();
            assertThat(restored.stateHash()).as("tick %d", e.h.tick()).isEqualTo(e.h.stateHash());
        }
        assertThat(e.gathered(0, EconScene.WOOD))
                .as("loads came home meanwhile")
                .isPositive();
    }

    @Test
    void theWorkerShowsItsAxeAndItsLoad() {
        EconScene e = EconScene.flat(64, 48)
                .tree(Species.OAK, 16, 24)
                .bush(16, 34, 60, 0)
                .build();
        e.civicCentre(0, 40, 19);
        int worker = e.workers(0, new Vec2d(30, 24), 1, 0)[0];
        e.gather(new int[] {worker}, 16, 2, 24);
        assertThat(e.h.runUntil(() -> e.gathered(0, EconScene.WOOD) > 0, 2_000)).isTrue();
        e.gather(new int[] {worker}, 16, 1, 34);
        assertThat(e.h.runUntil(() -> e.gathered(0, EconScene.FOOD) > 0, 2_000)).isTrue();
        assertThat(e.h.events(UnitGear.class))
                .as("an axe to the tree, a log on the way home, the axe again, then berries and no tool")
                .containsExactly(
                        new UnitGear(worker, UnitGear.AXE, -1, -1),
                        new UnitGear(worker, UnitGear.AXE, EconScene.WOOD, 0),
                        new UnitGear(worker, UnitGear.AXE, -1, -1),
                        new UnitGear(worker, UnitGear.NONE, -1, -1),
                        new UnitGear(worker, UnitGear.NONE, EconScene.FOOD, 0),
                        new UnitGear(worker, UnitGear.NONE, -1, -1));
    }
}
