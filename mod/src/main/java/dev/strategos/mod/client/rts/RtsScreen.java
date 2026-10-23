package dev.strategos.mod.client.rts;

import dev.strategos.core.client.input.Action;
import dev.strategos.core.client.input.ActionContext;
import dev.strategos.core.client.input.InputState;
import dev.strategos.core.client.input.KeyCode;
import dev.strategos.core.client.input.ScreenKeys;
import dev.strategos.core.client.pick.Picking;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The RTS screen (CAM-04, ADR-0017, 04 §13.3): non-pausing and full-window, it owns every input, so vanilla's attack,
 * use, hotbar, inventory, drop, spectator menu and scroll-to-fly are inert by construction ({@code Minecraft#tick} skips
 * {@code handleKeybinds} while a screen is open). It forwards F1, F2, F3 and F11, opens chat and the command line on
 * their keys, turns Escape into "cancel, else pause", leaves RTS mode on the RTS toggle, and hands the rest to the
 * action layer; it draws nothing behind the world, only the selection box.
 */
public final class RtsScreen extends Screen {
    private static final int BOX = 0x80FFFFFF;

    private final RtsMode mode;

    RtsScreen(RtsMode mode) {
        super(Component.literal("Strategos"));
        this.mode = mode;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        InputState in = mode.input();
        if (in == null) {
            return;
        }
        double[] from = in.dragFrom();
        if (from.length == 2) {
            int x0 = (int) Math.min(from[0], mouseX);
            int y0 = (int) Math.min(from[1], mouseY);
            int x1 = (int) Math.max(from[0], mouseX);
            int y1 = (int) Math.max(from[1], mouseY);
            graphics.renderOutline(x0, y0, Math.max(1, x1 - x0), Math.max(1, y1 - y0), BOX);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        Minecraft mc = Minecraft.getInstance();
        InputState in = mode.input();
        if (in == null) {
            return false;
        }
        switch (ScreenKeys.route(keyCode, in.context())) {
            case HIDE_GUI -> mc.options.hideGui = !mc.options.hideGui;
            case SCREENSHOT ->
                Screenshot.grab(
                        mc.gameDirectory,
                        mc.getMainRenderTarget(),
                        message -> mc.execute(() -> mc.gui.getChat().addMessage(message)));
            case DEBUG_SCREEN -> mc.getDebugOverlay().toggleOverlay();
            case FULLSCREEN -> {
                mc.getWindow().toggleFullScreen();
                mc.options.fullscreen().set(mc.getWindow().isFullscreen());
            }
            case CANCEL -> in.context(ActionContext.RTS);
            case PAUSE -> mc.pauseGame(false);
            case ACTIONS -> {
                return actionKey(mc, in, keyCode, scanCode, modifiers);
            }
        }
        return true;
    }

    private boolean actionKey(Minecraft mc, InputState in, int keyCode, int scanCode, int modifiers) {
        if (mc.options.keyChat.matches(keyCode, scanCode)) {
            mc.setScreen(new ChatScreen(""));
            return true;
        }
        if (mc.options.keyCommand.matches(keyCode, scanCode)) {
            mc.setScreen(new ChatScreen("/"));
            return true;
        }
        if (mode.keys().mapping(Action.TOGGLE_RTS).matches(keyCode, scanCode)) {
            mode.leave();
            return true;
        }
        for (InputState.Event e : in.keyDown(KeyCode.key(keyCode), modifiers, Util.getMillis())) {
            mode.fired(e.action());
        }
        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        InputState in = mode.input();
        if (in != null) {
            in.keyUp(KeyCode.key(keyCode), modifiers);
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        InputState in = mode.input();
        if (in != null) {
            handle(in.mouseDown(button, mouseX, mouseY, modifiers(), Util.getMillis()));
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        InputState in = mode.input();
        if (in != null) {
            handle(in.mouseUp(button, mouseX, mouseY, modifiers(), Util.getMillis()));
        }
        return true;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        InputState in = mode.input();
        if (in != null) {
            in.mouseMove(mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        mouseMoved(mouseX, mouseY);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        mode.zoom(scrollY);
        return true;
    }

    /** Selection and orders come with UI-03 and UI-05; until then a click picks, for the hover and for scenarios. */
    private void handle(java.util.List<InputState.Event> events) {
        for (InputState.Event e : events) {
            mode.fired(e.action());
            if (e.action() == Action.SELECT || e.action() == Action.ORDER_CONTEXT) {
                mode.pick(
                        Picking.toWindow(e.x(), width, windowWidth()), Picking.toWindow(e.y(), height, windowHeight()));
            }
        }
    }

    private static double windowWidth() {
        return Minecraft.getInstance().getWindow().getScreenWidth();
    }

    private static double windowHeight() {
        return Minecraft.getInstance().getWindow().getScreenHeight();
    }

    /** The modifiers held now, as GLFW's bits. */
    private static int modifiers() {
        int mods = 0;
        if (hasShiftDown()) {
            mods |= dev.strategos.core.client.input.Modifiers.SHIFT;
        }
        if (hasControlDown()) {
            mods |= dev.strategos.core.client.input.Modifiers.CTRL;
        }
        if (hasAltDown()) {
            mods |= dev.strategos.core.client.input.Modifiers.ALT;
        }
        return mods;
    }
}
