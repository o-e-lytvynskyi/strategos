package dev.strategos.mod.server.match;

import dev.strategos.core.protocol.SkirmishSetup;
import dev.strategos.core.sim.MatchLifecycle;
import dev.strategos.mod.StrategosServer;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.server.level.ServerPlayer;

/** Server-thread adapter to actual permissions, membership, M6 allocation and private publication. */
public final class LobbyBackend implements LobbyService.Backend {
    private final StrategosServer server;
    private final Skirmishes.Placer placer;
    private final BiConsumer<UUID, LobbyService.Snapshot> publisher;

    public LobbyBackend(StrategosServer server, BiConsumer<UUID, LobbyService.Snapshot> publisher) {
        this(server, (map, options) -> server.matches().createInArena(map, options), publisher);
    }

    public LobbyBackend(
            StrategosServer server, Skirmishes.Placer placer, BiConsumer<UUID, LobbyService.Snapshot> publisher) {
        this.server = Objects.requireNonNull(server);
        this.placer = Objects.requireNonNull(placer);
        this.publisher = Objects.requireNonNull(publisher);
    }

    @Override
    public boolean mayHost(UUID member) {
        ServerPlayer player = server.server().getPlayerList().getPlayer(member);
        return player != null
                && !player.hasDisconnected()
                && (server.server().isSingleplayerOwner(player.getGameProfile()) || player.hasPermissions(2));
    }

    @Override
    public boolean online(UUID member) {
        ServerPlayer player = server.server().getPlayerList().getPlayer(member);
        return player != null && !player.hasDisconnected();
    }

    @Override
    public boolean seated(UUID member) {
        return server.matches().seatOf(member) != null;
    }

    @Override
    public boolean activeMatch() {
        // Retained faulted/disposed entries also block a new allocation until cleanup is established.
        return server.matches().all().stream().anyMatch(rt -> rt.lifecycle() != MatchLifecycle.ENDED);
    }

    @Override
    public void validate(MatchLobby lobby, SkirmishSetup setup) {
        LobbyPreparation.validate(server, lobby, setup);
    }

    @Override
    public LobbyService.Preparation begin(MatchLobby lobby, SkirmishSetup setup) {
        return LobbyPreparation.start(server, lobby, setup, placer);
    }

    @Override
    public void send(UUID member, LobbyService.Snapshot snapshot) {
        publisher.accept(member, snapshot);
    }
}
