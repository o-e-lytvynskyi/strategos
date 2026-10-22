package dev.strategos.mod.server.jobs;

import dev.strategos.core.port.JobExecutor;
import dev.strategos.core.port.JobHandle;
import dev.strategos.core.port.JobScratch;
import dev.strategos.core.port.PureJob;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Runs each job on the server thread when it is joined ({@code pathThreads = 0}, and every job until T8 brings the
 * worker pool). Server thread only.
 */
public final class InlineJobExecutor implements JobExecutor {
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
