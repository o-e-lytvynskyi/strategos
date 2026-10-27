package dev.strategos.core.client.hud;

import dev.strategos.core.content.CorePaths;
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
        STOP,
        /** A farmstead's auto-reseed toggle (ECO-17): {@link Button#on} says how it stands. */
        AUTO_RESEED,
        /** Cancel an own foundation or site (BLD-09). */
        CANCEL_BUILDING,
        /** Demolish an own finished building (BLD-09): {@link Button#on} once a first click asked to confirm. */
        DEMOLISH
    }

    /**
     * One button in cell {@code slot}; {@code template} is a net index, -1 for none; {@code affordable} whether it can
     * be used now, and when not, {@code reason} says why (UI-08): the missing amounts, housing, the limit, a full queue.
     */
    public record Button(
            Kind kind,
            int slot,
            int template,
            List<Long> cost,
            boolean affordable,
            boolean on,
            @Nullable String reason) {
        public Button {
            cost = List.copyOf(cost);
        }

        /** A button with no state to show. */
        public Button(Kind kind, int slot, int template, List<Long> cost, boolean affordable) {
            this(kind, slot, template, cost, affordable, false, null);
        }

        /** A button with a state to show and nothing against it. */
        public Button(Kind kind, int slot, int template, List<Long> cost, boolean affordable, boolean on) {
            this(kind, slot, template, cost, affordable, on, null);
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
            boolean buildPage,
            List<BuildingState.Entry> buildings,
            boolean demolishAsked) {
        public Input {
            resources = List.copyOf(resources);
            unitTemplates = List.copyOf(unitTemplates);
            buildings = List.copyOf(buildings);
        }

        /** An input that knows no buildings and asked nothing: the card before UI-08's reasons. */
        public Input(
                TemplateSet templates,
                List<String> resources,
                @Nullable FactionState faction,
                List<Integer> unitTemplates,
                boolean buildPage) {
            this(templates, resources, faction, unitTemplates, buildPage, List.of(), false);
        }
    }

    /** The default key of cell {@code slot} (UI-08), pressed with Alt: the classics' grid in reading order. */
    public static char hotkey(int slot) {
        return dev.strategos.core.client.input.DefaultBindings.GRID.charAt(slot);
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
                Button build = button(in, Kind.BUILD, slot++, t, cost(in, t, "Cost/resources/"));
                out.add(atLimit(in, t) ? disabled(build, "Limit reached") : build);
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
        if (!b.own() || !in.templates().has(b.template())) {
            return out;
        }
        if (b.state() != COMPLETE) {
            out.add(new Button(Kind.CANCEL_BUILDING, SLOTS - 1, b.template(), List.of(), true));
            return out;
        }
        int slot = 0;
        for (int t : trains(in.templates(), b.template())) {
            if (slot == SLOTS - 1) {
                break;
            }
            Button train = button(in, Kind.TRAIN, slot++, t, cost(in, t, "Cost/resources/"));
            if (train.reason() == null && b.queue().size() >= QUEUE_MAX) {
                train = disabled(train, "Queue full");
            } else if (train.reason() == null && !houses(in, t)) {
                train = disabled(train, "Needs housing");
            }
            out.add(train);
        }
        if (b.fieldLeft() == 0 && b.fieldRipening() == 0) {
            out.add(button(
                    in, Kind.RESEED, Math.min(slot, SLOTS - 1), b.template(), cost(in, b.template(), "Field/reseed/")));
        }
        ValuePath reach = in.templates().valuePaths().byPath("Reseeder/reach");
        if (reach != null && in.templates().value(b.template(), reach.id()) > 0) {
            out.add(new Button(
                    Kind.AUTO_RESEED, Math.min(slot, SLOTS - 2), b.template(), List.of(), true, b.autoReseed()));
        }
        out.add(new Button(Kind.DEMOLISH, SLOTS - 1, b.template(), List.of(), true, in.demolishAsked()));
        return out;
    }

    /** The most entries a queue holds (BuildingStore.QUEUE_MAX). */
    public static final int QUEUE_MAX = 8;

    /** {@code b} unusable for {@code reason}. */
    private static Button disabled(Button b, String reason) {
        return new Button(b.kind(), b.slot(), b.template(), b.cost(), false, b.on(), reason);
    }

    /** Whether the faction's free population holds a unit of {@code template}: its {@code Cost/population}. */
    private static boolean houses(Input in, int template) {
        FactionState f = in.faction();
        ValuePath path = in.templates().valuePaths().byPath("Cost/population");
        double need = path == null ? Double.NaN : in.templates().value(template, path.id());
        return f == null || !(need > 0) || f.populationUsed() + need <= f.populationCap();
    }

    /** Whether the faction has as many buildings of {@code template} as its {@code Limit/count} allows (BLD-08). */
    private static boolean atLimit(Input in, int template) {
        ValuePath path = in.templates().valuePaths().byPath("Limit/count");
        double limit = path == null ? Double.NaN : in.templates().value(template, path.id());
        if (!(limit > 0)) {
            return false;
        }
        int own = 0;
        for (BuildingState.Entry e : in.buildings()) {
            own += e.own() && e.template() == template ? 1 : 0;
        }
        return own >= limit;
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
        StringBuilder missing = new StringBuilder();
        for (int r = 0; r < cost.size(); r++) {
            long have = f == null || r >= f.stock().size() ? 0 : f.stock().get(r);
            if (cost.get(r) > have) {
                missing.append(missing.isEmpty() ? "Needs " : ", ")
                        .append((cost.get(r) - have + 999) / 1000)
                        .append(' ')
                        .append(r < in.resources().size() ? in.resources().get(r) : "?");
            }
        }
        String reason = missing.isEmpty() ? null : missing.toString();
        return new Button(kind, slot, template, cost, reason == null, false, reason);
    }

    /** One line of a template's breakdown (DATA-02): its label, the value path read, the value in the set. */
    public record Line(String label, String path, double value) {}

    /**
     * Template {@code template}'s breakdown for a tooltip (UI-08, DATA-02): the values a player weighs, read from the
     * set as the server's {@code StatService} reads a faction's template with no modifier: hit points, the melee blow by
     * damage type, the armour, the walk speed in blocks a second, the build or training time, population and housing.
     * Values the template has not are left out.
     */
    public static List<Line> breakdown(TemplateSet templates, int template) {
        List<Line> out = new ArrayList<>();
        line(out, templates, template, "Health", "Health/max", 1);
        line(out, templates, template, "Hack", "Attack/Melee/hack", 1);
        line(out, templates, template, "Pierce", "Attack/Melee/pierce", 1);
        line(out, templates, template, "Crush", "Attack/Melee/crush", 1);
        if (hasRanged(templates, template)) {
            line(out, templates, template, "Hack, ranged", CorePaths.RANGED_HACK, 1);
            line(out, templates, template, "Pierce, ranged", CorePaths.RANGED_PIERCE, 1);
            line(out, templates, template, "Crush, ranged", CorePaths.RANGED_CRUSH, 1);
            line(out, templates, template, "Fire, ranged", CorePaths.RANGED_FIRE, 1);
            line(out, templates, template, "Range, blocks", CorePaths.RANGED_RANGE, 1);
            line(out, templates, template, "High ground, % range a block", CorePaths.HIGH_RANGE, 100);
            line(out, templates, template, "High ground, % damage a block", CorePaths.HIGH_DAMAGE, 100);
            line(out, templates, template, "High ground, up to blocks", CorePaths.HIGH_MAX, 1);
        }
        line(out, templates, template, "Armour, hack", "Resistance/hack", 1);
        line(out, templates, template, "Armour, pierce", "Resistance/pierce", 1);
        line(out, templates, template, "Armour, crush", "Resistance/crush", 1);
        line(out, templates, template, "Speed, blocks a second", "UnitMotion/walkSpeed", 20);
        line(out, templates, template, "Time, seconds", "Cost/time", 1);
        line(out, templates, template, "Population", "Cost/population", 1);
        line(out, templates, template, "Houses", "Housing/capacity", 1);
        return out;
    }

    /** Whether the template has a ranged weapon: a positive range and some ranged damage. */
    private static boolean hasRanged(TemplateSet templates, int template) {
        if (!(value(templates, template, CorePaths.RANGED_RANGE) > 0)) return false;
        return value(templates, template, CorePaths.RANGED_HACK) > 0
                || value(templates, template, CorePaths.RANGED_PIERCE) > 0
                || value(templates, template, CorePaths.RANGED_CRUSH) > 0
                || value(templates, template, CorePaths.RANGED_FIRE) > 0;
    }

    private static double value(TemplateSet templates, int template, String path) {
        ValuePath p = templates.valuePaths().byPath(path);
        return p == null || !templates.has(template) ? Double.NaN : templates.value(template, p.id());
    }

    private static void line(
            List<Line> out, TemplateSet templates, int template, String label, String path, double unit) {
        ValuePath p = templates.valuePaths().byPath(path);
        double v = p == null || !templates.has(template) ? Double.NaN : templates.value(template, p.id());
        if (Double.isFinite(v) && v != 0) {
            out.add(new Line(label, path, v * unit));
        }
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
