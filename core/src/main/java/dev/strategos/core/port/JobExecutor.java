package dev.strategos.core.port;

import dev.strategos.core.shared.CoreApi;

/** Runs {@link PureJob}s: on worker threads, or at their {@code join} when it has none ({@link #threads()} 0). */
@CoreApi
public interface JobExecutor {
    <R> JobHandle<R> submit(PureJob<R> job);

    int threads();
}
