package dev.strategos.core.protocol;

/** A command's result: all of it applied, some of its units refused, or all of it refused. */
public enum CommandOutcome {
    ACCEPTED,
    PARTIAL,
    REJECTED
}
