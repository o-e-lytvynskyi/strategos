package dev.strategos.mod.client.menu;

import dev.strategos.mod.client.StrategosClient;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Keeps vanilla menu positions: options and the connected server's lobby share one row. */
public final class LobbyEntry {
    private LobbyEntry() {}

    public static void onInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof PauseScreen parent)
                || !ClientLobby.available()
                || StrategosClient.match() != null) return;
        for (var listener : event.getListenersList()) {
            if (listener instanceof Button button
                    && button.getMessage().equals(Component.translatable("menu.options"))) {
                int half = (button.getWidth() - 4) / 2;
                button.setWidth(half);
                event.addListener(Button.builder(
                                Component.translatable("strategos.lobby.button"), press -> ClientLobby.open(parent))
                        .bounds(button.getX() + half + 4, button.getY(), half, button.getHeight())
                        .build());
                return;
            }
        }
    }
}
