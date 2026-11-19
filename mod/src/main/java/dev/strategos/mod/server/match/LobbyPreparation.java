package dev.strategos.mod.server.match;

import dev.strategos.core.content.MapManifest;
import dev.strategos.core.content.StartSlot;
import dev.strategos.core.port.MetricsSink;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.SkirmishSetup;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchLifecycle;
import dev.strategos.core.sim.ai.Levels;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.content.MapPackageLoader;
import dev.strategos.mod.server.content.Scenarios;
import dev.strategos.mod.server.net.Replication;
import dev.strategos.mod.server.world.ArenaPolicy;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Real M6 map import, first-tick setup and commander admission. The owning service must cancel an unfinished
 * preparation before server persistence on shutdown; the pending transaction is not a restored lobby (T5).
 * Private lobby status publication, including client reset after rollback, belongs to the service/protocol adapter.
 */
public final class LobbyPreparation implements LobbyService.Preparation {
    private final StrategosServer server;
    private final MatchRuntime match;
    private final LobbyLaunchPlan plan;
    private final List<StartSlot> starts;
    private final String terrainHash;
    private final List<MatchLobby.Seat> humans;
    private int replies;
    private boolean rejected;
    private boolean admitted;
    private int admittedAt;
    private boolean cancelled;
    private boolean completed;

    private LobbyPreparation(
            StrategosServer server,
            MatchRuntime match,
            LobbyLaunchPlan plan,
            List<StartSlot> starts,
            String terrainHash,
            List<MatchLobby.Seat> humans) {
        this.server = server;
        this.match = match;
        this.plan = plan;
        this.starts = List.copyOf(starts);
        this.terrainHash = terrainHash;
        this.humans = List.copyOf(humans);
    }

    private record Inputs(
            ResourceLocation map, MapManifest manifest, LobbyLaunchPlan plan, List<MatchLobby.Seat> humans) {}

    /** Check supported content, bindings and membership without allocating world or match state. */
    public static void validate(StrategosServer server, MatchLobby lobby, SkirmishSetup setup) {
        inspect(server, lobby, setup);
    }

    private static Inputs inspect(StrategosServer server, MatchLobby lobby, SkirmishSetup setup) {
        ResourceLocation map = ResourceLocation.tryParse(setup.map());
        if (map == null || !MapPackageLoader.list(server.server()).contains(map)) {
            throw new IllegalArgumentException("map is not available");
        }
        ResourceLocation scenario = Skirmishes.scenarioFor(server, map);
        if (scenario == null) throw new IllegalArgumentException("map has no scenario");
        var manifest = MapPackageLoader.manifest(server.server(), map);
        LobbyLaunchPlan plan = LobbyLaunchPlan.create(
                lobby,
                setup,
                Scenarios.load(server.server(), scenario),
                manifest.startSlots(),
                server.templates().set(),
                Set.of("strategos:placeholder"),
                Scenarios.script(server.server(), Levels.PROFILE));
        List<MatchLobby.Seat> humans = lobby.seats().stream()
                .filter(s -> s.kind() == MatchLobby.Kind.HUMAN)
                .toList();
        for (MatchLobby.Seat seat : humans) {
            UUID member = Objects.requireNonNull(seat.occupant());
            ServerPlayer player = server.server().getPlayerList().getPlayer(member);
            if (player == null || player.hasDisconnected() || server.matches().seatOf(member) != null) {
                throw new IllegalArgumentException("lobby member is unavailable");
            }
        }
        return new Inputs(map, manifest, plan, humans);
    }

