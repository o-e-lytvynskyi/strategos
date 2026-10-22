package dev.strategos.core.testkit;

import dev.strategos.core.port.JobExecutor;
import dev.strategos.core.port.JobHandle;
import dev.strategos.core.port.JobScratch;
import dev.strategos.core.port.PureJob;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.Nullable;

/**
 * The test pool (spec §5.7, {@code ThreadCountHashTest}): {@code n} daemon workers with the contract of the mod's pool.
 * A job runs on the first worker that takes it, or on the thread that joins it first, either way with that thread's own
 * scratch; {@link #close()} stops the workers.
 */
public final class ThreadedJobExecutor implements JobExecutor, AutoCloseable {
    private final LinkedBlockingQueue<Task<?>> queue = new LinkedBlockingQueue<>();
    private final List<Thread> workers = new ArrayList<>();
    private final ThreadLocal<JobScratch> scratch = ThreadLocal.withInitial(JobScratch::new);
    private volatile boolean closed;

    public ThreadedJobExecutor(int threads) {
        if (threads < 1) {
            throw new IllegalArgumentException("a pool needs a thread, not " + threads);
        }
        for (int k = 0; k < threads; k++) {
            Thread worker = new Thread(this::work, "test-path-" + k);
            worker.setDaemon(true);
            worker.start();
            workers.add(worker);
        }
    }

    @Override
    public <R> JobHandle<R> submit(PureJob<R> job) {
        Task<R> task = new Task<>(job);
        queue.add(task);
        return task;
    }

    @Override
    public int threads() {
        return workers.size();
    }

    @Override
    public void close() {
        closed = true;
        for (Thread worker : workers) {
            worker.interrupt();
        }
    }

    private void work() {
        try {
            while (!closed) {
                queue.take().claim();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private final class Task<R> implements JobHandle<R> {
        private final PureJob<R> job;
        private final AtomicBoolean claimed = new AtomicBoolean();
        private final CountDownLatch done = new CountDownLatch(1);
        private @Nullable R result;
        private @Nullable Throwable failure;

        Task(PureJob<R> job) {
            this.job = job;
        }

        /** Runs the job here unless another thread took it first. */
        void claim() {
            if (!claimed.compareAndSet(false, true)) {
                return;
            }
            try {
                result = job.run(scratch.get());
            } catch (Throwable t) {
                failure = t;
            } finally {
                done.countDown();
            }
        }

        @Override
        public boolean isDone() {
            return done.getCount() == 0;
        }

        @Override
        public R join() {
            claim();
            boolean interrupted = false;
            while (true) {
                try {
                    done.await();
                    break;
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
            if (failure != null) {
                throw new IllegalStateException(
                        "a job failed on " + Thread.currentThread().getName(), failure);
            }
            R r = result;
            if (r == null) {
                throw new IllegalStateException("a job returned null");
            }
            return r;
        }
    }
}
