package dev.strategos.mod.client.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * The title screen's way into a skirmish (MATCH-02): Singleplayer takes the left half of its row, Skirmish the right
 * half, so no vanilla button moves down.
 */
public final class SkirmishEntry {
    private static final int GAP = 4;

    private SkirmishEntry() {}

    public static void onInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof TitleScreen title)) {
            return;
        }
        Component singleplayer = Component.translatable("menu.singleplayer");
        for (GuiEventListener listener : event.getListenersList()) {
            if (listener instanceof Button b && b.getMessage().equals(singleplayer)) {
                int half = (b.getWidth() - GAP) / 2;
                b.setWidth(half);
                event.addListener(Button.builder(
                                Component.translatable("strategos.skirmish.button"),
                                press -> Minecraft.getInstance().setScreen(new SkirmishScreen(title)))
                        .bounds(b.getX() + half + GAP, b.getY(), half, b.getHeight())
                        .build());
                return;
            }
        }
    }
}
