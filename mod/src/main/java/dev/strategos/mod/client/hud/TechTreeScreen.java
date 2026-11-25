package dev.strategos.mod.client.hud;

import dev.strategos.core.client.hud.HudRect;
import dev.strategos.core.client.hud.TechTreeModel;
import dev.strategos.core.client.hud.TechTreeModel.Cell;
import dev.strategos.core.client.hud.TechTreeModel.State;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiPredicate;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The technology tree (TECH-05): the phases as columns, the lines of technologies as rows, each technology a box with
 * its icon and name, framed in the colour of its state; hovering one tells its state, cost, time, researcher and
 * effects. Read-only: research stays on the buildings' cards. Esc or the key that opened it closes it, and the RTS
 * screen comes back by itself ({@code RtsMode}'s tick). A {@link TechTreeModel} drawn; client thread only.
 */
public final class TechTreeScreen extends Screen {
    private static final int MARGIN = 16;
    private static final int LABEL = 90;
    private static final int HEADER = 22;
    private static final int ROW = 22;
    private static final int GAP = 3;

    private final TechTreeModel tree;
    private final BiPredicate<Integer, Integer> closeKey;
    private final HudPaint paint = new HudPaint();

    public TechTreeScreen(TechTreeModel tree, BiPredicate<Integer, Integer> closeKey) {
        super(Component.translatable("strategos.techtree.title"));
        this.tree = tree;
        this.closeKey = closeKey;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (closeKey.test(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** The first row's top. */
    private int top() {
        return MARGIN + font.lineHeight + 6 + HEADER;
    }

    private int columnWidth() {
        return Math.max(
                60, (width - 2 * MARGIN - LABEL) / Math.max(1, tree.columns().size()));
    }

    /** The box of technology {@code k} of {@code n} in the cell of row {@code row}, column {@code column}. */
    private HudRect box(int row, int column, int k, int n) {
        int w = (columnWidth() - GAP) / n;
        return new HudRect(MARGIN + LABEL + column * columnWidth() + k * w, top() + row * ROW, w - GAP, ROW - GAP);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(g);
        Font f = font;
        g.drawString(f, title, MARGIN, MARGIN, paint.theme.text(), false);
        int y = MARGIN + f.lineHeight + 6;
        for (int c = 0; c < tree.columns().size(); c++) {
            TechTreeModel.Column col = tree.columns().get(c);
            HudRect r = new HudRect(MARGIN + LABEL + c * columnWidth(), y, columnWidth() - GAP, HEADER - GAP);
            paint.panel(g, r);
            g.fill(r.x(), r.bottom() - 2, r.right(), r.bottom(), colour(col.state()));
            g.drawString(
                    f,
                    f.plainSubstrByWidth(Component.translatable(col.nameKey()).getString(), r.width() - 8),
                    r.x() + 4,
                    r.y() + 4,
                    paint.theme.text(),
                    false);
        }
        @Nullable Cell hovered = null;
        for (int row = 0; row < tree.rows().size(); row++) {
            TechTreeModel.Row line = tree.rows().get(row);
            g.drawString(f, label(line.line()), MARGIN, top() + row * ROW + 5, paint.theme.dimText(), false);
            for (int c = 0; c < line.cells().size(); c++) {
                List<Cell> cell = line.cells().get(c);
                for (int k = 0; k < cell.size(); k++) {
                    HudRect b = box(row, c, k, cell.size());
                    tech(g, f, b, cell.get(k));
                    if (b.contains(mouseX, mouseY)) {
                        hovered = cell.get(k);
                    }
                }
            }
        }
        if (hovered != null) {
            g.renderComponentTooltip(f, tooltip(hovered), mouseX, mouseY);
        }
    }

    private void tech(GuiGraphics g, Font f, HudRect b, Cell cell) {
        boolean dim = cell.state() == State.OUTSIDE_ROSTER || cell.state() == State.LOCKED;
        g.fill(b.x(), b.y(), b.right(), b.bottom(), paint.theme.panel());
        g.renderOutline(b.x(), b.y(), b.width(), b.height(), colour(cell.state()));
        paint.item(g, cell.tech().icon(), new HudRect(b.x() + 1, b.y() + 1, 18, b.height() - 2));
        String name = Component.translatable(cell.tech().name()).getString();
        g.drawString(
                f,
                f.plainSubstrByWidth(name, b.width() - 22),
                b.x() + 20,
                b.y() + (b.height() - f.lineHeight) / 2 + 1,
                dim ? paint.theme.dimText() : paint.theme.text(),
                false);
    }

    private static List<Component> tooltip(Cell cell) {
        List<Component> out = new ArrayList<>();
        out.add(Component.translatable(cell.tech().name()));
        String state = "strategos.techtree.state." + cell.state().name().toLowerCase(Locale.ROOT);
        out.add(
                cell.lockedBy() == null
                        ? Component.translatable(state)
                        : Component.translatable(state, Component.translatable("strategos.tech." + cell.lockedBy())));
        StringBuilder cost = new StringBuilder();
        cell.cost().forEach((r, v) -> {
            if (v > 0) {
                cost.append(cost.isEmpty() ? "" : ", ")
                        .append(Math.round(v))
                        .append(' ')
                        .append(r);
            }
        });
        out.add(Component.translatable("strategos.techtree.cost", cost.isEmpty() ? "-" : cost.toString()));
        out.add(Component.translatable("strategos.techtree.time", Math.round(cell.time())));
        if (cell.researcher() != null) {
            out.add(Component.translatable("strategos.techtree.researcher", Component.translatable(cell.researcher())));
        }
        for (String effect : cell.effects()) {
            out.add(Component.literal(effect));
        }
        return out;
    }

    /** A line's name for its row: {@code gather_wood} as "Gather wood". */
    private static String label(String line) {
        String text = line.replace('_', ' ');
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static int colour(State state) {
        return 0xFF000000
                | switch (state) {
                    case RESEARCHED -> 0x40C060;
                    case RESEARCHING -> 0xFFD040;
                    case AVAILABLE -> 0xFFFFFF;
                    case NEEDS_PHASE, NEEDS_PREVIOUS, NEEDS_BUILDINGS -> 0x8090B0;
                    case LOCKED -> 0xC04040;
                    case OUTSIDE_ROSTER -> 0x505050;
                };
    }
}
