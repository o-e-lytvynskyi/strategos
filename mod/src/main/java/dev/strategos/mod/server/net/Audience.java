package dev.strategos.mod.server.net;

import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.MatchId;
import net.minecraft.server.level.ServerPlayer;

/** Who a payload goes to (ADR-0016); each payload type declares the kinds it may use. */
public sealed interface Audience {
    enum Kind {
        PLAYER,
        FACTION,
        TRACKERS_OF,
        SEES_CELL,
        MATCH
    }

    Kind kind();

    /** One player. */
    record Player(ServerPlayer player) implements Audience {
        @Override
        public Kind kind() {
            return Kind.PLAYER;
        }
    }

    /** The players seated in a faction of a match. */
    record Faction(MatchId match, FactionSlot faction) implements Audience {
        @Override
        public Kind kind() {
            return Kind.FACTION;
        }
    }

    /** The players vanilla tracks a unit's body for. */
    record TrackersOf(MatchId match, int handle) implements Audience {
        @Override
        public Kind kind() {
            return Kind.TRACKERS_OF;
        }
    }

    /** The players who see a vision cell of a match (fog of war, M8); its members until then. */
    record SeesCell(MatchId match, int cellX, int cellZ) implements Audience {
        @Override
        public Kind kind() {
            return Kind.SEES_CELL;
        }
    }

    /** Every member of a match. */
    record Match(MatchId match) implements Audience {
        @Override
        public Kind kind() {
            return Kind.MATCH;
        }
    }
}
