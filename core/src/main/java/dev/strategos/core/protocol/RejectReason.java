package dev.strategos.core.protocol;

/**
 * Why a command, or a unit of it, was refused. A hidden unit and a missing one get the same reason,
 * {@link #UNKNOWN_TARGET}, so commands cannot probe the fog (04 §7.3).
 */
public enum RejectReason {
    UNKNOWN_TARGET,
    NOT_OWNER,
    OUT_OF_BOUNDS,
    UNSUPPORTED,
    UNKNOWN_TEMPLATE,
    UNIT_CAP,
    NOT_PERMITTED,
    INVALID_ARGUMENT
}
