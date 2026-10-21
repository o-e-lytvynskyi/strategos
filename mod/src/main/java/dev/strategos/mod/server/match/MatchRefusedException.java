package dev.strategos.mod.server.match;

/** A match could not be made or used; the message says why, for a command's reply or a test's failure. */
public final class MatchRefusedException extends RuntimeException {
    public MatchRefusedException(String reason) {
        super(reason);
    }
}
