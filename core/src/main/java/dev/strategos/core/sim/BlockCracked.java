package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;

/**
 * The block at match-local ({@code x}, {@code z}) and height {@code y} shows a crack at {@code stage}, 0 to 9, or none
 * at -1 (AUD-04): the next log of a tree being felled, by how much of it has been gathered. Posted only when the stage
 * changes; the mod sends it to those who see the cell.
 */
@CoreApi
public record BlockCracked(int x, int y, int z, int stage) implements MatchEvent {
    /** No crack. */
    public static final int NONE = -1;

    public BlockCracked {
        if (stage < NONE || stage > 9) {
            throw new IllegalArgumentException("crack stage " + stage + " outside -1..9");
        }
    }
}
