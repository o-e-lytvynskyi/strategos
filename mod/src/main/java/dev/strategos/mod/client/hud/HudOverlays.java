package dev.strategos.mod.client.hud;

import dev.strategos.core.client.HintBook;
import dev.strategos.core.client.hud.ControlsOverlay;
import dev.strategos.core.client.hud.HudRect;
import dev.strategos.core.client.hud.HudTheme;
import dev.strategos.core.client.input.DefaultBindings;
import dev.strategos.core.protocol.FactionState;
import dev.strategos.mod.client.StrategosClientConfig;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The HUD's two teaching overlays (UI-14): the hint line across the top, dismissed for good by a click, and the
 * controls sheet on its key. Split out of {@link RtsHud} in M6 T1. Client thread only.
 */
final class HudOverlays {
    private final HudPaint paint;
    private final HudTheme theme;
    private @Nullable HintBook hints;
    private HintBook.@Nullable Hint hint;
    private boolean controlsShown;

    HudOverlays(HudPaint paint) {
        this.paint = paint;
        this.theme = paint.theme;
    }

    /** The hint for this tick: the seen hints come from the client config, read once. */
    void update(boolean selecting, @Nullable FactionState faction) {
        HintBook book = hints;
        if (book == null) {
            book = new HintBook(List.copyOf(StrategosClientConfig.INSTANCE.seenHints.get()));
            hints = book;
        }
        hint = book.current(selecting, faction);
    }

    /** A click on the hint dismisses it, for good; whether the click was on it. */
    boolean click(double x, double y, boolean active) {
        HudRect hintAt = hintRect(active);
        HintBook book = hints;
        if (hintAt != null && book != null && hintAt.contains(x, y)) {
            book.dismiss();
            hint = null;
            StrategosClientConfig.INSTANCE.seenHints.set(book.seen());
            StrategosClientConfig.INSTANCE.seenHints.save();
            return true;
        }
        return false;
    }

    /** UI-14's overlay on or off (its key, K by default). */
    void toggleControls() {
        controlsShown = !controlsShown;
    }

    /** Whether the controls overlay shows, for scenarios. */
    boolean controlsShown() {
        return controlsShown;
    }

    /** The hint shown now, or null, for scenarios. */
    HintBook.@Nullable Hint hint() {
        return hint;
    }

    /** The hint, then the controls overlay when on. */
    void draw(GuiGraphics g, boolean active) {
        drawHint(g, active);
        if (controlsShown) {
            drawControls(g);
        }
    }

    private @Nullable HudRect hintRect(boolean active) {
        HintBook.Hint h = hint;
        if (h == null || !active) {
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        Component text = hintText(h);
        int w = mc.font.width(text) + 2 * theme.padding();
        return new HudRect(
                (mc.getWindow().getGuiScaledWidth() - w) / 2, 28, w, mc.font.lineHeight + 2 * theme.padding());
    }

    private static Component hintText(HintBook.Hint h) {
        return Component.translatable(h.key()).append(Component.translatable("strategos.hint.dismiss"));
    }

    private void drawHint(GuiGraphics g, boolean active) {
        HudRect r = hintRect(active);
        HintBook.Hint h = hint;
        if (r == null || h == null) {
            return;
        }
        paint.panel(g, r);
        g.drawString(
                Minecraft.getInstance().font,
                hintText(h),
                r.x() + theme.padding(),
                r.y() + theme.padding(),
                0xFFFFFFA0,
                false);
    }

    /** UI-14: the mod's key mappings on their current keys, rebinds included, in two columns. */
    private void drawControls(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        Map<String, KeyMapping> mappings = new HashMap<>();
        for (KeyMapping k : mc.options.keyMappings) {
            if (k.getCategory().equals("key.categories.strategos")) {
                mappings.put(k.getName(), k);
            }
        }
        List<ControlsOverlay.Row> keys = ControlsOverlay.rows(DefaultBindings.all(), b -> {
            KeyMapping m = mappings.get(b.mappingName());
            return m == null ? null : m.getTranslatedKeyMessage().getString();
        });
        int line = font.lineHeight + 1;
        int rows = (keys.size() + 1) / 2;
        int colW = 170;
        int w = 2 * colW + 3 * theme.padding();
        int h = (rows + 2) * line + 2 * theme.padding();
        int x = (mc.getWindow().getGuiScaledWidth() - w) / 2;
        int y = Math.max(4, (mc.getWindow().getGuiScaledHeight() - h) / 2);
        paint.panel(g, new HudRect(x, y, w, h));
        g.drawString(
                font,
                Component.translatable("strategos.controls.title"),
                x + theme.padding(),
                y + theme.padding(),
                0xFFFFD040,
                false);
        for (int k = 0; k < keys.size(); k++) {
            ControlsOverlay.Row row = keys.get(k);
            int cx = x + theme.padding() + (k / rows) * (colW + theme.padding());
            int cy = y + theme.padding() + (k % rows + 2) * line;
            String name = Component.translatable(row.nameKey()).getString();
            String bound = row.keyLabel();
            g.drawString(font, name, cx, cy, 0xFFFFFFFF, false);
            g.drawString(font, bound, cx + colW - font.width(bound) - 4, cy, 0xFFA0E0FF, false);
        }
    }
}
