package dev.strategos.mod.server.tick;

import dev.strategos.core.shared.MatchId;

/** A match fault rethrown after it was recorded, under the RETHROW policy: loud in dev runs and GameTests. */
public final class MatchFaultException extends RuntimeException {
    public MatchFaultException(MatchId id, String where, Throwable cause) {
        super("match " + id + " faulted in " + where + ": " + cause, cause);
    }
}
