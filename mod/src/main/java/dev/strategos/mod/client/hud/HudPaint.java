package dev.strategos.mod.client.hud;

import dev.strategos.core.client.hud.HudRect;
import dev.strategos.core.client.hud.HudTheme;
import dev.strategos.core.client.hud.SelectionPanel;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * What the RTS HUD's parts draw with: the theme's panels, items at full and half size, and lines of text fitted to a
 * rectangle. Split out of {@link RtsHud} in M4 T1. Client thread only.
 */
final class HudPaint {
    final HudTheme theme = HudTheme.DEFAULT;
    private final Map<String, ItemStack> icons = new HashMap<>();

    /** An item at half size, 8 pixels, its corner at ({@code x}, {@code y}). */
    void small(GuiGraphics g, String id, int x, int y) {
        ItemStack stack = stack(id);
        if (stack.isEmpty()) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.5f, 0.5f, 1);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
    }

    void item(GuiGraphics g, String id, HudRect cell) {
        ItemStack stack = stack(id);
        if (stack.isEmpty()) {
            return;
        }
        int scale = Math.max(1, (Math.min(cell.width(), cell.height()) - 2) / 16); // whole multiples stay crisp
        int size = 16 * scale;
        g.pose().pushPose();
        g.pose().translate(cell.x() + (cell.width() - size) / 2.0, cell.y() + (cell.height() - size) / 2.0, 0);
        g.pose().scale(scale, scale, 1);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
    }

    ItemStack stack(String id) {
        return icons.computeIfAbsent(id, k -> {
            ResourceLocation rl = ResourceLocation.tryParse(k);
            return rl == null ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.get(rl));
        });
    }

    void text(GuiGraphics g, Font font, HudRect r, int line, String text, int colour) {
        int[] at = SelectionPanel.line(r, theme.padding(), line);
        String fitted = font.plainSubstrByWidth(text, r.right() - theme.padding() - at[0]);
        g.drawString(font, fitted, at[0], at[1], colour, false);
    }

    void panel(GuiGraphics g, HudRect r) {
        g.fill(r.x(), r.y(), r.right(), r.bottom(), theme.panel());
        g.renderOutline(r.x(), r.y(), r.width(), r.height(), theme.border());
    }
}
