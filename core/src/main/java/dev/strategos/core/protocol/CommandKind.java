package dev.strategos.core.protocol;

import com.mojang.serialization.MapCodec;

/** One command type in a catalogue: its id in journals and on the wire, its record class and codec. */
public record CommandKind<C extends Command>(String id, Class<C> type, MapCodec<C> codec, boolean adminOnly) {}
