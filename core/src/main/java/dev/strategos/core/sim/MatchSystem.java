package dev.strategos.core.sim;

/** One step of the match logic, run by the {@link Scheduler} in its phase. */
public interface MatchSystem {
    Phase phase();

    default Cadence cadence() {
        return Cadence.EVERY_TICK;
    }

    /** A system whose work is large enough that two of them must not share a phase and cadence. */
    default boolean heavy() {
        return false;
    }

    void run(MatchState state);
}
