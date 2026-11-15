package dev.strategos.mod.client.menu;

import dev.strategos.core.protocol.SkirmishSetup;
import dev.strategos.mod.client.StrategosClient;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The skirmish setup (MATCH-02, spec §2.11): the map, the civilisation (the one there is, shown), the computer's level
 * with its handicaps, the starting resources, the population cap and the visibility (MATCH-07). The defaults are
 * {@link SkirmishSetup#DEFAULTS}, so Skirmish then Start is two clicks; Start opens the arena world and the setup goes
 * to the server once it is joined.
 */
public final class SkirmishScreen extends Screen {
    private static final int ROW = 24;
    private static final int WIDTH = 220;

    private final Screen parent;
    private String map = SkirmishSetup.DEFAULTS.map();
    private SkirmishSetup.Level level = SkirmishSetup.DEFAULTS.level();
    private SkirmishSetup.StartResources resources = SkirmishSetup.DEFAULTS.resources();
    private int population = SkirmishSetup.DEFAULTS.populationCap();
    private SkirmishSetup.Visibility visibility = SkirmishSetup.DEFAULTS.visibility();

    public SkirmishScreen(Screen parent) {
        super(Component.translatable("strategos.skirmish.title"));
        this.parent = parent;
    }

    /** The setup as chosen now. */
    SkirmishSetup setup() {
        return new SkirmishSetup(map, level, resources, population, visibility);
    }

    @Override
    protected void init() {
        int x = width / 2 - WIDTH / 2;
        int y = height / 6 + 12;
        addRenderableWidget(CycleButton.<String>builder(SkirmishScreen::mapName)
                .withValues(List.of(SkirmishSetup.FIRST_SKIRMISH))
                .withInitialValue(map)
                .create(x, y, WIDTH, 20, Component.translatable("strategos.skirmish.map"), (b, v) -> map = v));
        y += ROW * 2;
        addRenderableWidget(CycleButton.<SkirmishSetup.Level>builder(
                        v -> Component.translatable("strategos.skirmish.level." + key(v)))
                .withValues(SkirmishSetup.Level.values())
                .withInitialValue(level)
                .withTooltip(v ->
                        Tooltip.create(Component.translatable("strategos.skirmish.level." + key(v) + ".handicaps")))
                .create(x, y, WIDTH, 20, Component.translatable("strategos.skirmish.level"), (b, v) -> level = v));
        y += ROW;
        addRenderableWidget(CycleButton.<SkirmishSetup.StartResources>builder(
                        v -> Component.translatable("strategos.skirmish.resources." + key(v)))
                .withValues(SkirmishSetup.StartResources.values())
                .withInitialValue(resources)
                .create(
                        x,
                        y,
                        WIDTH,
                        20,
                        Component.translatable("strategos.skirmish.resources"),
                        (b, v) -> resources = v));
        y += ROW;
        addRenderableWidget(new PopulationSlider(x, y, WIDTH));
        y += ROW;
        addRenderableWidget(CycleButton.<SkirmishSetup.Visibility>builder(
                        v -> Component.translatable("strategos.skirmish.visibility." + key(v)))
                .withValues(SkirmishSetup.Visibility.values())
                .withInitialValue(visibility)
                .create(
                        x,
                        y,
                        WIDTH,
                        20,
                        Component.translatable("strategos.skirmish.visibility"),
                        (b, v) -> visibility = v));
        y += ROW * 2;
        addRenderableWidget(Button.builder(
                        Component.translatable("strategos.skirmish.start"),
                        b -> StrategosClient.arena().launch(Minecraft.getInstance(), this, setup()))
                .bounds(x, y, WIDTH / 2 - 2, 20)
                .build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(x + WIDTH / 2 + 2, y, WIDTH / 2 - 2, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
        g.drawCenteredString(
                font,
                Component.translatable("strategos.skirmish.civilisation"),
                width / 2,
                height / 6 + 12 + ROW + 6,
                0xA0A0A0);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    private static Component mapName(String id) {
        return Component.translatable("strategos.map." + id.substring(id.indexOf(':') + 1));
    }

    private static String key(Enum<?> e) {
        return e.name().toLowerCase(Locale.ROOT);
    }

    /** The population cap, 50 to 200 in steps of 10 (D-01). */
    private final class PopulationSlider extends AbstractSliderButton {
        private static final int STEP = 10;

        PopulationSlider(int x, int y, int width) {
            super(x, y, width, 20, Component.empty(), toValue(population));
            updateMessage();
        }

        private static double toValue(int cap) {
            return (double) (cap - SkirmishSetup.MIN_POPULATION)
                    / (SkirmishSetup.MAX_POPULATION - SkirmishSetup.MIN_POPULATION);
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("strategos.skirmish.population", population));
        }

        @Override
        protected void applyValue() {
            int span = SkirmishSetup.MAX_POPULATION - SkirmishSetup.MIN_POPULATION;
            int raw = SkirmishSetup.MIN_POPULATION + (int) Math.round(value * span);
            population = Mth.clamp(
                    Math.round(raw / (float) STEP) * STEP, SkirmishSetup.MIN_POPULATION, SkirmishSetup.MAX_POPULATION);
        }
    }
}
