package dev.strategos.core.client.hud;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.content.ValuePath;
import dev.strategos.core.protocol.BuildingState;
import dev.strategos.core.protocol.FactionState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The command card's view-model (UI-06, 04 §13.5): up to {@value #SLOTS} buttons in a {@value #COLUMNS} ×
 * {@value #ROWS} grid of square cells inside the card's rectangle, from what is selected. An own finished building
 * shows a button for each unit its {@code Trainer/units} names, and a field reaped empty its reseed; units show the
 * build page, when a builder is among them, and stop; the build page shows each building with a blueprint, and back.
 * Each button carries its template, its cost per resource of the match in thousandths and whether the faction's stock
 * covers it. Drawing and clicks use the same cells.
 */
public record CommandCard(Page page, List<Button> buttons) {
    public static final int COLUMNS = 5;
    public static final int ROWS = 3;
    public static final int SLOTS = COLUMNS * ROWS;

    /** The smallest cell: an item icon's 16 pixels. */
    public static final int CELL_MIN = 16;

    /** The gap between cells, in GUI pixels. */
    public static final int GAP = 1;

    public static final CommandCard EMPTY = new CommandCard(Page.NONE, List.of());

    /** What the card shows. */
    public enum Page {
        NONE,
        UNITS,
        BUILD,
        BUILDING
    }

    /** What a button does. */
    public enum Kind {
        TRAIN,
        RESEED,
        BUILD_PAGE,
        BUILD,
        BACK,
        STOP
    }

    /** One button in cell {@code slot}; {@code template} is a net index, -1 for none. */
    public record Button(Kind kind, int slot, int template, List<Long> cost, boolean affordable) {
        public Button {
            cost = List.copyOf(cost);
        }
    }

    /**
     * What the card is built from: the templates, the match's resources in order, the faction's last state (null
     * before the first), the selected units' templates and whether the build page is open.
     */
    public record Input(
            TemplateSet templates,
            List<String> resources,
            @Nullable FactionState faction,
            List<Integer> unitTemplates,
            boolean buildPage) {
        public Input {
            resources = List.copyOf(resources);
            unitTemplates = List.copyOf(unitTemplates);
        }
    }

    /** The building state code of a finished building (BuildingStore.COMPLETE). */
    public static final int COMPLETE = 2;

    /** The card for {@code in}, or for own building {@code b} when one is selected. */
    public static CommandCard of(Input in, BuildingState.@Nullable Entry b) {
        if (b != null) {
            return new CommandCard(Page.BUILDING, buildingButtons(in, b));
        }
        if (in.unitTemplates().isEmpty()) {
            return EMPTY;
        }
        boolean builders = in.unitTemplates().stream()
                .anyMatch(t -> in.templates().has(t)
                        && in.templates().byIndex(t).classes().contains("Builder"));
        List<Button> out = new ArrayList<>();
        if (builders && in.buildPage()) {
            int slot = 0;
            for (int t : buildings(in.templates())) {
                if (slot == SLOTS - 1) {
                    break;
                }
                out.add(button(in, Kind.BUILD, slot++, t, cost(in, t, "Cost/resources/")));
            }
            out.add(new Button(Kind.BACK, SLOTS - 1, -1, List.of(), true));
            return new CommandCard(Page.BUILD, out);
        }
        if (builders) {
            out.add(new Button(Kind.BUILD_PAGE, 0, -1, List.of(), true));
        }
        out.add(new Button(Kind.STOP, SLOTS - 1, -1, List.of(), true));
        return new CommandCard(Page.UNITS, out);
    }

    private static List<Button> buildingButtons(Input in, BuildingState.Entry b) {
        List<Button> out = new ArrayList<>();
        if (!b.own() || b.state() != COMPLETE || !in.templates().has(b.template())) {
            return out;
        }
        int slot = 0;
        for (int t : trains(in.templates(), b.template())) {
            if (slot == SLOTS) {
                break;
            }
            out.add(button(in, Kind.TRAIN, slot++, t, cost(in, t, "Cost/resources/")));
        }
        if (b.fieldLeft() == 0 && b.fieldRipening() == 0) {
            out.add(button(
                    in, Kind.RESEED, Math.min(slot, SLOTS - 1), b.template(), cost(in, b.template(), "Field/reseed/")));
        }
        return out;
    }

    /** The templates building template {@code building} trains, by its {@code Trainer/units}, in their order there. */
    public static List<Integer> trains(TemplateSet templates, int building) {
        ValuePath path = templates.tokenPaths().byPath("Trainer/units");
        String units = path == null ? null : templates.text(building, path.id());
        List<Integer> out = new ArrayList<>();
        if (units == null || units.isBlank()) {
            return out;
        }
        for (String id : units.trim().split("\\s+", -1)) {
            for (UnitTemplate t : templates.all()) {
                if (t.id().value().equals(id)) {
                    out.add(t.netIndex());
                }
            }
        }
        return out;
    }

    /** Template {@code template}'s icon, the item its {@code Identity/icon} names, or null for none. */
    public static @Nullable String icon(TemplateSet templates, int template) {
        ValuePath path = templates.tokenPaths().byPath("Identity/icon");
        String icon = path == null || !templates.has(template) ? null : templates.text(template, path.id());
        return icon == null || icon.isBlank() ? null : icon.trim();
    }

    /** The building templates with a blueprint a worker can place, by net index: deposits are spawned, not built. */
    public static List<Integer> buildings(TemplateSet templates) {
        List<Integer> out = new ArrayList<>();
        for (UnitTemplate t : templates.all()) {
            if (t.isBuilding()
                    && templates.blueprint(t.netIndex()) != null
                    && !t.classes().contains("Deposit")) {
                out.add(t.netIndex());
            }
        }
        out.sort(Comparator.naturalOrder());
        return out;
    }

    /** Template {@code template}'s amounts under {@code prefix} per resource of the match, in thousandths. */
    static List<Long> cost(Input in, int template, String prefix) {
        List<Long> out = new ArrayList<>(in.resources().size());
        for (String r : in.resources()) {
            ValuePath path = in.templates().valuePaths().byPath(prefix + r);
            double v = path == null ? Double.NaN : in.templates().value(template, path.id());
            out.add(Double.isFinite(v) && v > 0 ? Math.round(v * 1000) : 0L);
        }
        return out;
    }

    private static Button button(Input in, Kind kind, int slot, int template, List<Long> cost) {
        FactionState f = in.faction();
        boolean affordable = true;
        for (int r = 0; r < cost.size(); r++) {
            long have = f == null || r >= f.stock().size() ? 0 : f.stock().get(r);
            affordable &= cost.get(r) <= have;
        }
        return new Button(kind, slot, template, cost, affordable);
    }

    /** The side of a square cell in card {@code card}: as large as the grid allows, at least {@value #CELL_MIN}. */
    public static int cellSize(HudRect card, int padding) {
        int byWidth = (card.width() - 2 * padding - (COLUMNS - 1) * GAP) / COLUMNS;
        int byHeight = (card.height() - 2 * padding - (ROWS - 1) * GAP) / ROWS;
        return Math.max(CELL_MIN, Math.min(byWidth, byHeight));
    }

    /** Cell {@code slot}'s rectangle in card {@code card}: the grid centred in it. */
    public static HudRect cell(HudRect card, int padding, int slot) {
        int size = cellSize(card, padding);
        int gridW = COLUMNS * size + (COLUMNS - 1) * GAP;
        int gridH = ROWS * size + (ROWS - 1) * GAP;
        int x0 = card.x() + (card.width() - gridW) / 2;
        int y0 = card.y() + (card.height() - gridH) / 2;
        int col = slot % COLUMNS;
        int row = slot / COLUMNS;
        return new HudRect(x0 + col * (size + GAP), y0 + row * (size + GAP), size, size);
    }

    /** The button under GUI point ({@code x}, {@code y}) in card {@code card}, or null. */
    public @Nullable Button at(HudRect card, int padding, double x, double y) {
        for (Button b : buttons) {
            if (cell(card, padding, b.slot()).contains(x, y)) {
                return b;
            }
        }
        return null;
    }
}
