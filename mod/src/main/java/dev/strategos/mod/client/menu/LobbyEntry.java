package dev.strategos.mod.client.menu;

import dev.strategos.mod.client.StrategosClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Keeps vanilla menu positions: options and the connected server's lobby or named control sharing share one row. */
public final class LobbyEntry {
    private LobbyEntry() {}

    public static void onInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof PauseScreen parent)) return;
        var joined = StrategosClient.match();
        var state = StrategosClient.state();
        boolean sharing = joined != null
                && !joined.slot().isAdmin()
                && state != null
                && state.matchId() == joined.matchId()
                && (state.lifecycle() == dev.strategos.core.protocol.MatchStateMsg.Lifecycle.RUNNING
                        || state.lifecycle() == dev.strategos.core.protocol.MatchStateMsg.Lifecycle.PAUSED);
        if (!sharing && (!ClientLobby.available() || joined != null)) return;
        for (var listener : event.getListenersList()) {
            if (listener instanceof Button button
                    && button.getMessage().equals(Component.translatable("menu.options"))) {
                int half = (button.getWidth() - 4) / 2;
                button.setWidth(half);
                event.addListener(Button.builder(
                                Component.translatable(
                                        sharing ? "strategos.control_sharing.title" : "strategos.lobby.button"),
                                press -> {
                                    if (sharing) Minecraft.getInstance().setScreen(new ControlSharingScreen(parent));
                                    else ClientLobby.open(parent);
                                })
                        .bounds(button.getX() + half + 4, button.getY(), half, button.getHeight())
                        .build());
                return;
            }
        }
    }
}
