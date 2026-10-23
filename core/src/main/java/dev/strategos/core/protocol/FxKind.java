package dev.strategos.core.protocol;

import dev.strategos.core.shared.CoreApi;

/**
 * What an {@code fx} event shows or sounds: a unit hit or killed (AUD-01), and the world's edits (AUD-04): a block's
 * crack at a stage, a block broken with its particles and sound, a block placed with its sound; a torch's flames on a
 * building (CMB-03). The block kinds carry a datum ({@link #carriesData}): the crack's stage, or the block state's id.
 */
@CoreApi
public enum FxKind {
    UNIT_HIT,
    UNIT_DIED,
    /** The crack overlay on a block, its datum the stage 0 to 9, or -1 to clear it. */
    BLOCK_CRACK,
    /** A block broken: its datum the state's id (Block.getId), for its particles and break sound. */
    BLOCK_BREAK,
    /** A block placed: its datum the state's id, for its place sound. */
    BLOCK_PLACE,
    /** A torch's flames at a building's block: particles and a crackle, never a fire block (CMB-03). */
    FLAME;

    /** Whether events of this kind carry a datum on the wire; the unit kinds do not, so they encode as in M2. */
    public boolean carriesData() {
        return this == BLOCK_CRACK || this == BLOCK_BREAK || this == BLOCK_PLACE;
    }
}
