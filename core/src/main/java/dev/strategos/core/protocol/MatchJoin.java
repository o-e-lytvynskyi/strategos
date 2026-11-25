package dev.strategos.core.protocol;

import dev.strategos.core.shared.CivId;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.sim.MatchConfig;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@code match_join} payload: the match a player now sits in, its space (the origin's block, its size and its
 * vertical band), how many factions it has, the player's slot, its rules as their canonical JSON and, appended and
 * optional (CIV-01), each faction's civilisation id in faction order: empty means every faction plays the default.
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
        List<String> civs) {
    /** UTF-8 bytes of the rules' JSON. */
    public static final int RULES_BYTES = 3_072;

    /** UTF-8 bytes of one civilisation id. */
    public static final int CIV_BYTES = CivId.MAX_BYTES;

    /** The match's factions all play the default civilisation. */
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
        this(matchId, originX, originY, originZ, sizeX, sizeZ, minY, maxY, factions, slot, rules, List.of());
    }

    public MatchJoin {
        civs = List.copyOf(civs);
        if (civs.size() > MatchConfig.MAX_FACTIONS) {
            throw new IllegalArgumentException("a join of " + civs.size() + " civilisations");
        }
    }

    /** Faction {@code faction}'s civilisation: its entry, or the default when none is listed. */
    public CivId civ(int faction) {
        return faction >= 0 && faction < civs.size() ? new CivId(civs.get(faction)) : CivId.DEFAULT;
    }

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
            if (!value.civs().isEmpty()) { // appended: a join with no civilisations is as it was
                out.writeVarInt(value.civs().size());
                for (String civ : value.civs()) {
                    out.writeString(civ, CIV_BYTES);
                }
            }
        }

        @Override
        public MatchJoin read(WireReader in) {
            int matchId = in.readVarInt();
            return new MatchJoin(
                    matchId,
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
                    readCivs(in));
        }

        @Override
        public int maxSize() {
            return 9 * 5
                    + WireCodecs.PLAYER_SLOT.maxSize()
                    + 2
                    + RULES_BYTES
                    + 1
                    + MatchConfig.MAX_FACTIONS * (1 + CIV_BYTES);
        }
    };

    /** An id that is none reads as the default, so a corrupt entry cannot fail the join. */
    private static String safe(String id) {
        try {
            return new CivId(id).value();
        } catch (IllegalArgumentException e) {
            return CivId.DEFAULT.value();
        }
    }

    private static List<String> readCivs(WireReader in) {
        if (in.remaining() == 0) {
            return List.of();
        }
        int n = in.readVarInt(MatchConfig.MAX_FACTIONS);
        List<String> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(safe(in.readString(CIV_BYTES)));
        }
        return out;
    }
}
