package dev.strategos.core.port;

import dev.strategos.core.shared.CoreApi;

/**
 * Work that reads only what it was given and returns a value (spec §5.7): a page build or a path search. It may run on
 * any thread; it never touches the level (rule {@code JOBS_NEVER_TOUCH_THE_LEVEL}), so its result is the same wherever
 * it runs.
 *
 * @param <R> the result
 */
@CoreApi
@FunctionalInterface
public interface PureJob<R> {
    R run(JobScratch scratch);
}
