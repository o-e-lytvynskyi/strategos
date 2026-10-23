package dev.strategos.mod.client.hud;

import dev.strategos.core.client.hud.GroupBar;
import dev.strategos.core.client.hud.HudLayout;
import dev.strategos.core.client.hud.HudRect;
import dev.strategos.core.client.hud.HudTheme;
import dev.strategos.core.client.hud.SelectionSummary;
import dev.strategos.core.client.pick.Picking;
import dev.strategos.core.client.select.Selection;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.mod.client.StrategosClient;
import dev.strategos.mod.client.rts.RtsMode;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.entity.UnitSpawnData;
import dev.strategos.mod.platform.Ids;
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
 * The RTS HUD (UI-02): while RTS mode is on, three GUI layers above vanilla's draw the selection summary, the
 * control-group bar and the hover tooltip in {@link HudLayout}'s rectangles, and the vanilla hotbar, crosshair, health,
 * food, armour, air, experience and spectator layers are cancelled ({@code RenderGuiLayerEvent.Pre}). The view-models
 * are rebuilt once a client tick, after RTS mode's own tick; the layers only draw them. It times both, for PERF-14.
 */
public final class RtsHud {
    public static final ResourceLocation SELECTION = Ids.id("rts_selection");
    public static final ResourceLocation GROUPS = Ids.id("rts_groups");
    public static final ResourceLocation TOOLTIP = Ids.id("rts_tooltip");

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

    private final RtsMode mode;
    private final HudTheme theme = HudTheme.DEFAULT;
    private SelectionSummary summary = SelectionSummary.EMPTY;
    private GroupBar bar = new GroupBar(List.of());
    private @Nullable Tooltip tooltip;
    private long buildNanos;
    private long drawNanos;
    private long drawing;

    public RtsHud(RtsMode mode) {
        this.mode = mode;
    }

    public void register(IEventBus modBus) {
        modBus.addListener(RegisterGuiLayersEvent.class, e -> {
            e.registerAboveAll(SELECTION, this::drawSelection);
            e.registerAboveAll(GROUPS, this::drawGroups);
            e.registerAboveAll(TOOLTIP, this::drawTooltip);
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

    private void onLayer(RenderGuiLayerEvent.Pre event) {
        if (mode.active() && hidden.contains(event.getName())) {
            event.setCanceled(true);
        }
    }

    private void build() {
        long start = System.nanoTime();
        Minecraft mc = Minecraft.getInstance();
        if (!mode.active() || mc.level == null) {
            summary = SelectionSummary.EMPTY;
            bar = new GroupBar(List.of());
            tooltip = null;
            return;
        }
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
        bar = GroupBar.of(mode.groups());
        tooltip = hovered(mc, templates);
        buildNanos = System.nanoTime() - start;
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

    private static HudLayout layout() {
        Minecraft mc = Minecraft.getInstance();
        int scale = (int) Math.round(mc.getWindow().getGuiScale());
        return HudLayout.compute(mc.getWindow().getWidth(), mc.getWindow().getHeight(), Math.max(1, scale));
    }

    private void drawSelection(GuiGraphics g, DeltaTracker delta) {
        drawing = System.nanoTime();
        SelectionSummary s = summary;
        if (!mode.active() || (s.total() == 0 && s.inspected() == null)) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        HudRect r = layout().selectionPanel();
        panel(g, r);
        int pad = theme.padding();
        int line = font.lineHeight + 1;
        int y = r.y() + pad;
        if (s.total() == 0) {
            g.drawString(font, "Inspecting: " + s.inspected(), r.x() + pad, y, theme.text(), false);
            return;
        }
        g.drawString(font, s.total() + " selected", r.x() + pad, y, theme.accent(), false);
        y += line;
        for (SelectionSummary.TypeCount t : s.types()) {
            if (y + font.lineHeight > r.bottom() - pad) {
                break;
            }
            g.drawString(font, t.count() + " × " + t.name(), r.x() + pad, y, theme.text(), false);
            y += line;
        }
    }

    private void drawGroups(GuiGraphics g, DeltaTracker delta) {
        GroupBar b = bar;
        if (mode.active() && !b.slots().isEmpty()) {
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
        if (mode.active() && t != null) {
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

    private void panel(GuiGraphics g, HudRect r) {
        g.fill(r.x(), r.y(), r.right(), r.bottom(), theme.panel());
        g.renderOutline(r.x(), r.y(), r.width(), r.height(), theme.border());
    }
}
