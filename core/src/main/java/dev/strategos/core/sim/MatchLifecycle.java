package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;

/** A match's lifecycle (04 §3.5); the allowed transitions are a switch, so the table has no fields to drift. */
@CoreApi
public enum MatchLifecycle {
    CREATED,
    PREPARING,
    READY,
    RUNNING,
    PAUSED,
    ENDED,
    FAULTED,
    DISPOSED;

    public boolean canTransitionTo(MatchLifecycle next) {
        return switch (this) {
            case CREATED -> next == PREPARING || next == FAULTED || next == DISPOSED;
            case PREPARING -> next == READY || next == FAULTED;
            case READY -> next == RUNNING || next == FAULTED;
            case RUNNING -> next == PAUSED || next == ENDED || next == FAULTED;
            case PAUSED -> next == RUNNING || next == ENDED || next == FAULTED;
            case ENDED, FAULTED -> next == DISPOSED;
            case DISPOSED -> false;
        };
    }

    /** Returns {@code next}, or throws naming both states. */
    public MatchLifecycle transition(MatchLifecycle next) {
        if (!canTransitionTo(next)) {
            throw new IllegalStateException(this + " -> " + next + " is not a lifecycle transition");
        }
        return next;
    }
}
