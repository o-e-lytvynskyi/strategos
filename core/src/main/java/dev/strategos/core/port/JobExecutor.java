package dev.strategos.core.port;

import dev.strategos.core.shared.CoreApi;

/** Runs {@link PureJob}s: on worker threads, or at their {@code join} when it has none ({@link #threads()} 0). */
@CoreApi
public interface JobExecutor {
    <R> JobHandle<R> submit(PureJob<R> job);

    int threads();

    /**
     * An executor without threads: each job runs on the thread that joins it, with that executor's one scratch, so it
     * belongs to one thread ({@code pathThreads = 0}, L1, replays).
     */
    static JobExecutor inline() {
        return new InlineJobs();
    }
}
