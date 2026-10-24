package dev.strategos.mod.client.hud;

import dev.strategos.core.client.camera.CameraRig;
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
import dev.strategos.core.client.pick.View;
import dev.strategos.core.client.select.Selection;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.BuildingState;
import dev.strategos.core.protocol.FactionState;
import dev.strategos.core.protocol.FeedbackMsg;
import dev.strategos.core.protocol.MatchControl;
import dev.strategos.core.protocol.MatchJoin;
import dev.strategos.core.protocol.MatchNews;
import dev.strategos.core.protocol.MatchStateMsg;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
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
    private SelectionSummary summary = SelectionSummary.EMPTY;
    private SelectionPanel panel = SelectionPanel.EMPTY;
    private CommandCard card = CommandCard.EMPTY;
    private final CardController controller = new CardController();
    private CommandCard.@Nullable Button hovered;
    private final Map<String, ItemStack> icons = new HashMap<>();
    private GroupBar bar = new GroupBar(List.of());
    private @Nullable Tooltip tooltip;
    private final Income income = new Income();
    private ResourceBar resources = ResourceBar.EMPTY;
    private List<Segment> segments = List.of();
    private @Nullable String barHover;
    private final EconomyNotices notices = new EconomyNotices();
    private List<EconomyNotices.Shown> shownNotices = List.of();
    private long clientTicks;
    private final RtsMinimap minimap = new RtsMinimap();
    private int alertsHeard;
    private int wavesHeard;
    private long alertAt = -1_000_000;

    /** One part of the resource bar: where it is, its icon (null for none), its text and colour, and its tooltip. */
    record Segment(HudRect at, @Nullable String icon, String text, int colour, String tooltip) {}

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
        List<Segment> parts = segments;
        if (!parts.isEmpty() && l.resourceBar().contains(x, y)) {
            if (button == 0 && parts.get(parts.size() - 1).at().contains(x, y)) {
                mode.nextIdleWorker(); // the idle count is the idle-worker button
            }
            return true;
        }
        if (!card.buttons().isEmpty() && l.commandCard().contains(x, y)) {
            if (button == 0) {
                CardController.Click c = controller.click(
                        card.at(l.commandCard(), pad, x, y),
                        mode.selection().handles(),
                        ownBuilding(),
                        StrategosClient.templates(),
                        mode::send);
                if (c.effect() == CardController.Effect.ARM) {
                    mode.armPlacement(c.template());
                }
                build();
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

    /** The selected own building's entry, or null. */
    private BuildingState.@Nullable Entry ownBuilding() {
        Integer handle = mode.selection().building();
        return handle == null ? null : entry(handle);
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
        panel = SelectionPanel.of(summary, unit(selection, bodies), shown);
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
                        controller.buildPage()),
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
        segments = segments(mc.font, layout().resourceBar(), resources);
        barHover = null;
        for (Segment s : segments) {
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

    /**
     * The bar's parts, left to right: each resource's icon and stock, with its income a minute where all of them fit,
     * the population (red at its cap) and the idle workers.
     */
    private static List<Segment> segments(Font font, HudRect bar, ResourceBar b) {
        for (boolean incomes : new boolean[] {true, false}) {
            List<Segment> out = new ArrayList<>();
            int x = bar.x() + 3;
            for (ResourceBar.Entry e : b.resources()) {
                String text =
                        incomes && e.perMinute() > 0 ? e.stock() + " +" + e.perMinute() : String.valueOf(e.stock());
                int w = 10 + font.width(text);
                out.add(new Segment(
                        new HudRect(x, bar.y(), w, bar.height()),
                        resourceIcon(e.id()),
                        text,
                        HudTheme.DEFAULT.text(),
                        e.id() + ": " + e.stock() + " · " + e.perMinute() + " a minute · " + e.workers()
                                + " gathering"));
                x += w + 6;
            }
            String pop = b.populationUsed() + "/" + b.populationCap();
            int pw = 10 + font.width(pop);
            out.add(new Segment(
                    new HudRect(x, bar.y(), pw, bar.height()),
                    "minecraft:player_head",
                    pop,
                    b.capped() ? 0xFFFF6060 : HudTheme.DEFAULT.text(),
                    b.capped() ? "Population " + pop + ": build houses to train more" : "Population " + pop));
            x += pw + 6;
            String idle = String.valueOf(b.idleWorkers());
            int iw = 10 + font.width(idle);
            out.add(new Segment(
                    new HudRect(x, bar.y(), iw, bar.height()),
                    "minecraft:wooden_axe",
                    idle,
                    b.idleWorkers() > 0 ? HudTheme.DEFAULT.accent() : HudTheme.DEFAULT.dimText(),
                    b.idleWorkers() + " idle workers"));
            x += iw;
            if (x <= bar.right() || !incomes) {
                return out;
            }
        }
        return List.of();
    }

    /** A resource's icon, by its id: the item a player knows it by. */
    private static String resourceIcon(String id) {
        return switch (id) {
            case "food" -> "minecraft:apple";
            case "wood" -> "minecraft:oak_log";
            case "stone" -> "minecraft:cobblestone";
            case "iron" -> "minecraft:iron_ingot";
            default -> "minecraft:paper";
        };
    }

    private void drawResources(GuiGraphics g, DeltaTracker delta) {
        List<Segment> parts = segments;
        if (!shown() || parts.isEmpty() || resources.resources().isEmpty()) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        panel(g, layout().resourceBar());
        for (Segment s : parts) {
            if (s.icon() != null) {
                small(g, s.icon(), s.at().x(), s.at().y() + 2);
            }
            g.drawString(font, s.text(), s.at().x() + 10, s.at().y() + 2, s.colour(), false);
        }
    }

    /** The newest notices, right-aligned in the notices' strip, the newest nearest the corner. */
    private void drawNotices(GuiGraphics g, DeltaTracker delta) {
        List<EconomyNotices.Shown> shown = shownNotices;
        boolean alerted = clientTicks - alertAt < 100;
        MatchNews coming = StrategosClient.model().wave();
        boolean waveOn =
                coming != null && coming.ticks() > StrategosClient.model().tick();
        if (!shown() || (shown.isEmpty() && !alerted && !waveOn)) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        HudRect r = layout().notices();
        int x = r.right();
        MatchNews wave = StrategosClient.model().wave();
        MatchJoin joined = StrategosClient.match();
        long matchTick = StrategosClient.model().tick();
        if (wave != null && joined != null && wave.faction() != joined.slot().value() && wave.ticks() > matchTick) {
            String text = "Attack coming: " + (wave.ticks() - matchTick + 19) / 20 + " s"; // AI-17's countdown
            int w = font.width(text) + 2 * theme.padding();
            x -= w;
            panel(g, new HudRect(x, r.y(), w, r.height()));
            g.drawString(font, text, x + theme.padding(), r.y() + 2, 0xFFFFB040, false);
            x -= 2;
        }
        if (alerted) { // UI-12: the newest alert first, in red
            String text = "Under attack! (J)";
            int w = font.width(text) + 2 * theme.padding();
            x -= w;
            panel(g, new HudRect(x, r.y(), w, r.height()));
            g.drawString(font, text, x + theme.padding(), r.y() + 2, 0xFFFF4040, false);
            x -= 2;
        }
        for (EconomyNotices.Shown s : shown) {
            String text = switch (s.cause()) {
                case NOT_ENOUGH_RESOURCES -> "Not enough " + s.detail();
                case POPULATION_CAPPED -> "Population capped: build houses";
                case DEPOSIT_EXHAUSTED -> s.count() > 1 ? "Workers idle (" + s.count() + ")" : "A worker is idle";
            };
            int w = font.width(text) + 2 * theme.padding();
            if (x - w < r.x()) {
                break;
            }
            x -= w;
            panel(g, new HudRect(x, r.y(), w, r.height()));
            g.drawString(
                    font,
                    text,
                    x + theme.padding(),
                    r.y() + 2,
                    s.cause() == EconomyNotices.Cause.DEPOSIT_EXHAUSTED ? theme.text() : 0xFFFF9060,
                    false);
            x -= 2;
        }
    }

    /**
     * The end screen (MATCH-01): once the match is over, a panel in the middle of the window says whether the
     * player's faction won, who did, and how long the match lasted. It stays until the player leaves the match.
     */
    private void drawEnd(GuiGraphics g, DeltaTracker delta) {
        MatchNews end = StrategosClient.model().end();
        MatchJoin join = StrategosClient.match();
        Minecraft mc = Minecraft.getInstance();
        if (join != null && end == null && !mc.options.hideGui) {
            drawPace(g, mc);
        }
        if (end == null || join == null || mc.options.hideGui) {
            return;
        }
        Font font = mc.font;
        int own = join.slot().value();
        String title = end.faction() == own ? "Victory" : end.faction() < 0 ? "The match is over" : "Defeat";
        String winner = end.faction() < 0 ? "Nobody won" : "Faction " + (end.faction() + 1) + " wins";
        long seconds = end.ticks() / 20;
        String time = "After " + seconds / 60 + ":" + String.format(java.util.Locale.ROOT, "%02d", seconds % 60);
        int w = Math.max(font.width(winner), font.width(time)) + 40;
        int h = 3 * font.lineHeight + 24;
        int x = (mc.getWindow().getGuiScaledWidth() - w) / 2;
        int y = (mc.getWindow().getGuiScaledHeight() - h) / 3;
        panel(g, new HudRect(x, y, w, h));
        g.pose().pushPose();
        g.pose().translate(x + w / 2.0, y + 6, 0);
        g.pose().scale(2, 2, 1);
        g.drawString(
                font, title, -font.width(title) / 2, 0, end.faction() == own ? theme.accent() : theme.text(), true);
        g.pose().popPose();
        int line = y + 8 + 2 * font.lineHeight;
        if (end.faction() >= 0) {
            g.fill(
                    x + (w - font.width(winner)) / 2 - 10,
                    line,
                    x + (w - font.width(winner)) / 2 - 3,
                    line + 7,
                    theme.faction(end.faction()));
        }
        g.drawString(font, winner, x + (w - font.width(winner)) / 2, line, theme.text(), false);
        g.drawString(font, time, x + (w - font.width(time)) / 2, line + font.lineHeight + 2, theme.dimText(), false);
    }

    /** The minimap's pixel of match-local column ({@code x}, {@code z}), ARGB, for scenarios; 0 for none. */
    public int minimapPixel(int x, int z) {
        return minimap.pixel(x, z);
    }

    /** Paused, a banner at the top; another speed than normal, it beside the notices (MATCH-05). */
    private void drawPace(GuiGraphics g, Minecraft mc) {
        Font font = mc.font;
        MatchStateMsg state = StrategosClient.state();
        int w = mc.getWindow().getGuiScaledWidth();
        if (state != null && state.lifecycle() == MatchStateMsg.Lifecycle.PAUSED) {
            String text = "Paused (F9)";
            int tw = font.width(text) * 2;
            int y = layout().resourceBar().bottom() + 8;
            panel(g, new HudRect((w - tw) / 2 - 6, y - 4, tw + 12, font.lineHeight * 2 + 6));
            g.pose().pushPose();
            g.pose().translate(w / 2.0, y, 0);
            g.pose().scale(2, 2, 1);
            g.drawString(font, text, -font.width(text) / 2, 0, theme.accent(), true);
            g.pose().popPose();
        }
        float rate = mc.level == null ? 20 : mc.level.tickRateManager().tickrate();
        if (rate != 20) {
            String text = "Speed ×"
                    + (rate / 20 == MatchControl.SLOW
                            ? "0.5"
                            : rate / 20 == MatchControl.FAST ? "1.5" : String.valueOf(rate / 20));
            HudRect r = layout().notices();
            int tw = font.width(text) + 2 * theme.padding();
            panel(g, new HudRect(r.right() - tw, r.bottom() + 2, tw, r.height()));
            g.drawString(font, text, r.right() - tw + theme.padding(), r.bottom() + 4, theme.text(), false);
        }
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

    /**
     * The minimap (UI-11): the terrain image scaled into the HUD's square, buildings and units as dots in their
     * factions' colours, the latest alert as a red ring for its first seconds, and the camera's frame.
     */
    private void drawMinimap(GuiGraphics g, DeltaTracker delta) {
        MatchJoin join = StrategosClient.match();
        if (!shown() || join == null || !minimap.ready()) {
            return;
        }
        HudRect r = layout().minimap();
        panel(g, r);
        g.flush(); // the panel's fill is batched: drawn later, it would cover the image
        Minimap m = new Minimap(r, join.sizeX(), join.sizeZ());
        double[] o = m.origin();
        double s = m.scale();
        g.blit(
                RtsMinimap.TEXTURE,
                (int) Math.round(o[0]),
                (int) Math.round(o[1]),
                (int) Math.round(join.sizeX() * s),
                (int) Math.round(join.sizeZ() * s),
                0,
                0,
                join.sizeX(),
                join.sizeZ(),
                join.sizeX(),
                join.sizeZ());
        for (Minimap.Dot d : m.dots(
                StrategosClient.model().units(), StrategosClient.model().buildings(), StrategosClient.templates())) {
            int x0 = (int) Math.floor(d.x());
            int y0 = (int) Math.floor(d.y());
            g.fill(
                    x0,
                    y0,
                    x0 + Math.max(1, (int) Math.round(d.w())),
                    y0 + Math.max(1, (int) Math.round(d.h())),
                    theme.faction(d.faction()));
        }
        MatchNews alert = StrategosClient.model().alert();
        if (alert != null && clientTicks - alertAt < 200 && (clientTicks / 5) % 2 == 0) {
            double[] p = m.toMap(alert.x(), alert.z());
            g.renderOutline((int) p[0] - 4, (int) p[1] - 4, 9, 9, 0xFFFF3030);
        }
        CameraRig rig = mode.rig();
        View view = mode.view();
        if (rig != null && view != null) {
            double[] f = Minimap.frame(view, rig.pose().y() - rig.height(), join.originX(), join.originZ());
            double minX = Double.MAX_VALUE;
            double minY = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE;
            double maxY = -Double.MAX_VALUE;
            for (int k = 0; k < 4; k++) {
                double[] p = m.toMap(
                        Math.max(0, Math.min(join.sizeX(), f[2 * k])),
                        Math.max(0, Math.min(join.sizeZ(), f[2 * k + 1])));
                minX = Math.min(minX, p[0]);
                minY = Math.min(minY, p[1]);
                maxX = Math.max(maxX, p[0]);
                maxY = Math.max(maxY, p[1]);
            }
            g.renderOutline(
                    (int) minX,
                    (int) minY,
                    Math.max(2, (int) (maxX - minX)),
                    Math.max(2, (int) (maxY - minY)),
                    0xFFFFFFFF);
        }
    }

    /** An item at half size, 8 pixels, its corner at ({@code x}, {@code y}). */
    private void small(GuiGraphics g, String id, int x, int y) {
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
        panel(g, r);
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
                text(g, font, r, 0, name(templates, u.template()), theme.faction(u.faction()));
                text(
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
                    text(g, font, r, 2, state, theme.dimText());
                }
            }
            case BUILDING -> drawBuilding(g, font, templates, r, pad, java.util.Objects.requireNonNull(p.building()));
            default -> {}
        }
    }

    /** One building: its name and state, then its queue with the head's progress, or a field's crop. */
    private void drawBuilding(
            GuiGraphics g, Font font, TemplateSet templates, HudRect r, int pad, BuildingState.Entry b) {
        icon(g, font, templates, b.template(), SelectionPanel.icon(r, pad));
        text(g, font, r, 0, name(templates, b.template()), theme.faction(b.faction()));
        String state = switch (b.state()) {
            case 0 -> "Foundation";
            case 1 -> "Building " + b.progress() / 10 + " %";
            default -> Math.round(100.0 * b.health() / UnitHud.WHOLE) + " % HP";
        };
        text(g, font, r, 1, b.rally() ? state + " · rally set" : state, theme.text());
        if (b.fieldLeft() >= 0) {
            String crop = b.fieldRipening() > 0
                    ? "Ripening, " + (b.fieldRipening() + 19) / 20 + " s"
                    : b.fieldLeft() > 0 ? b.fieldLeft() / 1000 + " food left" : "Empty: reseed it";
            text(g, font, r, 2, crop, theme.dimText());
            return;
        }
        for (int k = 0; k < b.queue().size(); k++) {
            HudRect cell = SelectionPanel.queueCell(r, pad, k);
            g.fill(cell.x(), cell.y(), cell.right(), cell.bottom(), theme.border());
            icon(g, font, templates, b.queue().get(k), cell);
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
        panel(g, r);
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
                case RESEED -> item(g, "minecraft:wheat_seeds", cell);
                case BUILD_PAGE -> item(g, "minecraft:bricks", cell);
                case BACK -> item(g, "minecraft:arrow", cell);
                case STOP -> item(g, "minecraft:barrier", cell);
            }
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
        return text.toString();
    }

    private void text(GuiGraphics g, Font font, HudRect r, int line, String text, int colour) {
        int[] at = SelectionPanel.line(r, theme.padding(), line);
        String fitted = font.plainSubstrByWidth(text, r.right() - theme.padding() - at[0]);
        g.drawString(font, fitted, at[0], at[1], colour, false);
    }

    /** Template {@code template}'s icon centred in {@code cell}, or its name's first letter when it has none. */
    private void icon(GuiGraphics g, Font font, TemplateSet templates, int template, HudRect cell) {
        String id = CommandCard.icon(templates, template);
        if (id != null) {
            item(g, id, cell);
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

    private void item(GuiGraphics g, String id, HudRect cell) {
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

    private ItemStack stack(String id) {
        return icons.computeIfAbsent(id, k -> {
            ResourceLocation rl = ResourceLocation.tryParse(k);
            return rl == null ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.get(rl));
        });
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
        GroupBar b = bar;
        if (shown() && !b.slots().isEmpty()) {
            Font font = Minecraft.getInstance().font;
            HudRect r = layout().groupBar();
            int x = r.x();
            for (GroupBar.Slot slot : b.slots()) {
                String text = slot.digit() + ": " + slot.count();
                int w = font.width(text) + 2 * theme.padding();
                if (x + w > r.right()) {
                    break;
                }
                panel(g, new HudRect(x, r.y(), w, r.height()));
                g.drawString(
                        font,
                        text,
                        x + theme.padding(),
                        r.y() + (r.height() - font.lineHeight) / 2 + 1,
                        theme.text(),
                        false);
                x += w + 2;
            }
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
            panel(g, r);
            g.drawString(font, bar, r.x() + pad, r.y() + pad + 1, theme.text(), false);
        }
        if (shown() && b != null) {
            Font font = mc.font;
            String text = describe(b, StrategosClient.templates());
            int pad = theme.padding();
            double[] gui = cursorGui(mc);
            HudRect r = layout().tooltip(font.width(text) + 2 * pad, font.lineHeight + 2 * pad, gui[0], gui[1]);
            panel(g, r);
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
            panel(g, r);
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
        panel(g, r);
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

    private void panel(GuiGraphics g, HudRect r) {
        g.fill(r.x(), r.y(), r.right(), r.bottom(), theme.panel());
        g.renderOutline(r.x(), r.y(), r.width(), r.height(), theme.border());
    }
}
