package dev.strategos.mod.entity;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What a client learns about a unit body when it spawns: the match and core handle it shows, its template's net index,
 * its faction and how it moves. Four VarInts and a byte, at most 16 bytes on the wire.
 */
public record UnitSpawnData(int matchId, int handle, int templateIndex, int faction, byte bodyMode) {
    /** The core owns the pose; the body only shows it (M0's one mode). */
    public static final byte KINEMATIC = 0;

    public static final StreamCodec<ByteBuf, UnitSpawnData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            UnitSpawnData::matchId,
            ByteBufCodecs.VAR_INT,
            UnitSpawnData::handle,
            ByteBufCodecs.VAR_INT,
            UnitSpawnData::templateIndex,
            ByteBufCodecs.VAR_INT,
            UnitSpawnData::faction,
            ByteBufCodecs.BYTE,
            UnitSpawnData::bodyMode,
            UnitSpawnData::new);
}
