package dev.strategos.core.shared;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/** A per-tick kernel: no streams and no boxing (ArchUnit rule B9), and no allocation per call (JMH, QA-07.4). */
@Retention(RUNTIME)
@Target(TYPE)
@Documented
public @interface HotPath {}
