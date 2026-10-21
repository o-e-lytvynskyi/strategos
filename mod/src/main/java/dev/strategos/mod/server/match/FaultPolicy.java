package dev.strategos.mod.server.match;

/** What a fault in a match's code does after it is recorded: crash the server, or leave the match FAULTED. */
public enum FaultPolicy {
    RETHROW,
    CONTAIN
}
