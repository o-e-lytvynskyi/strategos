package dev.strategos.core.testkit;

/** A scenario's verification failed; an AssertionError, so JUnit and the GameTest runner report it as a failure. */
public final class ScenarioFailure extends AssertionError {
    public ScenarioFailure(String message) {
        super(message);
    }
}
