package dev.strategos.mod.client.hud;

import dev.strategos.core.client.hud.CardController;
import dev.strategos.core.client.hud.CommandCard;
import dev.strategos.core.client.hud.EconomyNotices;
import dev.strategos.core.client.hud.GroupBar;
import dev.strategos.core.client.hud.HudLayout;
import dev.strategos.core.client.hud.HudRect;
import dev.strategos.core.client.hud.HudTheme;
import dev.strategos.core.client.hud.Income;
import dev.strategos.core.client.hud.Minimap;
import dev.strategos.core.client.hud.ResourceBar;
import dev.strategos.core.client.hud.SelectionPanel;
import dev.strategos.core.client.hud.SelectionSummary;
import dev.strategos.core.client.pick.Picking;
import dev.strategos.core.client.select.Selection;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.BuildingState;
import dev.strategos.core.protocol.DepositState;
import dev.strategos.core.protocol.FactionState;
import dev.strategos.core.protocol.FeedbackMsg;
import dev.strategos.core.protocol.MatchJoin;
import dev.strategos.core.protocol.MatchNews;
import dev.strategos.core.protocol.UiQuery;
import dev.strategos.core.protocol.UnitDetail;
import dev.strategos.core.protocol.UnitHud;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.UnitState;
import dev.strategos.mod.client.StrategosClient;
import dev.strategos.mod.client.net.ClientPayloads;
import dev.strategos.mod.client.rts.RtsMode;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.entity.UnitSpawnData;
import dev.strategos.mod.platform.Ids;
import dev.strategos.mod.server.net.payload.UiQueryPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;

/**
 * The RTS HUD (UI-02, UI-06): while RTS mode is on, GUI layers above vanilla's draw the selection panel, the command
 * card, the control-group bar and the hover tooltip in {@link HudLayout}'s rectangles, and the vanilla hotbar,
 * crosshair, health, food, armour, air, experience and spectator layers are cancelled ({@code RenderGuiLayerEvent.Pre}).
 * The view-models are rebuilt once a client tick, after RTS mode's own tick; the layers only draw them, and a click on
 * the card or the panel goes to {@link CardController}. It times both, for PERF-14.
 */
public final class RtsHud {
    public static final ResourceLocation SELECTION = Ids.id("rts_selection");
    public static final ResourceLocation CARD = Ids.id("rts_command_card");
    public static final ResourceLocation RESOURCES = Ids.id("rts_resources");
    public static final ResourceLocation NOTICES = Ids.id("rts_notices");
    public static final ResourceLocation END = Ids.id("rts_match_end");
    public static final ResourceLocation MINIMAP = Ids.id("rts_minimap");
    public static final ResourceLocation GROUPS = Ids.id("rts_groups");
    public static final ResourceLocation TOOLTIP = Ids.id("rts_tooltip");
    public static final ResourceLocation INSPECT = Ids.id("first_person_inspect");

    /** What RTS mode hides: a commander has no hotbar, crosshair or survival bars. */
    private final Set<ResourceLocation> hidden = Set.of(
            VanillaGuiLayers.HOTBAR,
            VanillaGuiLayers.CROSSHAIR,
            VanillaGuiLayers.PLAYER_HEALTH,
            VanillaGuiLayers.FOOD_LEVEL,
            VanillaGuiLayers.ARMOR_LEVEL,
            VanillaGuiLayers.AIR_LEVEL,
            VanillaGuiLayers.VEHICLE_HEALTH,
            VanillaGuiLayers.JUMP_METER,
            VanillaGuiLayers.EXPERIENCE_BAR,
            VanillaGuiLayers.EXPERIENCE_LEVEL,
            VanillaGuiLayers.SELECTED_ITEM_NAME,
            VanillaGuiLayers.SPECTATOR_TOOLTIP);

    /** The hovered unit's tooltip: its type's name and its faction. */
    record Tooltip(String name, int faction) {}

    /**
     * In first person, the unit under the crosshair (CAM-07.4): its type's name, faction and handle, and what the
     * client knows of it: an own unit's hit points and state from {@code unit_detail}, an enemy's share of its hit
     * points from {@code unit_hud}.
     */
    record Inspect(String name, int faction, int handle, String detail) {}

