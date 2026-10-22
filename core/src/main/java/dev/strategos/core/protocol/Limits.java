package dev.strategos.core.protocol;

/** The bounds every decoder enforces (04 §7.3, §12.6): what a client may send and what a record may hold. */
public final class Limits {
    /** Commands in one {@code commands} payload. */
    public static final int BATCH_COMMANDS = 32;

    /** Bytes of one {@code commands} payload. */
    public static final int BATCH_BYTES = 4_096;

    /** Unit handles in one command. */
    public static final int UNITS_PER_COMMAND = UnitSet.MAX_SIZE;

    /** UTF-8 bytes of a namespaced id such as a template id. */
    public static final int ID_BYTES = 128;

    private Limits() {}
}
