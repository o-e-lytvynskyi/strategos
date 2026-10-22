package dev.strategos.core.protocol;

import com.mojang.serialization.MapCodec;

/**
 * One command type in a catalogue: its id in snapshots, its number on the wire and in journals, its record class, and
 * both codecs.
 */
public record CommandKind<C extends Command>(
        String id, int wireId, Class<C> type, MapCodec<C> codec, WireCodec<C> wire, boolean adminOnly) {
    public CommandKind {
        if (wireId < 1 || wireId > 0xFFFF) {
            throw new IllegalArgumentException("wire id " + wireId + " outside 1..65535 for " + id);
        }
    }
}
