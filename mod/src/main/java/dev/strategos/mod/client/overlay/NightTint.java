package dev.strategos.mod.client.overlay;

import dev.strategos.core.protocol.MatchStateMsg;
import dev.strategos.core.sim.DayClock;
import dev.strategos.mod.client.StrategosClient;
import dev.strategos.mod.platform.Ids;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * Night as a client-side tint (MATCH-16): when the server says the match's night may not be the level's own (more than
 * one match in the level, so the level's time stays as it is), a dark blue veil over the world, drawn below every other
 * layer, whose strength is {@link DayClock#darkness} of the match tick the client already has. Nothing is drawn by day,
 * with the rule off or the match not running.
 */
public final class NightTint {
    public static final ResourceLocation LAYER = Ids.id("night_tint");

    /** The veil's strongest alpha: the world stays readable, which an RTS needs. */
    static final int MAX_ALPHA = 0x70;

    private static final int RGB = 0x050A28;

    private NightTint() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterGuiLayersEvent.class, e -> e.registerBelowAll(LAYER, NightTint::draw));
    }

    /** The veil's alpha, 0 to {@link #MAX_ALPHA}, for a match in {@code state} at {@code tick}. */
    static int alpha(MatchStateMsg state, long tick) {
        if (!state.tint() || state.lifecycle() != MatchStateMsg.Lifecycle.RUNNING) {
            return 0;
        }
        return (int) Math.round(DayClock.darkness(tick) * MAX_ALPHA);
    }

    private static void draw(GuiGraphics g, DeltaTracker delta) {
        MatchStateMsg state = StrategosClient.state();
        if (state == null) {
            return;
        }
        int alpha = alpha(state, StrategosClient.model().tick());
        if (alpha > 0) {
            g.fill(0, 0, g.guiWidth(), g.guiHeight(), (alpha << 24) | RGB);
        }
    }
}
