package dev.strategos.mod.client.menu;

import dev.strategos.core.protocol.SkirmishSetup;
import dev.strategos.mod.server.net.LobbyAction;
import dev.strategos.mod.server.net.LobbyConfiguration;
import dev.strategos.mod.server.net.LobbyReply;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import org.jspecify.annotations.Nullable;

/** Canonical private lobby view with bounded intents; it never assigns occupants or acknowledges settings locally. */
public final class MultiplayerLobbyScreen extends Screen {
    private final @Nullable Screen parent;
    private @Nullable LobbyScreenModel shown;
    private @Nullable LobbyReply error;
    private boolean waiting;
    private boolean compact;
    private int left;
    private int top;
    private int panelWidth;
    private int tableLeft;
    private int rowsY;
    private int footerY;
    private int statusY;
    private int[] columns = {84, 66, 48, 64, 54, 56};
    private List<LobbyScreenModel.Row> rows = List.of();

    public MultiplayerLobbyScreen(@Nullable Screen parent) {
        super(text("title"));
        this.parent = parent;
    }

    private static Component text(String key) {
        return Component.translatable("strategos.lobby." + key);
    }

    private static String key(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    @Override
    protected void init() {
        shown = ClientLobby.session().model();
        error = ClientLobby.session().error();
        waiting = ClientLobby.session().waiting();
        panelWidth = Math.min(560, width - 20);
        left = (width - panelWidth) / 2;
        compact = panelWidth < 406;
        columns = compact ? new int[] {66, 54, 42, 48, 50} : new int[] {84, 66, 48, 64, 54, 56};
        LobbyScreenModel model = shown;
        rows = model == null ? List.of() : model.rows();
        int contentHeight = model == null ? 170 : 132 + 22 * rows.size();
        top = Math.max(4, (height - contentHeight) / 2);
        int tableWidth = IntStream.of(columns).sum() + 4 * (columns.length - 1);
        tableLeft = left + (panelWidth - tableWidth) / 2;
        rowsY = top + 48;
        if (model == null) {
            entry();
            return;
        }
        for (int i = 0; i < rows.size(); i++) row(model, rows.get(i), rowsY + 22 * i);
        int settingsY = rowsY + 22 * rows.size() + 2;
        settings(model, settingsY);
        footerY = settingsY + 24;
        statusY = footerY + 26;
        int part = (panelWidth - 24) / 3;
        var ready = addRenderableWidget(Button.builder(
                        text(model.ownReady() ? "not_ready" : "ready"),
                        button -> ClientLobby.request(model.readyIntent()))
                .bounds(left + 8, footerY, part, 20)
                .build());
        ready.active = model.mayReady() && !waiting;
        var start =
                addRenderableWidget(Button.builder(text("start"), button -> ClientLobby.request(model.startIntent()))
                        .bounds(left + 12 + part, footerY, part, 20)
                        .build());
        start.active = model.mayStart() && !waiting;
        addRenderableWidget(Button.builder(text(model.mayLeave() ? "leave" : "back"), button -> onClose())
                .bounds(left + 16 + 2 * part, footerY, part, 20)
                .build());
    }

    private void entry() {
        int part = (panelWidth - 20) / 2;
        var create = addRenderableWidget(
                Button.builder(text("create"), b -> ClientLobby.request(new LobbyAction.Open(defaultConfiguration())))
                        .bounds(left + 8, top + 56, part, 20)
                        .build());
        create.active = ClientLobby.available() && ClientLobby.mayHost() && !waiting;
        var join = addRenderableWidget(
                Button.builder(text("join"), b -> ClientLobby.request(new LobbyAction.JoinAvailable()))
                        .bounds(left + 12 + part, top + 56, part, 20)
                        .build());
        join.active = ClientLobby.available() && !waiting;
        footerY = top + 82;
        statusY = top + 110;
        addRenderableWidget(Button.builder(text("back"), b -> onClose())
                .bounds(left + 8, footerY, panelWidth - 16, 20)
                .build());
    }

    private static LobbyConfiguration defaultConfiguration() {
        return new LobbyConfiguration(
                2,
                List.of(
                        new LobbyConfiguration.Seat(0, LobbyConfiguration.Kind.HUMAN, 0, 0, "strategos:placeholder", 0),
                        new LobbyConfiguration.Seat(
                                1, LobbyConfiguration.Kind.HUMAN, 1, 1, "strategos:placeholder", 1)),
                SkirmishSetup.DEFAULTS);
    }

    private int column(int index) {
        int x = tableLeft;
        for (int i = 0; i < index; i++) x += columns[i] + 4;
        return x;
    }

    private void row(LobbyScreenModel model, LobbyScreenModel.Row row, int y) {
        var seat = row.configuration();
        boolean edit = model.mayConfigure() && !waiting;
        var kind = addRenderableWidget(CycleButton.<LobbyConfiguration.Kind>builder(v -> text("kind." + key(v)))
                .withValues(LobbyConfiguration.Kind.values())
                .withInitialValue(seat.kind())
                .displayOnlyValue()
                .withTooltip(v -> Tooltip.create(Component.literal(row.name())))
                .create(
                        column(1),
                        y,
                        columns[1],
                        20,
                        text("slot"),
                        (b, v) -> changeSeat(
                                seat.player(),
                                s -> new LobbyConfiguration.Seat(
                                        s.player(), v, s.faction(), s.team(), s.civilisation(), s.colour()))));
        kind.active = edit && !row.occupied();
        var team = addRenderableWidget(CycleButton.<Integer>builder(
                        v -> Component.translatable("strategos.lobby.team_value", v + 1))
                .withValues(IntStream.range(0, 16).boxed().toList())
                .withInitialValue(seat.team())
                .displayOnlyValue()
                .create(
                        column(2),
                        y,
                        columns[2],
                        20,
                        text("team"),
                        (b, v) -> changeFaction(
                                seat.player(),
                                s -> new LobbyConfiguration.Seat(
                                        s.player(), s.kind(), s.faction(), v, s.civilisation(), s.colour()))));
        team.active = edit && seat.kind() != LobbyConfiguration.Kind.CLOSED;
        if (!compact) {
            var civilisation = addRenderableWidget(Button.builder(
                            text(
                                    seat.civilisation().equals("strategos:placeholder")
                                            ? "civilisation_default"
                                            : "civilisation_server"),
                            b -> changeFaction(
                                    seat.player(),
                                    s -> new LobbyConfiguration.Seat(
                                            s.player(),
                                            s.kind(),
                                            s.faction(),
                                            s.team(),
                                            "strategos:placeholder",
                                            s.colour())))
                    .bounds(column(3), y, columns[3], 20)
                    .build());
            civilisation.active = edit
                    && seat.kind() != LobbyConfiguration.Kind.CLOSED
                    && !seat.civilisation().equals("strategos:placeholder");
        }
        int colourColumn = compact ? 3 : 4;
        var choices = IntStream.range(0, 16)
                .filter(value -> value == seat.colour()
                        || model.state().configuration().seats().stream()
                                .noneMatch(s -> s.kind() != LobbyConfiguration.Kind.CLOSED
                                        && s.faction() != seat.faction()
                                        && s.colour() == value))
                .boxed()
                .toList();
        var colour = addRenderableWidget(CycleButton.<Integer>builder(v -> Component.translatable(
                        "color.minecraft." + DyeColor.byId(v).getName()))
                .withValues(choices)
                .withInitialValue(seat.colour())
                .displayOnlyValue()
                .create(
                        column(colourColumn),
                        y,
                        columns[colourColumn],
                        20,
                        text("colour"),
                        (b, v) -> changeFaction(
                                seat.player(),
                                s -> new LobbyConfiguration.Seat(
                                        s.player(), s.kind(), s.faction(), s.team(), s.civilisation(), v))));
        colour.active = edit && seat.kind() != LobbyConfiguration.Kind.CLOSED;
    }

    private void changeSeat(int player, UnaryOperator<LobbyConfiguration.Seat> change) {
        edit(config -> new LobbyConfiguration(
                config.factionCapacity(),
                config.seats().stream()
                        .map(s -> s.player() == player ? change.apply(s) : s)
                        .toList(),
                config.setup()));
    }

    private void changeFaction(int player, UnaryOperator<LobbyConfiguration.Seat> change) {
        edit(config -> {
            int faction = config.seats().stream()
                    .filter(s -> s.player() == player)
                    .findFirst()
                    .orElseThrow()
                    .faction();
            return new LobbyConfiguration(
                    config.factionCapacity(),
                    config.seats().stream()
                            .map(s -> s.faction() == faction ? change.apply(s) : s)
                            .toList(),
                    config.setup());
        });
    }

    private void edit(UnaryOperator<LobbyConfiguration> update) {
        var model = ClientLobby.session().model();
        if (model == null || !model.mayConfigure() || ClientLobby.session().waiting()) return;
        try {
            ClientLobby.request(new LobbyAction.Edit(update.apply(model.state().configuration())));
        } catch (IllegalArgumentException invalid) {
            ClientLobby.reply(LobbyReply.REFUSED);
        }
    }

    private void setup(UnaryOperator<SkirmishSetup> change) {
        edit(config -> new LobbyConfiguration(config.factionCapacity(), config.seats(), change.apply(config.setup())));
    }

    private void settings(LobbyScreenModel model, int y) {
        var chosen = model.state().configuration().setup();
        int part = (panelWidth - 28) / 4;
        boolean edit = model.mayConfigure() && !waiting;
        var level = addRenderableWidget(CycleButton.<SkirmishSetup.Level>builder(v -> Component.translatable(
                        "strategos.lobby.ai_value", Component.translatable("strategos.skirmish.level." + key(v))))
                .withValues(SkirmishSetup.Level.values())
                .withInitialValue(chosen.level())
                .displayOnlyValue()
                .create(
                        left + 8,
                        y,
                        part,
                        20,
                        text("ai"),
                        (b, v) -> setup(
                                s -> new SkirmishSetup(s.map(), v, s.resources(), s.populationCap(), s.visibility()))));
        level.active = edit;
        var resources = addRenderableWidget(CycleButton.<SkirmishSetup.StartResources>builder(
                        v -> Component.translatable("strategos.skirmish.resources." + key(v)))
                .withValues(SkirmishSetup.StartResources.values())
                .withInitialValue(chosen.resources())
                .displayOnlyValue()
                .withTooltip(v -> Tooltip.create(Component.translatable("strategos.skirmish.resources")))
                .create(
                        left + 12 + part,
                        y,
                        part,
                        20,
                        text("resources"),
                        (b, v) -> setup(
                                s -> new SkirmishSetup(s.map(), s.level(), v, s.populationCap(), s.visibility()))));
        resources.active = edit;
        var populations = java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(50, 75, 100, 125, 150, 175, 200),
                        java.util.stream.Stream.of(chosen.populationCap()))
                .distinct()
                .sorted()
                .toList();
        var population = addRenderableWidget(CycleButton.<Integer>builder(
                        v -> Component.translatable("strategos.lobby.population_value", v))
                .withValues(populations)
                .withInitialValue(chosen.populationCap())
                .displayOnlyValue()
                .create(
                        left + 16 + 2 * part,
                        y,
                        part,
                        20,
                        text("population"),
                        (b, v) -> setup(s -> new SkirmishSetup(s.map(), s.level(), s.resources(), v, s.visibility()))));
        population.active = edit;
        var visibility = addRenderableWidget(CycleButton.<SkirmishSetup.Visibility>builder(
                        v -> Component.translatable("strategos.skirmish.visibility." + key(v)))
                .withValues(SkirmishSetup.Visibility.values())
                .withInitialValue(chosen.visibility())
                .displayOnlyValue()
                .create(
                        left + 20 + 3 * part,
                        y,
                        part,
                        20,
                        text("visibility"),
                        (b, v) -> setup(
                                s -> new SkirmishSetup(s.map(), s.level(), s.resources(), s.populationCap(), v))));
        visibility.active = edit;
    }

