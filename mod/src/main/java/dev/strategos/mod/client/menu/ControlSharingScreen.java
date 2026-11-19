package dev.strategos.mod.client.menu;

import dev.strategos.core.client.hud.ControlSharing;
import dev.strategos.core.protocol.MatchStateMsg;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.mod.client.StrategosClient;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Explicit named-ally grant/revoke requests through the existing sequenced command producer. */
public final class ControlSharingScreen extends Screen {
    private final @Nullable Screen parent;
    private ControlSharing shown = new ControlSharing(List.of());
    private List<ControlSharing.Row> visible = List.of();
    private int page;
    private int left;
    private int panelWidth;
    private int rowsPerPage;
    private @Nullable String status;

    public ControlSharingScreen(@Nullable Screen parent) {
        super(text("title"));
        this.parent = parent;
    }

    private static Component text(String key) {
        return Component.translatable("strategos.control_sharing." + key);
    }

    private static ControlSharing model() {
        var joined = StrategosClient.match();
        var state = StrategosClient.state();
        boolean active = joined != null
                && state != null
                && state.matchId() == joined.matchId()
                && (state.lifecycle() == MatchStateMsg.Lifecycle.RUNNING
                        || state.lifecycle() == MatchStateMsg.Lifecycle.PAUSED);
        return ControlSharing.of(joined, StrategosClient.presentation(), active);
    }

    @Override
    protected void init() {
        shown = model();
        panelWidth = Math.min(440, width - 24);
        left = (width - panelWidth) / 2;
        rowsPerPage = Math.max(1, Math.min(6, (height - 132) / 24));
        int pages = Math.max(1, (shown.rows().size() + rowsPerPage - 1) / rowsPerPage);
        page = Math.min(page, pages - 1);
        int from = page * rowsPerPage;
        visible = shown.rows().subList(from, Math.min(shown.rows().size(), from + rowsPerPage));
        for (int i = 0; i < visible.size(); i++) {
            var row = visible.get(i);
            int y = 72 + i * 24;
            addRenderableWidget(Button.builder(text("allow"), b -> request(row.player(), true))
                    .bounds(left + panelWidth - 172, y, 82, 20)
                    .build());
            addRenderableWidget(Button.builder(text("revoke"), b -> request(row.player(), false))
                    .bounds(left + panelWidth - 86, y, 82, 20)
                    .build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(width / 2 - 50, height - 28, 100, 20)
                .build());
        if (pages > 1) {
            var previous = addRenderableWidget(Button.builder(text("previous"), b -> {
                        page--;
                        rebuildWidgets();
                    })
                    .bounds(left, height - 28, 82, 20)
                    .build());
            previous.active = page > 0;
            var next = addRenderableWidget(Button.builder(text("next"), b -> {
                        page++;
                        rebuildWidgets();
                    })
                    .bounds(left + panelWidth - 82, height - 28, 82, 20)
                    .build());
            next.active = page + 1 < pages;
        }
    }

    private void request(PlayerSlot recipient, boolean granted) {
        try {
            status = StrategosClient.rts().send(model().intent(recipient, granted)) ? "sent" : "unavailable";
        } catch (IllegalArgumentException stale) {
            status = "unavailable";
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!shown.equals(model())) rebuildWidgets();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(left - 6, 10, left + panelWidth + 6, height - 38, 0xD0101418);
        graphics.drawCenteredString(font, title, width / 2, 18, 0xFFFFFF);
        graphics.drawWordWrap(font, text("description"), left + 4, 36, panelWidth - 8, 0xCDD5DD);
        if (visible.isEmpty()) graphics.drawWordWrap(font, text("empty"), left + 4, 78, panelWidth - 8, 0xA8B4C0);
        for (int i = 0; i < visible.size(); i++) {
            var row = visible.get(i);
            graphics.drawString(
                    font,
                    font.plainSubstrByWidth(row.name(), panelWidth - 184),
                    left + 4,
                    78 + i * 24,
                    StrategosClient.colour(row.faction()),
                    false);
        }
        if (status != null)
            graphics.drawString(font, text(status), left + 4, 78 + Math.max(1, visible.size()) * 24, 0xCDD5DD, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
