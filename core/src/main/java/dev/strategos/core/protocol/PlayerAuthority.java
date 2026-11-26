package dev.strategos.core.protocol;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.PlayerSlot;

/** Recipient-specific command rights. Primary identity alone determines private data and fog. */
@CoreApi
public record PlayerAuthority(
        int matchId, PlayerSlot player, long tick, int primaryFaction, int controlMask, int alliedMask) {
    private static final int ALL = 65535;

    public PlayerAuthority {
        if (matchId <= 0 || tick < 0 || (controlMask & ~ALL) != 0 || (alliedMask & ~ALL) != 0) {
            throw new IllegalArgumentException("authority context or masks outside bounds");
        }
        if (player.isAdmin()) {
            if (primaryFaction != -1 || controlMask != 0 || alliedMask != 0) {
                throw new IllegalArgumentException("an observer has no primary command authority");
            }
        } else if (primaryFaction < 0
                || primaryFaction >= 16
                || (controlMask & (1 << primaryFaction)) == 0
                || (controlMask & ~alliedMask) != 0) {
            throw new IllegalArgumentException("a participant needs its own primary bit and only allied control");
        }
    }

    public boolean controlsFaction(int faction) {
        return faction >= 0 && faction < 16 && (controlMask & (1 << faction)) != 0;
    }

    public boolean alliedFaction(int faction) {
        return faction >= 0 && faction < 16 && (alliedMask & (1 << faction)) != 0;
    }

    /** Tick-free equality for sending changes only; identity and the complete public authority are included. */
    public boolean sameAuthority(PlayerAuthority other) {
        return matchId == other.matchId
                && player.equals(other.player)
                && primaryFaction == other.primaryFaction
                && controlMask == other.controlMask
                && alliedMask == other.alliedMask;
    }

    public static final WireCodec<PlayerAuthority> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, PlayerAuthority value) {
            out.writeVarInt(value.matchId());
            WireCodecs.PLAYER_SLOT.write(out, value.player());
            out.writeVarLong(value.tick())
                    .writeZigZag(value.primaryFaction())
                    .writeVarInt(value.controlMask())
                    .writeVarInt(value.alliedMask());
        }

        @Override
        public PlayerAuthority read(WireReader in) {
            try {
                return new PlayerAuthority(
                        in.readVarInt(),
                        WireCodecs.PLAYER_SLOT.read(in),
                        in.readVarLong(),
                        in.readZigZagInt(),
                        in.readVarInt(),
                        in.readVarInt());
            } catch (IllegalArgumentException ex) {
                throw new WireException("invalid player authority: " + ex.getMessage());
            }
        }

        @Override
        public int maxSize() {
            return 5 + 1 + 9 + 1 + 3 + 3;
        }
    };
}
