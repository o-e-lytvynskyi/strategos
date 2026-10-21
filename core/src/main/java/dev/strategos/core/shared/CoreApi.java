package dev.strategos.core.shared;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/** A type of {@code core.sim} or {@code core.diag} that the mod may use (ArchUnit rule B2); nested types carry it too. */
@Retention(RUNTIME)
@Target(TYPE)
@Documented
public @interface CoreApi {}
