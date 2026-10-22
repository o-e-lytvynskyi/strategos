package dev.strategos.core.sim.nav;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.port.JobExecutor;
import dev.strategos.core.port.JobHandle;
import dev.strategos.core.port.PureJob;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchRules;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import dev.strategos.core.testkit.VoxelWorld;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * PATH-16.2 (spec §5.7): admission never puts more than W of expansion caps into one tick's jobs, and every waiting
 * request is admitted in turn; with 2,048 requests outstanding, the next one is answered {@code BUSY}, not {@code
 * NO_PATH}: its unit steers at its target and asks again ten ticks later.
 */
class AdmissionTest {
    private static final FactionSlot F0 = new FactionSlot(0);

    @Test
    void noTickAdmitsMoreThanW() {
        CountingJobs jobs = new CountingJobs();
        MatchRules rules = rules(60_000); // three searches of 20,000 a tick
        SimHarness h = harness(VoxelFixtures.flat(128, 32, 16, 1), rules, jobs);
        int[] units = new int[20];
        for (int k = 0; k < units.length; k++) {
            units[k] = h.spawn(F0, new Vec2d(4.5, 1.5 + k * 1.5), 1, 0)[0];
        }
        for (int unit : units) {
            h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(unit), new Vec2d(120.5, 16.5)));
        }
        List<Integer> perTick = new ArrayList<>();

        for (int t = 0; t < 12; t++) {
            int before = jobs.submitted;
            h.step();
            perTick.add(jobs.submitted - before);
        }

        assertThat(perTick).allSatisfy(n -> assertThat(n).isLessThanOrEqualTo(3));
        assertThat(perTick).startsWith(3, 3, 3, 3, 3, 3, 2);
        assertThat(jobs.submitted).isEqualTo(units.length);
        assertThat(h.queries().pathStats().ok()).isEqualTo(units.length);
        assertThat(h.queries().pathStats().waiting()).isZero();
    }

    @Test
    void theRequestPastTheLimitIsBusyAndAsksAgain() {
        MatchRules rules = new MatchRules(4_096, 4_096); // every move order of the tick applies at once
        SimHarness h = harness(VoxelFixtures.flat(192, 64, 16, 1), rules, JobExecutor.inline());
        List<Integer> units = new ArrayList<>();
        while (units.size() < rules.maxOutstanding() + 1) {
            int n = Math.min(256, rules.maxOutstanding() + 1 - units.size());
            for (int unit : h.spawn(F0, new Vec2d(24, 32), n, 20)) {
                units.add(unit);
            }
        }
        for (int unit : units) {
            h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(unit), new Vec2d(180.5, 32.5)));
        }
        h.step();

        int last = units.get(units.size() - 1);
        int i = Handle.index(last);
        PathStore paths = state(h).paths();
        assertThat(h.queries().pathStats().busy()).isEqualTo(1);
        assertThat(paths.status(i)).isEqualTo(PathStatus.BUSY);
        assertThat(paths.request(i)).as("it waits for no request").isZero();
        assertThat(paths.outstanding()).isEqualTo(rules.maxOutstanding());
        UnitView before = unit(h, last);

        h.runTicks(rules.busyRetry() - 1);
        UnitView later = unit(h, last);
        assertThat(paths.status(i)).as("still waiting to ask again").isEqualTo(PathStatus.BUSY);
        assertThat(later.x()).as("it steers at its target meanwhile").isGreaterThan(before.x());

        h.step();
        int asked = paths.request(i);
        assertThat(asked).as("asked again, %s ticks on", rules.busyRetry()).isPositive();
        assertThat(paths.waiting()).as("and waits in line").contains(new PathStore.Waiting(asked, last));
        assertThat(h.queries().pathStats().busy()).isEqualTo(1);
        assertThat(h.queries().pathStats().noPath()).isZero();
    }

    /** The default rules with admission window {@code w}. */
    private static MatchRules rules(int w) {
        MatchRules d = MatchRules.DEFAULTS;
        return new MatchRules(
                d.commandsPerTick(),
                d.unitCap(),
                d.delivery(),
                d.pathK(),
                d.pageK(),
                w,
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

    private static UnitView unit(SimHarness h, int handle) {
        UnitView view = h.queries().unit(handle);
        assertThat(view).isNotNull();
        return view;
    }

    private static MatchState state(SimHarness h) {
        return KernelAccess.state(h.core());
    }

    /** Inline jobs, counted as they are submitted. */
    private static final class CountingJobs implements JobExecutor {
        private final JobExecutor inline = JobExecutor.inline();
        int submitted;

        @Override
        public <R> JobHandle<R> submit(PureJob<R> job) {
            submitted++;
            return inline.submit(job);
        }

        @Override
        public int threads() {
            return 0;
        }
    }
}