    private final RtsMode mode;
    private final HudTheme theme = HudTheme.DEFAULT;
    private final HudPaint paint = new HudPaint();
    private final HudBanners banners = new HudBanners(paint);
    private SelectionSummary summary = SelectionSummary.EMPTY;
    private SelectionPanel panel = SelectionPanel.EMPTY;
    private CommandCard card = CommandCard.EMPTY;
    private final CardController controller = new CardController();
    private CommandCard.@Nullable Button hovered;
    private GroupBar bar = new GroupBar(List.of());
    private @Nullable Tooltip tooltip;
    private final Income income = new Income();
    private ResourceBar resources = ResourceBar.EMPTY;
    private List<ResourceBarSegments.Segment> segments = List.of();
    private @Nullable String barHover;
    private final EconomyNotices notices = new EconomyNotices();
    private List<EconomyNotices.Shown> shownNotices = List.of();
    private long clientTicks;
    private final RtsMinimap minimap = new RtsMinimap();
    private int alertsHeard;
    private int wavesHeard;
    private long alertAt = -1_000_000;

    private @Nullable Inspect inspect;
    private long buildNanos;
    /** Ticks between two {@code ui_query}s: four a second, the server's limit. */
    private static final int QUERY_EVERY = 5;

    private List<Integer> queried = List.of();
    private int sinceQuery = QUERY_EVERY;
    private long drawNanos;
    private long drawing;

    public RtsHud(RtsMode mode) {
        this.mode = mode;
    }

