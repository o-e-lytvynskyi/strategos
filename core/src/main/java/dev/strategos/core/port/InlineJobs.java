package dev.strategos.core.port;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** The executor behind {@link JobExecutor#inline()}: a job runs when it is first joined, and keeps its result. */
final class InlineJobs implements JobExecutor {
    private final JobScratch scratch = new JobScratch();

    @Override
    public <R> JobHandle<R> submit(PureJob<R> job) {
        return new JobHandle<>() {
            private @Nullable R result;
            private boolean done;

            @Override
            public boolean isDone() {
                return done;
            }

            @Override
            public R join() {
                if (!done) {
                    result = job.run(scratch);
                    done = true;
                }
                return Objects.requireNonNull(result);
            }
        };
    }

    @Override
    public int threads() {
        return 0;
    }
}
