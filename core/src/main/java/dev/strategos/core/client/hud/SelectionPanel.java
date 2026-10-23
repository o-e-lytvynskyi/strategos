package dev.strategos.core.client.hud;

import dev.strategos.core.protocol.BuildingState;
import dev.strategos.core.protocol.UnitDetail;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The selection panel's view-model (UI-06): several units as an icon per type with its count, the largest group
 * first; one unit's details (template, faction, hit points and their most, state, and the load it carries) from
 * {@code unit_detail}; or one building's entry from {@code building_state}, its queue as a row of icons, the head's
 * progress over it, that a click cancels. Its layout puts a 16-pixel icon at the top left with two lines of text beside
 * it and the queue's row, or a third line, at the bottom; drawing and clicks use the same cells.
 */
public record SelectionPanel(
        Kind kind, SelectionSummary summary, @Nullable Unit unit, BuildingState.@Nullable Entry building) {
    /** An item icon's side, in GUI pixels. */
    public static final int ICON = 16;

    /** The gap between icons. */
    public static final int GAP = 2;

    /** The rows of text beside the icon and below it, and the height of one. */
    public static final int LINE = 10;

    public static final SelectionPanel EMPTY = new SelectionPanel(Kind.EMPTY, SelectionSummary.EMPTY, null, null);

    /** What the panel shows. */
    public enum Kind {
        EMPTY,
        TYPES,
        UNIT,
        BUILDING
    }

    /** One unit's details; {@code carried} is the resource's index, -1 for none, {@code load} in thousandths. */
    public record Unit(
            int handle,
            int template,
            int faction,
            float health,
            float maxHealth,
            int state,
            int carried,
            long load,
            boolean own) {
        /** An own unit's, from its {@code unit_detail}. */
        public static Unit of(UnitDetail.Unit d, int faction) {
            return new Unit(
                    d.handle(),
                    d.template(),
                    faction,
                    d.health(),
                    d.maxHealth(),
                    d.state(),
                    d.carried(),
                    d.load(),
                    true);
        }
    }

    /**
     * From what is selected: a building shown alone; else one unit's details when one is selected or inspected and
     * known; else the types.
     */
    public static SelectionPanel of(
            SelectionSummary summary, @Nullable Unit unit, BuildingState.@Nullable Entry building) {
        if (building != null) {
            return new SelectionPanel(Kind.BUILDING, summary, null, building);
        }
        if (unit != null && summary.total() <= 1) {
            return new SelectionPanel(Kind.UNIT, summary, unit, null);
        }
        if (summary.total() == 0) {
            return EMPTY;
        }
        return new SelectionPanel(Kind.TYPES, summary, null, null);
    }

    /** The icon at the top left of panel {@code panel}. */
    public static HudRect icon(HudRect panel, int padding) {
        return new HudRect(panel.x() + padding, panel.y() + padding, ICON, ICON);
    }

    /** Where text line {@code n} starts, x then y: lines 0 and 1 beside the icon, line 2 on below it. */
    public static int[] line(HudRect panel, int padding, int n) {
        int x = n < 2 ? panel.x() + padding + ICON + 4 : panel.x() + padding;
        int y = panel.y() + padding + n * LINE + (n < 2 ? 0 : 1);
        return new int[] {x, y};
    }

    /** Queue entry {@code k}'s cell, along the bottom. */
    public static HudRect queueCell(HudRect panel, int padding, int k) {
        return new HudRect(panel.x() + padding + k * (ICON + GAP), panel.bottom() - padding - ICON, ICON, ICON);
    }

    /** How many type icons fit the panel. */
    public static int typeCapacity(HudRect panel, int padding) {
        return columns(panel, padding) * Math.max(0, (panel.height() - 2 * padding + GAP) / (ICON + GAP));
    }

    /** Type {@code k}'s icon cell, in rows from the top left. */
    public static HudRect typeCell(HudRect panel, int padding, int k) {
        int columns = Math.max(1, columns(panel, padding));
        return new HudRect(
                panel.x() + padding + (k % columns) * (ICON + GAP),
                panel.y() + padding + (k / columns) * (ICON + GAP),
                ICON,
                ICON);
    }

    /** The queue entry under GUI point ({@code x}, {@code y}), or -1. */
    public int queueAt(HudRect panel, int padding, double x, double y) {
        BuildingState.Entry b = building;
        if (b == null) {
            return -1;
        }
        List<Integer> queue = b.queue();
        for (int k = 0; k < queue.size(); k++) {
            if (queueCell(panel, padding, k).contains(x, y)) {
                return k;
            }
        }
        return -1;
    }

    private static int columns(HudRect panel, int padding) {
        return Math.max(0, (panel.width() - 2 * padding + GAP) / (ICON + GAP));
    }
}