    @Override
    public void tick() {
        super.tick();
        if (!Objects.equals(shown, ClientLobby.session().model())
                || error != ClientLobby.session().error()
                || waiting != ClientLobby.session().waiting()) rebuildWidgets();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        ClientLobby.leave();
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xFF10151B);
        g.fill(left, top, left + panelWidth, statusY + 32, 0xFF202A35);
        for (int i = 0; i < rows.size(); i++)
            if (rows.get(i).self())
                g.fill(left + 4, rowsY + i * 22, left + panelWidth - 4, rowsY + i * 22 + 20, 0xFF2A4052);
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, top + 8, 0xFFFFFF);
        LobbyScreenModel model = shown;
        if (model == null) {
            g.drawWordWrap(font, text("entry_hint"), left + 10, top + 25, panelWidth - 20, 0xCDD5DD);
        } else {
            String map = model.state().configuration().setup().map();
            Component name = map.equals(SkirmishSetup.FIRST_SKIRMISH)
                    ? Component.translatable("strategos.map.first_skirmish")
                    : text("server_map");
            g.drawCenteredString(
                    font, Component.translatable("strategos.lobby.map_value", name), width / 2, top + 24, 0xB9C7D4);
            String[] labels = compact
                    ? new String[] {"player", "slot", "team", "colour", "readiness"}
                    : new String[] {"player", "slot", "team", "civilisation", "colour", "readiness"};
            for (int i = 0; i < labels.length; i++)
                g.drawCenteredString(font, text(labels[i]), column(i) + columns[i] / 2, rowsY - 11, 0xA8B4C0);
            for (int i = 0; i < rows.size(); i++) {
                var row = rows.get(i);
                int y = rowsY + i * 22 + 6;
                String nameText = row.occupied() ? row.name() + (row.host() ? " *" : "") : "—";
                nameText =
                        font.plainSubstrByWidth((row.configuration().player() + 1) + ". " + nameText, columns[0] - 6);
                g.drawCenteredString(font, nameText, column(0) + columns[0] / 2, y, row.self() ? 0xAAFFAA : 0xFFFFFF);
                int readyColumn = columns.length - 1;
                Component ready = row.configuration().kind() != LobbyConfiguration.Kind.HUMAN
                        ? text("not_applicable")
                        : text(!row.occupied() ? "waiting_player" : row.ready() ? "ready" : "not_ready");
                g.drawCenteredString(
                        font,
                        ready,
                        column(readyColumn) + columns[readyColumn] / 2,
                        y,
                        row.ready() ? 0xAAFFAA : 0xD3D9E0);
            }
        }
        Component status = error != null
                ? Component.translatable(error.translationKey())
                : waiting
                        ? text("waiting_server")
                        : model == null ? text("entry_status") : Component.translatable(model.statusKey());
        g.drawWordWrap(font, status, left + 10, statusY, panelWidth - 20, error == null ? 0xCDD5DD : 0xFFAAA0);
    }
}
