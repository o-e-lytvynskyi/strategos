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
    INVALID_ARGUMENT,
    /** The unit already holds the most orders a queue takes (ORD-02). */
    QUEUE_FULL,
    /** The faction cannot pay; the result names the missing amounts (ECO-01). */
    NOT_ENOUGH_RESOURCES,
    /** A placement over a footprint, a node or a reserved cell (BLD-02). */
    BLOCKED,
    /** A placement over water or no ground (BLD-02). */
    NOT_ON_LAND,
    /** A placement on ground not yet explored (BLD-02). */
    NOT_EXPLORED,
    /** A placement on ground that rises more than two blocks across it (BLD-02). */
    NOT_FLAT,
    /** A civic centre nearer another than their spacing (BLD-02). */
    TOO_CLOSE,
    /** A building beyond its reach of its faction's civic centres (BLD-02). */
    OUT_OF_REACH,
    /** A building its faction has as many of as its {@code Limit/count} allows (BLD-08). */
    LIMIT
}
