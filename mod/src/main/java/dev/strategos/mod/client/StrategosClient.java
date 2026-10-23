package dev.strategos.mod.client;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.client.model.ClientMatchModel;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.MatchJoin;
import dev.strategos.core.protocol.MatchStateMsg;
import dev.strategos.core.sim.MatchRules;
import dev.strategos.core.sim.econ.ResourceDef;
import dev.strategos.mod.Strategos;
import dev.strategos.mod.client.audio.FxPlayer;
import dev.strategos.mod.client.debug.ClientDebugState;
import dev.strategos.mod.client.debug.DebugOverlayHost;
import dev.strategos.mod.client.hud.RtsHud;
import dev.strategos.mod.client.net.ClientPayloads;
import dev.strategos.mod.client.overlay.FarMarkers;
import dev.strategos.mod.client.overlay.HealthBarOverlay;
import dev.strategos.mod.client.rts.RtsMode;
import dev.strategos.mod.registry.ModEntityTypes;
import dev.strategos.mod.server.net.payload.PayloadArrived;
import java.util.List;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
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
    private static final RtsMode RTS = new RtsMode();
    private static final RtsHud HUD = new RtsHud(RTS);
    private static final ClientMatchModel MODEL = new ClientMatchModel();
    private static final FarMarkers FAR = new FarMarkers(MODEL);
    private static final HealthBarOverlay BARS = new HealthBarOverlay(MODEL, RTS);
    private static final FxPlayer SOUNDS = new FxPlayer(RTS);
    private static volatile @Nullable MatchJoin match;
    private static volatile List<String> resources = List.of();
    private static volatile @Nullable MatchStateMsg state;
    private static volatile TemplateSet templates = TemplateSet.builtin();

    public StrategosClient(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, StrategosClientConfig.SPEC, StrategosClientConfig.FILE);
        modBus.addListener(
                EntityRenderersEvent.RegisterRenderers.class,
                e -> e.registerEntityRenderer(ModEntityTypes.UNIT_HUMANOID.get(), UnitRenderer::new));
        NeoForge.EVENT_BUS.addListener(PayloadArrived.class, ClientPayloads::onPayload);
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> {
            match = null;
            resources = List.of();
            state = null;
            DEBUG.reset();
            MODEL.clear();
        });
        new DebugOverlayHost(modBus, DEBUG);
        RTS.register(modBus);
        HUD.register(modBus); // after RTS mode: its tick builds from the tick RTS mode just ran
        FAR.register();
        BARS.register();
    }

    /** What replication told this client of its match (NET-08); the client thread's only. */
    public static ClientMatchModel model() {
        return MODEL;
    }

    /** The match's sounds (AUD-01); the client thread's only. */
    public static FxPlayer sounds() {
        return SOUNDS;
    }

    /** The health bars (CMB-06). */
    public static HealthBarOverlay healthBars() {
        return BARS;
    }

    /** The far-unit markers (NET-08). */
    public static FarMarkers farMarkers() {
        return FAR;
    }

    /** The RTS HUD (UI-02); the client thread's only. */
    public static RtsHud hud() {
        return HUD;
    }

    /** RTS mode on this client (CAM-02, CAM-04); the client thread's only. */
    public static RtsMode rts() {
        return RTS;
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
        resources = resources(join);
    }

    /** The joined match's resources in their order, from the rules it was joined with; empty before a join. */
    public static List<String> resources() {
        return resources;
    }

    private static List<String> resources(MatchJoin join) {
        try {
            MatchRules rules = MatchRules.CODEC
                    .parse(JsonOps.INSTANCE, JsonParser.parseString(join.rules()))
                    .getOrThrow();
            return rules.economy().resources().stream().map(ResourceDef::id).toList();
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    public static @Nullable MatchStateMsg state() {
        return state;
    }

    public static void state(MatchStateMsg update) {
        state = update;
    }

    /** The server's template set as this client decoded it (DATA-03); the bundled one before the first arrives. */
    public static TemplateSet templates() {
        return templates;
    }

    public static void templates(TemplateSet set) {
        templates = set;
    }
}
