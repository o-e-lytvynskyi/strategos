package dev.strategos.core.port;

import dev.strategos.core.shared.CoreApi;

/**
 * A submitted {@link PureJob}. {@link #join()} runs the job on the calling thread if no worker has claimed it, or waits
 * for the worker that holds it; the result is the same either way.
 *
 * @param <R> the result
 */
@CoreApi
public interface JobHandle<R> {
    boolean isDone();

    R join();
}
