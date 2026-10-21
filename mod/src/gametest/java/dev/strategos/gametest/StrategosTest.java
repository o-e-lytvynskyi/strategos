package dev.strategos.gametest;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/** A GameTest of a suite; {@link StrategosGameTests} turns each into a {@code TestFunction}. */
@Retention(RUNTIME)
@Target(METHOD)
public @interface StrategosTest {
    /** Template {@code strategos:<arena>}, an SNBT file in {@code src/gametest/structures}. */
    String arena() default "arena_flat_32";

    /** default | slow | skeleton | isolation | perf_walk | perf_snapshot | selftest. */
    String batch() default "default";

    int timeoutTicks() default 200;

    long setupTicks() default 0;

    boolean required() default true;

    /** Passed to {@code TestFunction.maxAttempts}. */
    int attempts() default 1;

    /** Passed to {@code TestFunction.requiredSuccesses}, which avoids 1.21.1's swap of the annotation fields. */
    int successes() default 1;

    /** The owner-approved reason, required when {@code attempts() > 1} (QA-04.4). */
    String retryReason() default "";

    /** Changes server-wide state (tick rate, /reload, game rules, time), so it runs alone in its batch (QA-04.5). */
    boolean solo() default false;
}
