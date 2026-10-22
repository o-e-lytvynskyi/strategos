package dev.strategos.devtools;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;

/** QA-06.1's negative: enters a world and never finishes, so the watchdog must end the run with exit 1. */
final class StallScenario implements Scenario {
    @Override
    public String name() {
        return "stall";
    }

    @Override
    public void setup(IntegratedServer server, ScenarioContext ctx) {}

    @Override
    public boolean tick(Minecraft mc, ScenarioContext ctx, int tick) {
        return false;
    }
}
