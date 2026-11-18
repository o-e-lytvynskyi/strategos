package dev.strategos.core.protocol;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.PlayerSlot;

/**
 * The {@code match_join} payload: the match a player now sits in, its space (the origin's block, its size and its
 * vertical band), faction count, authenticated player/primary/control view, and canonical rules JSON.
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
        String rules,
        PlayerAuthority authority) {
    public MatchJoin {
        int allowed = factions >= 16 ? 65535 : factions <= 0 ? 0 : (1 << factions) - 1;
        if (authority.matchId() != matchId
                || !authority.player().equals(slot)
                || authority.primaryFaction() >= factions
                || ((authority.controlMask() | authority.alliedMask()) & ~allowed) != 0) {
            throw new IllegalArgumentException("joined authority differs from its player, match or faction bounds");
        }
    }

    /** Legacy identity-only fixture constructor. Production joins supply authenticated authority explicitly. */
    public MatchJoin(
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
        this(
                matchId,
                originX,
                originY,
                originZ,
                sizeX,
                sizeZ,
                minY,
                maxY,
                factions,
                slot,
                rules,
                new PlayerAuthority(
                        matchId,
                        slot,
                        0,
                        slot.isAdmin() ? -1 : slot.value(),
                        slot.isAdmin() ? 0 : 1 << slot.value(),
                        slot.isAdmin() ? 0 : 1 << slot.value()));
    }

    public int primaryFaction() {
        return authority.primaryFaction();
    }

    public boolean controlsFaction(int faction) {
        return authority.controlsFaction(faction);
    }

    public boolean alliedFaction(int faction) {
        return authority.alliedFaction(faction);
    }

    /** Permission updates cannot rebind a join or restore a stale grant. A rejoin supplies a new initial context. */
    public MatchJoin withAuthority(PlayerAuthority next) {
        int allowed = factions >= 16 ? 65535 : factions <= 0 ? 0 : (1 << factions) - 1;
        if (next.matchId() != matchId
                || !next.player().equals(slot)
                || next.tick() <= authority.tick()
                || next.primaryFaction() != primaryFaction()
                || ((next.controlMask() | next.alliedMask()) & ~allowed) != 0) return this;
        return new MatchJoin(matchId, originX, originY, originZ, sizeX, sizeZ, minY, maxY, factions, slot, rules, next);
    }

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
            PlayerAuthority.WIRE.write(out, value.authority());
        }

        @Override
        public MatchJoin read(WireReader in) {
            try {
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
                        in.readString(RULES_BYTES),
                        PlayerAuthority.WIRE.read(in));
            } catch (IllegalArgumentException ex) {
                throw new WireException("invalid joined authority: " + ex.getMessage());
            }
        }

        @Override
        public int maxSize() {
            return 9 * 5 + WireCodecs.PLAYER_SLOT.maxSize() + 2 + RULES_BYTES + PlayerAuthority.WIRE.maxSize();
        }
    };
}
