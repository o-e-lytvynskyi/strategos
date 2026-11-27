package dev.strategos.mod.entity;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What a client learns about a unit body when it spawns: the match and core handle it shows, its template's net index,
 * its faction, how it moves and its rank (CMB-11, 0 for none, the insignia it wears). Four VarInts, a byte and a rank
 * byte, at most 14 bytes on the wire.
 */
public record UnitSpawnData(int matchId, int handle, int templateIndex, int faction, byte bodyMode, byte rank) {
    /** The core owns the pose; the body only shows it (M0's one mode). */
    public static final byte KINEMATIC = 0;

    /** A body without rank. */
    public UnitSpawnData(int matchId, int handle, int templateIndex, int faction, byte bodyMode) {
        this(matchId, handle, templateIndex, faction, bodyMode, (byte) 0);
    }

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
            ByteBufCodecs.BYTE,
            UnitSpawnData::rank,
            UnitSpawnData::new);
}
