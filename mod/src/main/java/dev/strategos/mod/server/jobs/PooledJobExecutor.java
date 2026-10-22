package dev.strategos.mod.server.jobs;

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
 * The path and page job pool (ADR-0015, 04 §9.6): {@code strategos-path-N} daemon threads taking jobs first in, first
 * out. A job runs on the first worker that takes it, or on the thread that joins it first (the server thread, at its
 * due tick), either way with that thread's own scratch, so its result never depends on where it ran. {@link #close()}
 * stops the workers when the server has stopped.
 */
public final class PooledJobExecutor implements JobExecutor, AutoCloseable {
    private final LinkedBlockingQueue<Job<?>> queue = new LinkedBlockingQueue<>();
    private final List<Thread> workers = new ArrayList<>();
    private final ThreadLocal<JobScratch> scratch = ThreadLocal.withInitial(JobScratch::new);
    private volatile boolean closed;

    public PooledJobExecutor(int threads) {
        if (threads < 1 || threads > PathThreads.MAX) {
            throw new IllegalArgumentException("a pool of 1 to " + PathThreads.MAX + " threads, not " + threads);
        }
        for (int k = 0; k < threads; k++) {
            Thread worker = new Thread(this::work, "strategos-path-" + k);
            worker.setDaemon(true);
            worker.start();
            workers.add(worker);
        }
    }

    @Override
    public <R> JobHandle<R> submit(PureJob<R> job) {
        Job<R> handle = new Job<>(job);
        queue.add(handle);
        return handle;
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

    private final class Job<R> implements JobHandle<R> {
        private final PureJob<R> job;
        private final AtomicBoolean claimed = new AtomicBoolean();
        private final CountDownLatch done = new CountDownLatch(1);
        private @Nullable R result;
        private @Nullable Throwable failure;

        Job(PureJob<R> job) {
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
            Throwable t = failure;
            if (t != null) {
                throw new IllegalStateException("a path or page job failed", t);
            }
            R r = result;
            if (r == null) {
                throw new IllegalStateException("a path or page job returned null");
            }
            return r;
        }
    }
}
