package dev.strategos.devtools;

import net.minecraft.client.Minecraft;

/**
 * QA-19.4's client: it joins the spike server ({@code -Pspike=qa19}), asks for view distance 10 and stands where the
 * server puts it, a spectator, until the server has timed its connection and disconnects it. The verdict is the
 * server's report; this side only says it stayed connected for the window.
 */
final class PacketSentScenario implements Scenario {
    private int ticks;

    @Override
    public String name() {
        return "qa19";
    }

    @Override
    public String server() {
        return System.getProperty("strategos.scenario.server", "localhost:25598");
    }

    @Override
    public void joined(Minecraft mc, ScenarioContext ctx) {
        ScenarioWorld.renderDistance(mc, 10); // the mock viewer's distance; the harness's options say 5
        mc.options.broadcastOptions();
    }

    @Override
    public boolean tick(Minecraft mc, ScenarioContext ctx, int tick) {
        if (mc.level == null || mc.player == null) {
            ctx.result.addProperty("ticksConnected", ticks);
            ctx.result.addProperty("ok", ticks >= 1_600); // the server's settle and window
            return true;
        }
        ticks = tick;
        return false;
    }
}
