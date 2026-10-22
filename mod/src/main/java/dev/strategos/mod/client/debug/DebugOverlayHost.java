package dev.strategos.mod.client.debug;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.strategos.core.protocol.DebugLayer;
import dev.strategos.core.protocol.DebugSubscribe;
import dev.strategos.mod.client.net.ClientPayloads;
import dev.strategos.mod.platform.Ids;
import dev.strategos.mod.server.net.payload.DebugSubscribePayload;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The client's host for developer overlays (PATH-10, moved here from QA-10's plan): a key mapping, F8 by default, and
 * {@code /strategos-client overlay} turn it on, which subscribes to the server's {@code debug_feed}, and off, which
 * leaves it; {@code /strategos-client overlay <layer>} draws a layer or stops drawing it. Each frame it gives its world
 * overlays their pass in {@code RenderLevelStageEvent} at {@code AFTER_PARTICLES} (04 §13.6), camera-relative, and its
 * text panel is a GUI layer. M1 hosts one world overlay, {@link MovementOverlay}; later ones join {@link #overlays}.
 */
public final class DebugOverlayHost {
    public static final String KEY = "key.strategos.overlay";
    public static final String CATEGORY = "key.categories.strategos";
    public static final String COMMAND = "strategos-client";
    public static final ResourceLocation PANEL = Ids.id("debug_panel");

    /** A world overlay: draws from the feed's data into the frame's buffers, relative to the camera. */
    public interface WorldOverlay {
        void render(PoseStack pose, MultiBufferSource.BufferSource buffers, Vec3 camera);
    }

    private final ClientDebugState state;
    private final KeyMapping key = new KeyMapping(
            KEY, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, InputConstants.KEY_F8, CATEGORY);
    private final List<WorldOverlay> overlays;

    public DebugOverlayHost(IEventBus modBus, ClientDebugState state) {
        this.state = state;
        this.overlays = List.of(new MovementOverlay(state));
        modBus.addListener(RegisterKeyMappingsEvent.class, e -> e.register(key));
        modBus.addListener(RegisterGuiLayersEvent.class, e -> e.registerAboveAll(PANEL, new OverlayPanel(state)));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, this::onTick);
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.class, this::onRender);
        NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent.class, this::onCommands);
    }

    private void onTick(ClientTickEvent.Post event) {
        while (key.consumeClick()) {
            toggle();
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (state.enabled() && player != null) {
            state.prune(player.getX(), player.getZ());
        }
    }

    private void onRender(RenderLevelStageEvent event) {
        if (!state.enabled() || !event.getStage().equals(RenderLevelStageEvent.Stage.AFTER_PARTICLES)) {
            return;
        }
        MultiBufferSource.BufferSource buffers =
                Minecraft.getInstance().renderBuffers().bufferSource();
        Vec3 camera = event.getCamera().getPosition();
        for (WorldOverlay overlay : overlays) {
            overlay.render(event.getPoseStack(), buffers, camera);
        }
    }

    private void onCommands(RegisterClientCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> overlay = Commands.literal("overlay")
                .executes(ctx -> {
                    toggle();
                    return 1;
                });
        for (DebugLayer layer : DebugLayer.values()) {
            overlay.then(Commands.literal(layer.id()).executes(ctx -> {
                boolean drawn = state.toggle(layer);
                tell("Strategos overlay: " + layer.id() + (drawn ? " drawn" : " hidden"));
                return 1;
            }));
        }
        event.getDispatcher().register(Commands.literal(COMMAND).then(overlay));
    }

    /** The overlay on, subscribed to every layer around the player, or off, the subscription left. */
    private void toggle() {
        if (Minecraft.getInstance().player == null) {
            return;
        }
        boolean on = !state.enabled();
        DebugSubscribe request = on ? new DebugSubscribe(DebugLayer.ALL, state.radius()) : DebugSubscribe.NONE;
        if (!ClientPayloads.toServer(new DebugSubscribePayload(request)) && on) {
            tell("Strategos overlay: this server offers no debug feed");
            return;
        }
        state.enable(on);
        tell(on ? "Strategos overlay on (operators only)" : "Strategos overlay off");
    }

    private static void tell(String text) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.literal(text), false);
        }
    }
}
