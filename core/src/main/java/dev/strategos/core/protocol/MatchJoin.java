package dev.strategos.core.protocol;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.PlayerSlot;

/**
 * The {@code match_join} payload: the match a player now sits in, its space (the origin's block, its size and its
 * vertical band), how many factions it has, the player's slot, and its rules as their canonical JSON.
 */
@CoreApi
public record MatchJoin(
        int matchId,
        int originX,
        int originY,
        int originZ,
        int sizeX,
        int sizeZ,
        int minY,
        int maxY,
        int factions,
        PlayerSlot slot,
        String rules) {
    /** UTF-8 bytes of the rules' JSON. */
    public static final int RULES_BYTES = 3_072;

    public static final WireCodec<MatchJoin> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, MatchJoin value) {
            out.writeVarInt(value.matchId());
            out.writeZigZag(value.originX());
            out.writeZigZag(value.originY());
            out.writeZigZag(value.originZ());
            out.writeVarInt(value.sizeX());
            out.writeVarInt(value.sizeZ());
            out.writeZigZag(value.minY());
            out.writeZigZag(value.maxY());
            out.writeVarInt(value.factions());
            WireCodecs.PLAYER_SLOT.write(out, value.slot());
            out.writeString(value.rules(), RULES_BYTES);
        }

        @Override
        public MatchJoin read(WireReader in) {
            return new MatchJoin(
                    in.readVarInt(),
                    in.readZigZagInt(),
                    in.readZigZagInt(),
                    in.readZigZagInt(),
                    in.readVarInt(),
                    in.readVarInt(),
                    in.readZigZagInt(),
                    in.readZigZagInt(),
                    in.readVarInt(),
                    WireCodecs.PLAYER_SLOT.read(in),
                    in.readString(RULES_BYTES));
        }

        @Override
        public int maxSize() {
            return 9 * 5 + WireCodecs.PLAYER_SLOT.maxSize() + 2 + RULES_BYTES;
        }
    };
}
