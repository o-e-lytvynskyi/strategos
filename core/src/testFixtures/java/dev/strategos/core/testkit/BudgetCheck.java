package dev.strategos.core.testkit;

import java.util.ArrayList;
import java.util.List;

/** A time budget: limits on the p50 and p99 of a sample (PERF-01.1); the units are the caller's, usually nanoseconds. */
public record BudgetCheck(String name, long p50Limit, long p99Limit) {
    /** One message per broken limit, naming the measured value and the limit; empty when the sample fits. */
    public List<String> violations(long[] samples) {
        List<String> problems = new ArrayList<>(2);
        long p50 = Percentiles.percentile(samples, 0.50);
        long p99 = Percentiles.percentile(samples, 0.99);
        if (p50 > p50Limit) {
            problems.add(name + ": p50 " + p50 + " above the limit " + p50Limit);
        }
        if (p99 > p99Limit) {
            problems.add(name + ": p99 " + p99 + " above the limit " + p99Limit);
        }
        return problems;
    }
}