    public static LobbyPreparation start(
            StrategosServer server, MatchLobby lobby, SkirmishSetup setup, Skirmishes.Placer placer) {
        Inputs inputs = inspect(server, lobby, setup);
        ResourceLocation map = inputs.map();
        var manifest = inputs.manifest();
        LobbyLaunchPlan plan = inputs.plan();
        List<MatchLobby.Seat> humans = inputs.humans();
        MatchOptions options = new MatchOptions(
                null,
                plan.factions(),
                true,
                MatchSource.GENERATED,
                null,
                MetricsSink.none(),
                ArenaPolicy.FLOOR_Y,
                null,
                plan.play());
        MatchRuntime rt = placer.place(map, options);
        LobbyPreparation preparation =
                new LobbyPreparation(server, rt, plan, manifest.startSlots(), manifest.terrainHash(), humans);
        try {
            var data = rt.data();
            if (data != null) data.beginLobbyLaunch();
            MatchCore core = Objects.requireNonNull(rt.core());
            if (core.queries().templates().hash() != plan.templateSetHash()) {
                throw new IllegalStateException("template version changed during allocation");
            }
            rt.initializeDiplomacy(plan.authority(), plan.presentation());
            for (var command : plan.commands()) {
                rt.submit(PlayerSlot.ADMIN, command, result -> {
                    preparation.replies++;
                    preparation.rejected |= result.outcome() != CommandOutcome.ACCEPTED;
                });
            }
            return preparation;
        } catch (RuntimeException failure) {
            throw preparation.cleanupAfterFailure(failure);
        }
    }

    public MatchRuntime match() {
        return match;
    }

    @Override
    public Status poll() {
        if (cancelled) return Status.FAILED;
        if (completed) return Status.COMPLETE;
        MatchLifecycle life = match.lifecycle();
        if (life == MatchLifecycle.FAULTED
                || life == MatchLifecycle.DISPOSED
                || life == MatchLifecycle.ENDED
                || life == MatchLifecycle.PAUSED) {
            return Status.FAILED;
        }
        for (MatchLobby.Seat seat : humans) {
            ServerPlayer player = server.server().getPlayerList().getPlayer(Objects.requireNonNull(seat.occupant()));
            if (player == null || player.hasDisconnected()) return Status.FAILED;
        }
        if (life != MatchLifecycle.RUNNING) return Status.PREPARING;
        MatchCore core = Objects.requireNonNull(match.core());
        if (core.queries().tick() == 0) return Status.PREPARING;
        if (rejected
                || replies != plan.commands().size()
                || core.queries().templates().hash() != plan.templateSetHash()
                || !core.queries().config().starts().equals(starts)
                || !terrainHash.equals(match.importedHash())
                || !core.queries().diplomacy().teamsLocked()) return Status.FAILED;
        if (!admitted) {
            if (!match.roster().isEmpty()) return Status.FAILED;
            for (MatchLobby.Seat seat : humans) {
                if (server.matches().seatOf(Objects.requireNonNull(seat.occupant())) != null) return Status.FAILED;
            }
            try {
                for (MatchLobby.Seat seat : humans) {
                    server.matches().join(match, Objects.requireNonNull(seat.occupant()), seat.player());
                }
                for (MatchLobby.Seat seat : humans) {
                    UUID member = Objects.requireNonNull(seat.occupant());
                    ServerPlayer player = Objects.requireNonNull(
                            server.server().getPlayerList().getPlayer(member));
                    server.commanders().enter(player, match);
                    server.net().joined(match, player, seat.player());
                }
                admitted = true;
                admittedAt = server.server().getTickCount();
            } catch (RuntimeException failure) {
                throw cleanupAfterFailure(failure);
            }
        }
        // The existing replication cadence queues all initial owner state within one summary period.
        if (server.server().getTickCount() - admittedAt < Replication.SUMMARY_EVERY) return Status.PREPARING;
        var data = match.data();
        if (data != null) data.completeLobbyLaunch();
        completed = true;
        return Status.COMPLETE;
    }

    private RuntimeException cleanupAfterFailure(RuntimeException failure) {
        try {
            cancel();
            return failure;
        } catch (RuntimeException cleanup) {
            failure.addSuppressed(cleanup);
            try {
                match.fault(cleanup, "lobby preparation cleanup");
            } catch (RuntimeException markingFault) {
                cleanup.addSuppressed(markingFault);
            }
            return new LobbyService.IncompleteCleanupException(failure);
        }
    }

    @Override
    public void cancel() {
        if (cancelled) return;
        List<UUID> seated = match.roster().members();
        for (UUID member : seated) {
            server.net().discardQueued(member);
            server.replication().forget(member);
        }
        if (match.lifecycle() != MatchLifecycle.DISPOSED) server.matches().dispose(match.id());
        for (UUID member : seated) match.roster().unseat(member);
        cancelled = true;
    }
}
