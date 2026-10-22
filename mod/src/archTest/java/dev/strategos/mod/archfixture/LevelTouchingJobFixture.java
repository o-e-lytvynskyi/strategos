package dev.strategos.mod.archfixture;

import dev.strategos.core.port.JobScratch;
import dev.strategos.core.port.PureJob;
import net.minecraft.world.level.Level;

/** JOBS_NEVER_TOUCH_THE_LEVEL's violating fixture: a job that holds the level it would read from another thread. */
public final class LevelTouchingJobFixture implements PureJob<Object> {
    private final Level level;

    LevelTouchingJobFixture(Level level) {
        this.level = level;
    }

    @Override
    public Object run(JobScratch scratch) {
        return level;
    }
}
