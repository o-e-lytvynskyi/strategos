package dev.strategos.core.port;

import dev.strategos.core.shared.CoreApi;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Reusable working memory of one thread's jobs, such as a page builder's arrays: what a job keeps here never changes
 * its result. Each worker has its own; the inline executor has one for its caller.
 */
@CoreApi
public final class JobScratch {
    private final Map<Class<?>, Object> parts = new HashMap<>();

    /** This thread's instance of {@code type}, made by {@code create} on first use. */
    public <T> T get(Class<T> type, Supplier<T> create) {
        return type.cast(parts.computeIfAbsent(type, k -> create.get()));
    }
}
