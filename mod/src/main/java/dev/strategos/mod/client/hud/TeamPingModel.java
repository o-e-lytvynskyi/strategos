package dev.strategos.mod.client.hud;

import dev.strategos.core.protocol.MatchJoin;
import dev.strategos.core.protocol.PlayerAuthority;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.mod.server.net.payload.TeamPingPayload;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** One temporary allied marker per sending player, scoped to the current private match view. */
public final class TeamPingModel {
    public static final int COLOUR = 0xFF53E8FF;
    private static final int LIFETIME = 100;

    private record Marker(TeamPingPayload.Ping ping, long expiresAt) {}

    private final Map<PlayerSlot, Marker> markers = new LinkedHashMap<>();
    private @Nullable MatchJoin join;
    private long epoch;
    private int allies;
    private long tick;

    public void join(long epoch, MatchJoin join) {
        clear();
        this.epoch = epoch;
        this.join = join;
        allies = join.authority().alliedMask();
    }

    public void authority(PlayerAuthority authority) {
        if (allies != authority.alliedMask()) {
            allies = authority.alliedMask();
            markers.clear();
        }
    }

    public void clear() {
        markers.clear();
        join = null;
        epoch = 0;
        allies = 0;
    }

    public void tick() {
        tick++;
        markers.values().removeIf(marker -> marker.expiresAt() <= tick);
    }

    public boolean accept(TeamPingPayload frame) {
        var current = join;
        var ping = frame.ping();
        if (current == null
                || current.slot().isAdmin()
                || epoch != frame.epoch()
                || current.matchId() != ping.matchId()
                || ping.x() >= current.sizeX()
                || ping.z() >= current.sizeZ()) return false;
        markers.put(ping.player(), new Marker(ping, tick + LIFETIME));
        return true;
    }

    public List<TeamPingPayload.Ping> active() {
        if (markers.isEmpty()) return List.of();
        return markers.values().stream().map(Marker::ping).toList();
    }
}