    public void register(IEventBus modBus) {
        modBus.addListener(RegisterGuiLayersEvent.class, e -> {
            e.registerAboveAll(SELECTION, this::drawSelection);
            e.registerAboveAll(CARD, this::drawCard);
            e.registerAboveAll(RESOURCES, this::drawResources);
            e.registerAboveAll(NOTICES, this::drawNotices);
            e.registerAboveAll(END, this::drawEnd);
            e.registerAboveAll(MINIMAP, this::drawMinimap);
            e.registerAboveAll(GROUPS, this::drawGroups);
            e.registerAboveAll(TOOLTIP, this::drawTooltip);
            e.registerAboveAll(INSPECT, this::drawInspect);
        });
        NeoForge.EVENT_BUS.addListener(RenderGuiLayerEvent.Pre.class, this::onLayer);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> build());
    }

    /** Nanoseconds the last client tick spent building the view-models. */
    public long buildNanos() {
        return buildNanos;
    }

    /** Nanoseconds the last frame spent drawing the layers. */
    public long drawNanos() {
        return drawNanos;
    }

    SelectionSummary summary() {
        return summary;
    }

    /** The command card as last built, for scenarios. */
    public CommandCard card() {
        return card;
    }

    /** The selection panel as last built, for scenarios. */
    public SelectionPanel panel() {
        return panel;
    }

    /**
     * A mouse press at GUI point ({@code x}, {@code y}): on the command card or a shown selection panel it is the HUD's,
     * the left button pressing what is under it; elsewhere it is the world's.
     */
    public boolean click(double x, double y, int button) {
        if (!shown()) {
            return false;
        }
        HudLayout l = layout();
        int pad = theme.padding();
        MatchJoin join = StrategosClient.match();
        if (join != null && minimap.ready() && l.minimap().contains(x, y)) {
            Minimap m = new Minimap(l.minimap(), join.sizeX(), join.sizeZ());
            if (button == 0 && m.contains(x, y)) { // UI-11: the camera to the clicked column
                double[] at = m.toWorld(x, y);
                mode.centreOn(join.originX() + at[0], join.originZ() + at[1]);
            }
            return true;
        }
        List<ResourceBarSegments.Segment> parts = segments;
        if (!parts.isEmpty() && l.resourceBar().contains(x, y)) {
            if (button == 0 && parts.get(parts.size() - 1).at().contains(x, y)) {
                mode.nextIdleWorker(); // the idle count is the idle-worker button
            }
            return true;
        }
        if (!card.buttons().isEmpty() && l.commandCard().contains(x, y)) {
            if (button == 0) {
                press(card.at(l.commandCard(), pad, x, y));
            }
            return true;
        }
        if (panel.kind() != SelectionPanel.Kind.EMPTY && l.selectionPanel().contains(x, y)) {
            BuildingState.Entry own = ownBuilding();
            int k = panel.queueAt(l.selectionPanel(), pad, x, y);
            if (button == 0 && own != null && k >= 0) {
                controller.cancel(own, k, mode::send);
            }
            return true;
        }
        return false;
    }

    /**
     * The card's cell {@code slot} pressed by its key (UI-08): as a click on its button, if it shows one; whether it
     * did.
     */
    public boolean pressCard(int slot) {
        for (CommandCard.Button b : card.buttons()) {
            if (b.slot() == slot) {
                press(b);
                return true;
            }
        }
        return false;
    }

    private void press(CommandCard.@Nullable Button b) {
        if (b != null && !b.affordable()) {
            return; // its reason is in the tooltip; the server would refuse it the same way
        }
        CardController.Click c = controller.click(
                b,
                mode.selection().handles(),
                ownBuilding(),
                StrategosClient.templates(),
                mode::send,
                net.minecraft.client.gui.screens.Screen.hasShiftDown()); // a batch of five (PROD-02)
        if (c.effect() == CardController.Effect.ARM) {
            mode.armPlacement(c.template());
        }
        build();
    }

    /** The selected own building's entry, or null. */
    private BuildingState.@Nullable Entry ownBuilding() {
        Integer handle = mode.selection().building();
        return handle == null ? null : entry(handle);
    }

    private static DepositState.@Nullable Entry deposit(@Nullable Integer node) {
        if (node == null) {
            return null;
        }
        for (DepositState.Entry e : StrategosClient.model().deposits()) {
            if (e.node() == node) {
                return e;
            }
        }
        return null;
    }

    private static BuildingState.@Nullable Entry entry(@Nullable Integer handle) {
        if (handle == null) {
            return null;
        }
        for (BuildingState.Entry e : StrategosClient.model().buildings()) {
            if (e.handle() == handle) {
                return e;
            }
        }
        return null;
    }

    private void onLayer(RenderGuiLayerEvent.Pre event) {
        if (mode.active() && hidden.contains(event.getName())) {
            event.setCanceled(true);
        }
    }

    /** A feedback message from the server: the economy's become notices (UI-10). */
    public void feedback(FeedbackMsg msg) {
        notices.accept(msg, clientTicks, StrategosClient.resources());
    }

    /** The notices shown now, the newest first, for scenarios. */
    public List<EconomyNotices.Shown> notices() {
        return shownNotices;
    }

    private void build() {
        long start = System.nanoTime();
        clientTicks++;
        Minecraft client = Minecraft.getInstance();
        minimap.tick(client);
        int announced = StrategosClient.model().waves();
        MatchNews wave = StrategosClient.model().wave();
        MatchJoin joined = StrategosClient.match();
        if (announced > wavesHeard) { // AI-17: an enemy's wave, a horn for those it comes for
            wavesHeard = announced;
            if (wave != null
                    && joined != null
                    && wave.faction() != joined.slot().value()) {
                client.getSoundManager()
                        .play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                net.minecraft.sounds.SoundEvents.RAID_HORN.value(), 1.0F, 1.0F));
            }
        }
        int heard = StrategosClient.model().alerts();
        if (heard > alertsHeard) { // UI-12: a new alert sounds once, here only
            alertsHeard = heard;
            alertAt = clientTicks;
            client.getSoundManager()
                    .play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.BELL_BLOCK, 1.0F, 0.8F));
        }
        Minecraft mc = Minecraft.getInstance();
        if (!mode.active() || mc.level == null) {
            summary = SelectionSummary.EMPTY;
            panel = SelectionPanel.EMPTY;
            card = CommandCard.EMPTY;
            hovered = null;
            bar = new GroupBar(List.of());
            tooltip = null;
            inspect = mode.firstPerson() && mc.level != null ? inspected() : null;
            return;
        }
        inspect = null;
        Map<Integer, UnitSpawnData> bodies = new HashMap<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof UnitEntity body && body.spawnData() != null) {
                bodies.put(body.spawnData().handle(), body.spawnData());
            }
        }
        TemplateSet templates = StrategosClient.templates();
        Selection selection = mode.selection();
        Integer inspected = selection.inspected();
        UnitSpawnData inspectedData = inspected == null ? null : bodies.get(inspected);
        summary = SelectionSummary.of(
                selection.handles(),
                h -> {
                    UnitSpawnData d = bodies.get(h);
                    return d == null ? -1 : d.templateIndex();
                },
                t -> name(templates, t),
                inspectedData == null ? null : name(templates, inspectedData.templateIndex()));
        selection.pruneBuilding(h -> entry(h) != null);
        BuildingState.Entry shown = entry(selection.shownBuilding());
        panel = SelectionPanel.of(summary, unit(selection, bodies), shown, deposit(selection.deposit()));
        controller.selected(selection.handles(), selection.building());
        List<Integer> unitTemplates = new ArrayList<>();
        for (int h : selection.handles()) {
            UnitSpawnData d = bodies.get(h);
            if (d != null) {
                unitTemplates.add(d.templateIndex());
            }
        }
        BuildingState.Entry own = selection.building() == null ? null : shown;
        card = CommandCard.of(
                new CommandCard.Input(
                        templates,
                        StrategosClient.resources(),
                        StrategosClient.model().faction(),
                        unitTemplates,
                        controller.buildPage(),
                        StrategosClient.model().buildings(),
                        own != null && controller.demolishAsked(own.handle())),
                own);
        double[] gui = cursorGui(mc);
        hovered = card.at(layout().commandCard(), theme.padding(), gui[0], gui[1]);
        bar = GroupBar.of(mode.groups());
        FactionState faction = StrategosClient.model().faction();
        if (faction != null) {
            income.accept(faction);
            notices.population(faction.populationUsed(), faction.populationCap());
        }
        shownNotices = notices.shown(clientTicks);
        resources = ResourceBar.of(faction, StrategosClient.resources(), income);
        segments = ResourceBarSegments.of(mc.font, layout().resourceBar(), resources);
        barHover = null;
        for (ResourceBarSegments.Segment s : segments) {
            if (s.at().contains(gui[0], gui[1])) {
                barHover = s.tooltip();
            }
        }
        tooltip = hovered == null && barHover == null ? hovered(mc, templates) : null;
        query(selection.handles());
        buildNanos = System.nanoTime() - start;
    }

    /** The resource bar as last built, for scenarios. */
    public ResourceBar resources() {
        return resources;
    }

    private void drawResources(GuiGraphics g, DeltaTracker delta) {
        List<ResourceBarSegments.Segment> parts = segments;
        if (!shown() || parts.isEmpty() || resources.resources().isEmpty()) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        paint.panel(g, layout().resourceBar());
        for (ResourceBarSegments.Segment s : parts) {
            if (s.icon() != null) {
                paint.small(g, s.icon(), s.at().x(), s.at().y() + 2);
            }
            g.drawString(font, s.text(), s.at().x() + 10, s.at().y() + 2, s.colour(), false);
        }
    }

    /** The minimap's pixel of match-local column ({@code x}, {@code z}), ARGB, for scenarios; 0 for none. */
    public int minimapPixel(int x, int z) {
        return minimap.pixel(x, z);
    }

    /** The HUD's ticks so far: what its blinking and fading count in, for a scenario that frames them. */
    public long clientTicks() {
        return clientTicks;
    }

    /** The HUD's ticks since the latest alert was heard; large when there was none. */
    public long alertAge() {
        return clientTicks - alertAt;
    }

    /** The camera to the latest alert (UI-12's jump key); whether there was one. */
    public boolean jumpToAlert() {
        MatchNews alert = StrategosClient.model().alert();
        MatchJoin join = StrategosClient.match();
        if (alert == null || join == null) {
            return false;
        }
        mode.centreOn(join.originX() + alert.x(), join.originZ() + alert.z());
        return true;
    }

    /** One unit's details: the one own unit selected, from its {@code unit_detail}, or the enemy inspected. */
    private SelectionPanel.@Nullable Unit unit(Selection selection, Map<Integer, UnitSpawnData> bodies) {
        MatchJoin join = StrategosClient.match();
        if (join == null) {
            return null;
        }
        if (selection.handles().size() == 1) {
            int handle = selection.handles().get(0);
            for (UnitDetail.Unit u : StrategosClient.model().details()) {
                if (u.handle() == handle) {
                    return SelectionPanel.Unit.of(u, join.slot().value());
                }
            }
            return null;
        }
        Integer inspected = selection.inspected();
        UnitSpawnData d = inspected == null ? null : bodies.get(inspected);
        if (d == null || !selection.handles().isEmpty()) {
            return null;
        }
        int health = StrategosClient.model().health(Handle.index(d.handle()));
        return new SelectionPanel.Unit(
                d.handle(), d.templateIndex(), d.faction(), health, UnitHud.WHOLE, -1, -1, 0, false);
    }

    /** The selection's details, asked for again when it changes, at most every {@value #QUERY_EVERY} ticks (NET-08). */
    private void query(List<Integer> handles) {
        List<Integer> wanted = handles.size() > UiQuery.MAX_HANDLES ? handles.subList(0, UiQuery.MAX_HANDLES) : handles;
        sinceQuery++;
        if (wanted.equals(queried) || sinceQuery < QUERY_EVERY) {
            return;
        }
        if (ClientPayloads.toServer(new UiQueryPayload(new UiQuery(wanted)))) {
            queried = List.copyOf(wanted);
            sinceQuery = 0;
        }
    }

    /** The unit under the first-person crosshair, its own details asked for (CAM-07.4). */
    private @Nullable Inspect inspected() {
        UnitEntity body = mode.crosshairUnit();
        MatchJoin join = StrategosClient.match();
        UnitSpawnData data = body == null ? null : body.spawnData();
        if (data == null || join == null) {
            return null;
        }
        String detail;
        if (data.faction() == join.slot().value()) {
            query(List.of(data.handle()));
            UnitDetail.Unit known = null;
            for (UnitDetail.Unit u : StrategosClient.model().details()) {
                if (u.handle() == data.handle()) {
                    known = u;
                }
            }
            detail = known == null
                    ? "…"
                    : Math.round(known.health()) + " / " + Math.round(known.maxHealth()) + " · "
                            + UnitState.ofCode(known.state()).id();
        } else {
            int health = StrategosClient.model().health(Handle.index(data.handle()));
            detail = Math.round(100.0 * health / UnitHud.WHOLE) + " %";
        }
        return new Inspect(
                name(StrategosClient.templates(), data.templateIndex()), data.faction(), data.handle(), detail);
    }

    /** The first-person inspection, for scenarios; null when the crosshair is on no unit. */
    public @Nullable String inspecting() {
        Inspect i = inspect;
        return i == null ? null : i.name() + " " + i.detail();
    }

    private @Nullable Tooltip hovered(Minecraft mc, TemplateSet templates) {
        double[] at = mode.cursor();
        if (at == null || mc.level == null) {
            return null;
        }
        RtsMode.Pick pick = mode.find(
                Picking.toWindow(
                        at[0],
                        mc.getWindow().getGuiScaledWidth(),
                        mc.getWindow().getScreenWidth()),
                Picking.toWindow(
                        at[1],
                        mc.getWindow().getGuiScaledHeight(),
                        mc.getWindow().getScreenHeight()));
        Entity e = pick == null || pick.entityId() < 0 ? null : mc.level.getEntity(pick.entityId());
        if (!(e instanceof UnitEntity body) || body.spawnData() == null) {
            return null;
        }
        return new Tooltip(
                name(templates, body.spawnData().templateIndex()),
                body.spawnData().faction());
    }

    /** A template's name, its translation key in the language files. */
    private static String name(TemplateSet templates, int index) {
        return templates.has(index)
                ? Component.translatable(templates.byIndex(index).name()).getString()
                : "?";
    }

    /** The HUD's parts for the window as it is now. */
    public static HudLayout layout() {
        Minecraft mc = Minecraft.getInstance();
        int scale = (int) Math.round(mc.getWindow().getGuiScale());
        return HudLayout.compute(mc.getWindow().getWidth(), mc.getWindow().getHeight(), Math.max(1, scale));
    }

    private void drawSelection(GuiGraphics g, DeltaTracker delta) {
        drawing = System.nanoTime();
        SelectionPanel p = panel;
        if (!shown() || p.kind() == SelectionPanel.Kind.EMPTY) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        TemplateSet templates = StrategosClient.templates();
        HudRect r = layout().selectionPanel();
        int pad = theme.padding();
        paint.panel(g, r);
        switch (p.kind()) {
            case TYPES -> {
                int capacity = SelectionPanel.typeCapacity(r, pad);
                List<SelectionSummary.TypeCount> types = p.summary().types();
                for (int k = 0; k < types.size() && k < capacity; k++) {
                    HudRect cell = SelectionPanel.typeCell(r, pad, k);
                    boolean more = k == capacity - 1 && types.size() > capacity;
                    if (!more) {
                        icon(g, font, templates, types.get(k).template(), cell);
                    }
                    String count = more
                            ? "+" + (types.size() - k)
                            : String.valueOf(types.get(k).count());
                    g.drawString(
                            font,
                            count,
                            cell.right() - font.width(count),
                            cell.bottom() - font.lineHeight + 1,
                            theme.text(),
                            true);
                }
            }
            case UNIT -> {
                SelectionPanel.Unit u = java.util.Objects.requireNonNull(p.unit());
                icon(g, font, templates, u.template(), SelectionPanel.icon(r, pad));
                paint.text(g, font, r, 0, name(templates, u.template()), theme.faction(u.faction()));
                paint.text(
                        g,
                        font,
                        r,
                        1,
                        u.own()
                                ? Math.round(u.health()) + " / " + Math.round(u.maxHealth()) + " HP"
                                : Math.round(100.0 * u.health() / u.maxHealth()) + " % HP",
                        theme.text());
                if (u.own()) {
                    String state = UnitState.ofCode(u.state()).id();
                    List<String> resources = StrategosClient.resources();
                    if (u.carried() >= 0 && u.load() > 0) {
                        state += " · " + u.load() / 1000 + " "
                                + (u.carried() < resources.size() ? resources.get(u.carried()) : "?");
                    }
                    paint.text(g, font, r, 2, state, theme.dimText());
                }
            }
            case BUILDING -> drawBuilding(g, font, templates, r, pad, java.util.Objects.requireNonNull(p.building()));
            case DEPOSIT -> {
                DepositState.Entry d = java.util.Objects.requireNonNull(p.deposit());
                List<String> resources = StrategosClient.resources();
                icon(g, font, templates, d.template(), SelectionPanel.icon(r, pad));
                paint.text(g, font, r, 0, name(templates, d.template()), theme.text());
                paint.text(
                        g,
                        font,
                        r,
                        1,
                        SelectionPanel.depositLine(
                                d, d.resource() < resources.size() ? resources.get(d.resource()) : "?"),
                        theme.text());
            }
            default -> {}
        }
    }

    /** One building: its name and state, then its queue with the head's progress, or a field's crop. */
    private void drawBuilding(
            GuiGraphics g, Font font, TemplateSet templates, HudRect r, int pad, BuildingState.Entry b) {
        icon(g, font, templates, b.template(), SelectionPanel.icon(r, pad));
        paint.text(g, font, r, 0, name(templates, b.template()), theme.faction(b.faction()));
        String state = switch (b.state()) {
            case 0 -> "Foundation";
            case 1 -> "Building " + b.progress() / 10 + " %";
            default -> Math.round(100.0 * b.health() / UnitHud.WHOLE) + " % HP";
        };
        paint.text(g, font, r, 1, b.rally() ? state + " · rally set" : state, theme.text());
        if (b.fieldLeft() >= 0) {
            String crop = b.fieldRipening() > 0
                    ? "Ripening, " + (b.fieldRipening() + 19) / 20 + " s"
                    : b.fieldLeft() > 0 ? b.fieldLeft() / 1000 + " food left" : "Empty: reseed it";
            paint.text(g, font, r, 2, crop, theme.dimText());
            return;
        }
        for (int k = 0; k < b.queue().size(); k++) {
            HudRect cell = SelectionPanel.queueCell(r, pad, k);
            g.fill(cell.x(), cell.y(), cell.right(), cell.bottom(), theme.border());
            icon(g, font, templates, b.queue().get(k), cell);
            if (b.batch(k) > 1) { // a batch (PROD-02): its count in the corner
                String n = "×" + b.batch(k);
                g.drawString(
                        font, n, cell.right() - font.width(n), cell.bottom() - font.lineHeight + 1, theme.text(), true);
            }
            if (k == 0) {
                int w = (int) Math.round(cell.width() * Math.min(1000, b.trained()) / 1000.0);
                g.fill(cell.x(), cell.bottom() - 2, cell.x() + w, cell.bottom(), theme.accent());
            }
        }
    }

    /** The command card: a cell per button with its icon, dimmed where the stock does not cover it. */
    private void drawCard(GuiGraphics g, DeltaTracker delta) {
        CommandCard c = card;
        if (!shown() || c.buttons().isEmpty()) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        TemplateSet templates = StrategosClient.templates();
        HudRect r = layout().commandCard();
        int pad = theme.padding();
        paint.panel(g, r);
        for (CommandCard.Button b : c.buttons()) {
            HudRect cell = CommandCard.cell(r, pad, b.slot());
            g.fill(
                    cell.x(),
                    cell.y(),
                    cell.right(),
                    cell.bottom(),
                    b.equals(hovered) ? theme.accent() & 0x60FFFFFF : 0x40FFFFFF);
            switch (b.kind()) {
                case TRAIN, BUILD -> icon(g, font, templates, b.template(), cell);
                case RESEED -> paint.item(g, "minecraft:wheat_seeds", cell);
                case BUILD_PAGE -> paint.item(g, "minecraft:bricks", cell);
                case BACK -> paint.item(g, "minecraft:arrow", cell);
                case STOP -> paint.item(g, "minecraft:barrier", cell);
                case AUTO_RESEED -> {
                    paint.item(g, "minecraft:hay_block", cell);
                    if (!b.on()) { // off: dimmed
                        g.fill(cell.x(), cell.y(), cell.right(), cell.bottom(), 0x80202020);
                    }
                }
                case CANCEL_BUILDING -> paint.item(g, "minecraft:barrier", cell);
                case DEMOLISH -> {
                    paint.item(g, "minecraft:tnt", cell);
                    if (b.on()) { // asked: one more click demolishes
                        g.fill(cell.x(), cell.y(), cell.right(), cell.bottom(), 0x60FF0000);
                    }
                }
            }
            g.drawString(
                    font,
                    String.valueOf(CommandCard.hotkey(b.slot())),
                    cell.x() + 1,
                    cell.y() + 1,
                    theme.dimText(),
                    true); // its key, with Alt (UI-08)
            if (!b.affordable()) {
                g.fill(cell.x(), cell.y(), cell.right(), cell.bottom(), 0x90400000);
            }
        }
    }

    /** What the hovered card button does, and what it costs. */
    private String describe(CommandCard.Button b, TemplateSet templates) {
        String what = switch (b.kind()) {
            case TRAIN -> "Train " + name(templates, b.template());
            case BUILD -> "Build " + name(templates, b.template());
            case RESEED -> "Reseed";
            case BUILD_PAGE -> "Build";
            case BACK -> "Back";
            case STOP -> "Stop";
            case AUTO_RESEED -> b.on() ? "Auto-reseed: on (click to turn off)" : "Auto-reseed: off (click to turn on)";
            case CANCEL_BUILDING -> "Cancel: the share not yet built comes back";
            case DEMOLISH -> b.on() ? "Click again to demolish, for nothing back" : "Demolish";
        };
        List<String> resources = StrategosClient.resources();
        StringBuilder text = new StringBuilder(what);
        for (int k = 0; k < b.cost().size(); k++) {
            if (b.cost().get(k) > 0) {
                text.append(" · ")
                        .append(b.cost().get(k) / 1000)
                        .append(' ')
                        .append(k < resources.size() ? resources.get(k) : "?");
            }
        }
        if (b.kind() == CommandCard.Kind.TRAIN || b.kind() == CommandCard.Kind.BUILD) {
            for (CommandCard.Line line : CommandCard.breakdown(templates, b.template())) {
                text.append(" · ").append(line.label()).append(' ').append(plain(line.value()));
            }
        }
        String reason = b.reason();
        if (reason != null) {
            text.append(" — ").append(reason);
        }
        return text.toString();
    }

    /** A number as a player reads it: a whole one bare, else to one decimal. */
    private static String plain(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    /** Template {@code template}'s icon centred in {@code cell}, or its name's first letter when it has none. */
    private void icon(GuiGraphics g, Font font, TemplateSet templates, int template, HudRect cell) {
        String id = CommandCard.icon(templates, template);
        if (id != null) {
            paint.item(g, id, cell);
        } else {
            String letter = name(templates, template).substring(0, 1);
            g.drawString(
                    font,
                    letter,
                    cell.x() + (cell.width() - font.width(letter)) / 2,
                    cell.y() + (cell.height() - font.lineHeight) / 2 + 1,
                    theme.text(),
                    false);
        }
    }

    /** The cursor in GUI pixels, as Minecraft turns window points into a screen's mouse position. */
    private static double[] cursorGui(Minecraft mc) {
        return new double[] {
            mc.mouseHandler.xpos()
                    * mc.getWindow().getGuiScaledWidth()
                    / mc.getWindow().getScreenWidth(),
            mc.mouseHandler.ypos()
                    * mc.getWindow().getGuiScaledHeight()
                    / mc.getWindow().getScreenHeight()
        };
    }

    private void drawGroups(GuiGraphics g, DeltaTracker delta) {
        if (shown()) {
            banners.groups(g, bar);
        }
    }

    private void drawTooltip(GuiGraphics g, DeltaTracker delta) {
        Tooltip t = tooltip;
        Minecraft mc = Minecraft.getInstance();
        CommandCard.Button b = hovered;
        String bar = barHover;
        if (shown() && bar != null) {
            Font font = mc.font;
            int pad = theme.padding();
            double[] gui = cursorGui(mc);
            HudRect r = layout().tooltip(font.width(bar) + 2 * pad, font.lineHeight + 2 * pad, gui[0], gui[1]);
            paint.panel(g, r);
            g.drawString(font, bar, r.x() + pad, r.y() + pad + 1, theme.text(), false);
        }
        if (shown() && b != null) {
            Font font = mc.font;
            String text = describe(b, StrategosClient.templates());
            int pad = theme.padding();
            double[] gui = cursorGui(mc);
            HudRect r = layout().tooltip(font.width(text) + 2 * pad, font.lineHeight + 2 * pad, gui[0], gui[1]);
            paint.panel(g, r);
            g.drawString(font, text, r.x() + pad, r.y() + pad + 1, b.affordable() ? theme.text() : 0xFFFF8080, false);
        }
        if (shown() && t != null) {
            Font font = mc.font;
            int pad = theme.padding();
            int swatch = font.lineHeight - 2;
            int w = swatch + 3 + font.width(t.name()) + 2 * pad;
            int h = font.lineHeight + 2 * pad;
            // the cursor in GUI pixels, as Minecraft turns window points into a screen's mouse position
            double x = mc.mouseHandler.xpos()
                    * mc.getWindow().getGuiScaledWidth()
                    / mc.getWindow().getScreenWidth();
            double y = mc.mouseHandler.ypos()
                    * mc.getWindow().getGuiScaledHeight()
                    / mc.getWindow().getScreenHeight();
            HudRect r = layout().tooltip(w, h, x, y);
            paint.panel(g, r);
            g.fill(
                    r.x() + pad,
                    r.y() + pad + 1,
                    r.x() + pad + swatch,
                    r.y() + pad + 1 + swatch,
                    theme.faction(t.faction()));
            g.drawString(font, t.name(), r.x() + pad + swatch + 3, r.y() + pad + 1, theme.text(), false);
        }
        drawNanos = System.nanoTime() - drawing;
    }

    /** Under the first-person crosshair: a panel with the unit's name, faction swatch and what is known of it. */
    private void drawInspect(GuiGraphics g, DeltaTracker delta) {
        Inspect i = inspect;
        Minecraft mc = Minecraft.getInstance();
        if (i == null || mc.options.hideGui || mode.active()) {
            return;
        }
        Font font = mc.font;
        int pad = theme.padding();
        int swatch = font.lineHeight - 2;
        int w = Math.max(swatch + 3 + font.width(i.name()), font.width(i.detail())) + 2 * pad;
        int h = 2 * font.lineHeight + 2 * pad + 1;
        int x = mc.getWindow().getGuiScaledWidth() / 2 + 8;
        int y = mc.getWindow().getGuiScaledHeight() / 2 + 8;
        HudRect r = new HudRect(x, y, w, h);
        paint.panel(g, r);
        g.fill(
                r.x() + pad,
                r.y() + pad + 1,
                r.x() + pad + swatch,
                r.y() + pad + 1 + swatch,
                theme.faction(i.faction()));
        g.drawString(font, i.name(), r.x() + pad + swatch + 3, r.y() + pad + 1, theme.text(), false);
        g.drawString(font, i.detail(), r.x() + pad, r.y() + pad + 2 + font.lineHeight, theme.text(), false);
    }

    /** In RTS mode with the GUI shown: F1 hides the HUD as it hides vanilla's. */
    private boolean shown() {
        return mode.active() && !Minecraft.getInstance().options.hideGui;
    }

    private void drawNotices(GuiGraphics g, DeltaTracker delta) {
        if (shown()) {
            banners.notices(g, shownNotices, clientTicks - alertAt < 100);
        }
    }

    private void drawEnd(GuiGraphics g, DeltaTracker delta) {
        banners.end(g);
    }

    private void drawMinimap(GuiGraphics g, DeltaTracker delta) {
        if (shown()) {
            minimap.draw(g, mode, paint, clientTicks, alertAt);
        }
    }
}
