package dev.strategos.core.testkit;

/** A match invariant broke; the message starts with {@code seed=<n> tick=<t>: } (QA-02.3). */
public final class InvariantViolation extends AssertionError {
    public InvariantViolation(String message) {
        super(message);
    }
}
