package dev.strategos.mod.client;

import dev.strategos.core.protocol.MatchJoin;
import dev.strategos.core.protocol.MatchStateMsg;
import dev.strategos.mod.Strategos;
import dev.strategos.mod.client.debug.ClientDebugState;
import dev.strategos.mod.client.debug.DebugOverlayHost;
import dev.strategos.mod.client.net.ClientPayloads;
import dev.strategos.mod.registry.ModEntityTypes;
import dev.strategos.mod.server.net.payload.PayloadArrived;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;

/**
 * The client entry, loaded only on a client, and the client's holder (B8): the match this player sits in and its last
 * state, as the server last told them, and the developer overlay's state; all clear when the player leaves the server.
 */
@Mod(value = Strategos.MOD_ID, dist = Dist.CLIENT)
public final class StrategosClient {
    private static final ClientDebugState DEBUG = new ClientDebugState();
    private static volatile @Nullable MatchJoin match;
    private static volatile @Nullable MatchStateMsg state;

    public StrategosClient(IEventBus modBus) {
        modBus.addListener(
                EntityRenderersEvent.RegisterRenderers.class,
                e -> e.registerEntityRenderer(ModEntityTypes.UNIT_HUMANOID.get(), UnitRenderer::new));
        NeoForge.EVENT_BUS.addListener(PayloadArrived.class, ClientPayloads::onPayload);
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> {
            match = null;
            state = null;
            DEBUG.reset();
        });
        new DebugOverlayHost(modBus, DEBUG);
    }

    /** The developer overlay's state (PATH-10); the client thread's only. */
    public static ClientDebugState debug() {
        return DEBUG;
    }

    public static @Nullable MatchJoin match() {
        return match;
    }

    public static void match(MatchJoin join) {
        match = join;
    }

    public static @Nullable MatchStateMsg state() {
        return state;
    }

    public static void state(MatchStateMsg update) {
        state = update;
    }
}
