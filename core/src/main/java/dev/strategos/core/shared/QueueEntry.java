package dev.strategos.core.shared;

/**
 * A production queue's entry as the store, the snapshot and {@code building_state} carry it (TECH-01, spec row 18): a
 * unit template's net index as it is, or a technology as {@code -1 - its net index}, so a queue of units reads and
 * hashes as before techs.
 */
@CoreApi
public final class QueueEntry {
    private QueueEntry() {}

    /** Whether {@code entry} is a technology. */
    public static boolean isTech(int entry) {
        return entry < 0;
    }

    /** The net index of the technology {@code entry} names; only for a tech entry. */
    public static int techIndex(int entry) {
        return -1 - entry;
    }

    /** The entry that names the technology with net index {@code techIndex}. */
    public static int techEntry(int techIndex) {
        return -1 - techIndex;
    }
}
