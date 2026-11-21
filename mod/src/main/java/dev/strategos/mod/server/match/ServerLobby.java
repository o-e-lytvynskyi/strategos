package dev.strategos.mod.server.match;

import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.net.Audience;
import dev.strategos.mod.server.net.LobbyAction;
import dev.strategos.mod.server.net.LobbyIntents;
import dev.strategos.mod.server.net.LobbyViews;
import dev.strategos.mod.server.net.payload.LobbyStatePayload;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/** One server's transient lobby. Tick and shutdown are owned by the production server lifecycle. */
public final class ServerLobby {
    private final StrategosServer server;
    private final LobbyBackend backend;
    private final LobbyService service;
    private final Map<UUID, String> names = new HashMap<>();

    public ServerLobby(StrategosServer server) {
        this(server, (map, options) -> server.matches().createInArena(map, options));
    }

    public ServerLobby(StrategosServer server, Skirmishes.Placer placer) {
        this.server = server;
        this.backend = new LobbyBackend(server, placer, this::publish);
        this.service = new LobbyService(backend);
    }

    public LobbyService service() {
        return service;
    }

    /** sender is supplied only by the authenticated server-side connection handler. */
    public void accept(UUID sender, LobbyAction action) {
        LobbyIntents.accept(service, sender, action);
    }

    /** Production solo setup shares the lobby's single active match policy. Test placers remain isolated seams. */
    public void requireSoloStartAllowed() {
        if (service.reservesStart() || backend.activeMatch()) {
            throw new MatchRefusedException("a lobby or match is already active");
        }
    }

    public void tick() {
        service.tick();
    }

    public void shutdown() {
        service.shutdown();
        names.clear();
    }

    private void publish(UUID member, LobbyService.Snapshot snapshot) {
        // Keep names for a disconnected host's final CLOSED view, bounded by the actual four-seat roster.
        Set<UUID> members = new HashSet<>();
        for (MatchLobby.Seat seat : snapshot.lobby().seats()) {
            UUID occupant = seat.occupant();
            if (occupant == null) continue;
            members.add(occupant);
            ServerPlayer player = server.server().getPlayerList().getPlayer(occupant);
            if (player != null) names.put(occupant, player.getGameProfile().getName());
        }
        names.keySet().retainAll(members);
        ServerPlayer recipient = server.server().getPlayerList().getPlayer(member);
        if (recipient == null || recipient.hasDisconnected()) return;
        var state = LobbyViews.project(
                snapshot, member, service::isHost, service.isHost(member) || backend.mayHost(member), occupant -> {
                    String name = names.get(occupant);
                    if (name == null) throw new IllegalStateException("lobby member has no retained display name");
                    return name;
                });
        server.net().send(new Audience.Player(recipient), new LobbyStatePayload(state));
    }
}
