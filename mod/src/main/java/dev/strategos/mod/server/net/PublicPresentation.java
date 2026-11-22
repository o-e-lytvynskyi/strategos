package dev.strategos.mod.server.net;

import dev.strategos.core.protocol.MatchPresentation;
import dev.strategos.mod.server.match.MatchRuntime;
import java.util.ArrayList;
import java.util.Objects;
import net.minecraft.server.MinecraftServer;

/** Public identity projection from the current core binding and authenticated adapter roster. */
final class PublicPresentation {
    private PublicPresentation() {}

    static MatchPresentation of(MinecraftServer server, MatchRuntime match) {
        var core = Objects.requireNonNull(match.core());
        var queries = core.queries();
        var participants = new ArrayList<MatchPresentation.Participant>();
        for (var member : match.roster().members()) {
            var slot = match.roster().slotOf(member);
            if (slot == null || slot.isAdmin()) continue;
            var primary = queries.diplomacy().factionOf(slot);
            if (primary.isEmpty()) continue;
            var online = server.getPlayerList().getPlayer(member);
            boolean connected = online != null
                    && !online.hasDisconnected()
                    && match.roster().isConnected(member);
            String name = "—";
            if (online != null) name = online.getGameProfile().getName();
            else {
                var profiles = server.getProfileCache();
                if (profiles != null)
                    name = profiles.get(member)
                            .map(profile -> profile.getName())
                            .orElse("—");
            }
            var faction = primary.orElseThrow();
            participants.add(new MatchPresentation.Participant(
                    slot, faction.value(), queries.diplomacy().teamOf(faction), name, connected));
        }
        return new MatchPresentation(match.id().value(), 0, queries.presentation(), participants);
    }
}
