package dev.strategos.core.sim.nav;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.port.JobExecutor;
import dev.strategos.core.port.JobHandle;
import dev.strategos.core.port.JobScratch;
import dev.strategos.core.port.PureJob;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.JobDelivery;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchRules;
import dev.strategos.core.sim.TerrainPage;
import dev.strategos.core.sim.TickInputs;
import dev.strategos.core.sim.journal.Journal;
import dev.strategos.core.sim.journal.ReplayDriver;
import dev.strategos.core.sim.journal.ReplayResult;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.ThreadedJobExecutor;
import dev.strategos.core.testkit.VoxelFixtures;
import dev.strategos.core.testkit.VoxelWorld;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * PATH-16.1 (spec §2.5): a match of path requests, group places and a wall built mid-walk hashes the same on every tick
 * with 0, 1 and 4 path threads in {@code STRICT}; {@code ADAPTIVE} without threads delivers as {@code STRICT}; a live
 * {@code ADAPTIVE} run on 4 threads replays from its journal, deliveries and all, to the same hashes on 0 and 1; and
 * {@code ADAPTIVE} applies a listed job once it is due and joins an unlisted one {@value PathService#HELP_AFTER} ticks
 * late.
 */
class ThreadCountHashTest {
    private static final FactionSlot F0 = new FactionSlot(0);
    private static final int TICKS = 260;

    @Test
    void strictHashesAreTheSameOnZeroOneAndFourThreads() {
        long[] inline = run(DeliveryMode.STRICT, JobExecutor.inline()).hashes;
        try (ThreadedJobExecutor one = new ThreadedJobExecutor(1);
                ThreadedJobExecutor four = new ThreadedJobExecutor(4)) {
            assertThat(run(DeliveryMode.STRICT, one).hashes).isEqualTo(inline);
            assertThat(run(DeliveryMode.STRICT, four).hashes).isEqualTo(inline);
        }
    }

    @Test
    void adaptiveWithoutThreadsDeliversAsStrict() {
        assertThat(run(DeliveryMode.ADAPTIVE, JobExecutor.inline()).hashes)
                .isEqualTo(run(DeliveryMode.STRICT, JobExecutor.inline()).hashes);
    }

    @Test
    void anAdaptiveRunOnFourThreadsReplaysOnZeroAndOne() {
        Run live;
        try (ThreadedJobExecutor four = new ThreadedJobExecutor(4)) {
            live = run(DeliveryMode.ADAPTIVE, four);
        }
        Journal journal = live.harness.core().journal();
        long liveHash = live.hashes[live.hashes.length - 1];

        ReplayResult inline = ReplayDriver.replay(journal, TemplateSet.builtin());
        ReplayResult one;
        try (ThreadedJobExecutor pool = new ThreadedJobExecutor(1)) {
            one = ReplayDriver.replay(journal, TemplateSet.builtin(), pool);
        }

        assertThat(journal.checkpointTicks()).isNotEmpty();
        for (ReplayResult replay : List.of(inline, one)) {
            assertThat(replay.matches()).as("every checkpoint").isTrue();
            assertThat(replay.finalHash()).isEqualTo(liveHash);
        }
        int listed = 0;
        for (TickInputs tick : journal.nonEmptyTicks()) {
            listed += (int)
                    tick.events().stream().filter(JobDelivery.class::isInstance).count();
        }
        System.out.println("ADAPTIVE on 4 threads: " + listed + " ticks with a journaled delivery");
    }

    @Test
    void adaptiveAppliesAListedJobOnceDueAndJoinsAnUnlistedOneLate() {
        ManualJobs jobs = new ManualJobs();
        MatchRules rules = rules(DeliveryMode.ADAPTIVE);
        SimHarness h = harness(VoxelFixtures.flat(96, 32, 16, 1), rules, jobs);
        int early = h.spawn(F0, new Vec2d(4.5, 8.5), 1, 0)[0];
        int late = h.spawn(F0, new Vec2d(4.5, 24.5), 1, 0)[0];
        h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(early), new Vec2d(90.5, 8.5)));
        h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(late), new Vec2d(90.5, 24.5)));
        h.step();
        long asked = h.tick();
        jobs.finish(0); // the first job submitted, the early unit's, is done before its due tick; the other never is
        PathStore paths = KernelAccess.state(h.core()).paths();
        long[] applied = {-1, -1};

        for (int t = 0; t < 14; t++) {
            h.step();
            int[] units = {early, late};
            for (int k = 0; k < 2; k++) {
                if (applied[k] < 0 && paths.request(Handle.index(units[k])) == 0) {
                    applied[k] = h.tick();
                }
            }
        }

        assertThat(applied[0]).as("listed: applied on its due tick").isEqualTo(asked + rules.pathK());
        assertThat(applied[1])
                .as("never listed: joined %s ticks after it was due", PathService.HELP_AFTER)
                .isEqualTo(asked + rules.pathK() + PathService.HELP_AFTER);
    }

    /**
     * Forty-eight units: half sent one by one past two walls, half as one group to the far corner; at tick 60 a third
     * wall goes up across their way, and at tick 120 all are sent back. The state hash of every tick.
     */
    private static Run run(DeliveryMode mode, JobExecutor jobs) {
        VoxelWorld w = VoxelFixtures.flat(96, 96, 16, 1);
        w.fill(40, 1, 0, 40, 3, 70, VoxelFixtures.SOLID);
        w.fill(60, 1, 26, 60, 3, 95, VoxelFixtures.SOLID);
        SimHarness h = harness(w, rules(mode), jobs);
        int[] units = h.spawn(F0, new Vec2d(10, 48), 48, 6);
        int[] alone = Arrays.copyOfRange(units, 0, 24);
        int[] group = Arrays.copyOfRange(units, 24, 48);
        for (int k = 0; k < alone.length; k++) {
            h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(alone[k]), new Vec2d(88.5, 8.5 + 3 * k)));
        }
        h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(group), new Vec2d(84.5, 84.5)));
        long[] hashes = new long[TICKS];
        for (int t = 0; t < TICKS; t++) {
            if (t == 60) {
                w.fill(75, 1, 0, 75, 3, 60, VoxelFixtures.SOLID);
                for (int pz = 0; pz < 4; pz++) {
                    PageCoord page = new PageCoord(4, pz);
                    h.publish(TerrainPage.build(page, w.slice(page), new PageBuilder(MobilityClass.INFANTRY)));
                }
            }
            if (t == 120) {
                h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(units), new Vec2d(8.5, 48.5)));
            }
            h.step();
            hashes[t] = h.stateHash();
        }
        assertThat(h.queries().pathStats().ok()).as("the run asks for paths").isGreaterThan(48);
        return new Run(h, hashes);
    }

    /** A run's harness and the state hash of each of its ticks. */
    private static final class Run {
        final SimHarness harness;
        final long[] hashes;

        Run(SimHarness harness, long[] hashes) {
            this.harness = harness;
            this.hashes = hashes;
        }
    }

    private static MatchRules rules(DeliveryMode mode) {
        MatchRules d = MatchRules.DEFAULTS;
        return new MatchRules(
                d.commandsPerTick(),
                d.unitCap(),
                mode,
                d.pathK(),
                d.pageK(),
                d.admissionW(),
                d.maxOutstanding(),
                d.busyRetry(),
                d.searchCap(),
                d.joinCap(),
                d.shortMove(),
                d.groupMin());
    }

    private static SimHarness harness(VoxelWorld world, MatchRules rules, JobExecutor jobs) {
        SimHarness h = SimHarness.builder()
                .size(world.sizeX(), world.sizeZ())
                .rules(rules)
                .jobs(jobs)
                .build();
        h.publishAll(world);
        return h;
    }

    /** A pool in name only: a job is done when the test says so, and runs at its join. */
    private static final class ManualJobs implements JobExecutor {
        private final JobScratch scratch = new JobScratch();
        private final List<Job<?>> submitted = new ArrayList<>();

        void finish(int k) {
            submitted.get(k).run();
        }

        @Override
        public <R> JobHandle<R> submit(PureJob<R> job) {
            Job<R> handle = new Job<>(job);
            submitted.add(handle);
            return handle;
        }

        @Override
        public int threads() {
            return 1;
        }

        private final class Job<R> implements JobHandle<R> {
            private final PureJob<R> job;
            private @Nullable R result;

            Job(PureJob<R> job) {
                this.job = job;
            }

            void run() {
                if (result == null) {
                    result = job.run(scratch);
                }
            }

            @Override
            public boolean isDone() {
                return result != null;
            }

            @Override
            public R join() {
                run();
                R r = result;
                assertThat(r).isNotNull();
                return r;
            }
        }
    }
}
