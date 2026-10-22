package dev.strategos.mod.server.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.port.JobExecutor;
import dev.strategos.core.port.JobHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * PATH-16 (spec §2.5): the thread count from the setting or the cores, and the pool's contract: every job's result
 * whoever runs it, a join that runs a job no worker took, and named daemon workers.
 */
class PathThreadsTest {

    @Test
    void theCountComesFromTheSettingOrTheCores() {
        assertThat(PathThreads.count(0, 16, true)).as("0 runs jobs inline").isZero();
        assertThat(PathThreads.count(3, 2, false)).as("a setting wins").isEqualTo(3);
        assertThat(PathThreads.count(-1, 12, false)).as("integrated, cores / 3").isEqualTo(4);
        assertThat(PathThreads.count(-1, 6, false)).isEqualTo(2);
        assertThat(PathThreads.count(-1, 2, false)).as("at least one").isEqualTo(1);
        assertThat(PathThreads.count(-1, 8, true))
                .as("dedicated, cores / 2 - 1")
                .isEqualTo(3);
        assertThat(PathThreads.count(-1, 32, true)).as("at most four").isEqualTo(4);
        assertThat(PathThreads.count(-1, 2, true)).isEqualTo(1);
        assertThatThrownBy(() -> PathThreads.count(5, 8, true)).isInstanceOf(IllegalArgumentException.class);
        assertThat(PathThreads.executor(0).threads()).isZero();
    }

    @Test
    void thePoolGivesEveryJobsResultAndNamesItsWorkers() throws InterruptedException {
        try (PooledJobExecutor pool = new PooledJobExecutor(2)) {
            List<JobHandle<String>> handles = new ArrayList<>();
            for (int k = 0; k < 200; k++) {
                int n = k;
                handles.add(pool.submit(
                        scratch -> n + " on " + Thread.currentThread().getName()));
            }
            List<String> results = new ArrayList<>();
            for (JobHandle<String> handle : handles) {
                results.add(handle.join());
            }

            assertThat(pool.threads()).isEqualTo(2);
            for (int k = 0; k < results.size(); k++) {
                assertThat(results.get(k)).startsWith(k + " on ");
            }
            assertThat(results)
                    .as("each job ran on a worker or on the joining thread")
                    .allSatisfy(r -> assertThat(r)
                            .containsAnyOf(
                                    "strategos-path-", Thread.currentThread().getName()));
        }
    }

    @Test
    void aJoinRunsAJobNoWorkerTook() throws InterruptedException {
        CountDownLatch blocked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (PooledJobExecutor pool = new PooledJobExecutor(1)) {
            JobHandle<String> busy = pool.submit(scratch -> {
                blocked.countDown();
                await(release);
                return "worker";
            });
            assertThat(blocked.await(10, TimeUnit.SECONDS)).isTrue();
            JobHandle<String> queued =
                    pool.submit(scratch -> Thread.currentThread().getName());

            String ranOn = queued.join(); // the only worker is busy: the join runs it here

            assertThat(ranOn).isEqualTo(Thread.currentThread().getName());
            assertThat(queued.isDone()).isTrue();
            release.countDown();
            assertThat(busy.join()).isEqualTo("worker");
        }
    }

    @Test
    void aFailedJobFailsItsJoin() {
        JobExecutor pool = new PooledJobExecutor(1);
        try {
            JobHandle<String> handle = pool.submit(scratch -> {
                throw new ArithmeticException("no");
            });

            assertThatThrownBy(handle::join).hasRootCauseInstanceOf(ArithmeticException.class);
        } finally {
            ((PooledJobExecutor) pool).close();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
