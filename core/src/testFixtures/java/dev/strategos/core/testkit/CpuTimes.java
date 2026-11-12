package dev.strategos.core.testkit;

/** Cumulative CPU counters in one common unit: all-processor total, idle, and this process. */
public record CpuTimes(long total, long idle, long process) {
    public CpuTimes {
        if (total < 0 || idle < 0 || process < 0 || idle > total) {
            throw new IllegalArgumentException("invalid cumulative CPU counters");
        }
    }

    /** Foreign busy time over exactly the same interval; no average of differently sized samples. */
    public double foreignUntil(CpuTimes end) {
        long elapsed = end.total - total;
        long idleElapsed = end.idle - idle;
        long ownElapsed = end.process - process;
        if (elapsed <= 0 || idleElapsed < 0 || idleElapsed > elapsed || ownElapsed < 0) {
            throw new IllegalStateException("CPU measurement counters did not advance consistently");
        }
        return Math.max(0, Math.min(1, (double) (elapsed - idleElapsed - ownElapsed) / elapsed));
    }
}
