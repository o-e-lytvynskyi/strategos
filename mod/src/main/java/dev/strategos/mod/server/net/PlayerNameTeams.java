package dev.strategos.mod.server.net;

import dev.strategos.core.protocol.MatchPresentation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/** Recipient-local human name colours: no server scoreboard mutations and no unit membership. */
final class PlayerNameTeams {
    private record Published(ServerPlayer connection, MatchPresentation view, List<PlayerTeam> teams) {}

    private final Map<UUID, Published> published = new HashMap<>();
    private final MinecraftServer server;
    private final NetGateway net;

    PlayerNameTeams(MinecraftServer server, NetGateway net) {
        this.server = server;
        this.net = net;
    }

    void update(ServerPlayer recipient, MatchPresentation view) {
        Published previous = published.get(recipient.getUUID());
        if (previous != null
                && previous.connection().equals(recipient)
                && previous.view().sameView(view)) return;
        forget(recipient.getUUID());
        var isolated = new Scoreboard();
        var teams = new ArrayList<PlayerTeam>();
        String prefix = "stg_m" + Integer.toHexString(view.matchId()) + "_";
        for (int faction = 0; faction < view.factions().styles().size(); faction++) {
            var names = new ArrayList<String>();
            for (var participant : view.participants())
                if (participant.connected() && participant.faction() == faction) names.add(participant.name());
            if (names.isEmpty()) continue;
            PlayerTeam team = isolated.addPlayerTeam(prefix + Integer.toHexString(faction));
            int selected = view.factions().style(faction).colour();
            team.setColor(
                    selected < 0
                            ? legacy(faction)
                            : nearest(DyeColor.byId(selected).getTextureDiffuseColor()));
            for (String name : names) isolated.addPlayerToTeam(name, team);
            net.sendPlayerPacket(recipient, ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true));
            teams.add(team);
        }
        published.put(recipient.getUUID(), new Published(recipient, view, List.copyOf(teams)));
    }

    void forget(UUID player) {
        Published previous = published.remove(player);
        if (previous == null) return;
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online == null || !previous.connection().equals(online) || online.hasDisconnected()) return;
        for (PlayerTeam team : previous.teams())
            net.sendPlayerPacket(online, ClientboundSetPlayerTeamPacket.createRemovePacket(team));
        // Only original name membership is restored; never enumerate another team's members.
        for (var participant : previous.view().participants()) {
            if (!participant.connected()) continue;
            PlayerTeam original = server.getScoreboard().getPlayersTeam(participant.name());
            if (original != null)
                net.sendPlayerPacket(
                        online,
                        ClientboundSetPlayerTeamPacket.createPlayerPacket(
                                original, participant.name(), ClientboundSetPlayerTeamPacket.Action.ADD));
        }
    }

    void retain(java.util.Set<UUID> recipients) {
        for (UUID player : new ArrayList<>(published.keySet())) if (!recipients.contains(player)) forget(player);
    }

    private static ChatFormatting legacy(int faction) {
        return switch (faction) {
            case 0 -> ChatFormatting.BLUE;
            case 1 -> ChatFormatting.RED;
            case 2 -> ChatFormatting.GREEN;
            case 3 -> ChatFormatting.YELLOW;
            case 4 -> ChatFormatting.LIGHT_PURPLE;
            case 5 -> ChatFormatting.GOLD;
            case 6 -> ChatFormatting.AQUA;
            default -> ChatFormatting.WHITE;
        };
    }

    /** Vanilla teams support sixteen text colours; choose the closest RGB to the selected dye. */
    private static ChatFormatting nearest(int rgb) {
        ChatFormatting best = ChatFormatting.WHITE;
        int distance = Integer.MAX_VALUE;
        for (int i = 0; i < 16; i++) {
            ChatFormatting candidate = ChatFormatting.getById(i);
            if (candidate == null) continue;
            Integer colour = candidate.getColor();
            if (colour == null) continue;
            int red = ((rgb >> 16) & 255) - ((colour >> 16) & 255);
            int green = ((rgb >> 8) & 255) - ((colour >> 8) & 255);
            int blue = (rgb & 255) - (colour & 255);
            int next = red * red + green * green + blue * blue;
            if (next < distance) {
                best = candidate;
                distance = next;
            }
        }
        return best;
    }
}
