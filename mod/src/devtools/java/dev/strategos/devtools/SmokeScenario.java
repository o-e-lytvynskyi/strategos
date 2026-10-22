package dev.strategos.devtools;

import com.mojang.blaze3d.platform.GlUtil;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerPlayer;

/** S3's smoke (QA-06.1): five units at the player, a fixed camera, three PNGs and the window's facts. */
final class SmokeScenario implements Scenario {
    @Override
    public String name() {
        return "smoke";
    }

    @Override
    public void setup(IntegratedServer server, ScenarioContext ctx) {
        ServerPlayer player = ScenarioWorld.player(server);
        ScenarioWorld.matchAtPlayer(server, 16, 5, false);
        ScenarioWorld.camera(server, player.getX(), player.getY() + 6, player.getZ() - 6, 0, 45);
    }

    @Override
    public boolean tick(Minecraft mc, ScenarioContext ctx, int tick) {
        if (tick == 20 || tick == 40 || tick == 60) {
            ctx.capture("smoke-" + tick);
        }
        if (tick < 62) {
            return false;
        }
        Window window = mc.getWindow();
        ctx.result.addProperty("framebufferWidth", window.getWidth());
        ctx.result.addProperty("framebufferHeight", window.getHeight());
        ctx.result.addProperty("guiScale", window.getGuiScale());
        ctx.result.addProperty("glRenderer", GlUtil.getRenderer());
        if (mc.player != null) {
            ctx.result.addProperty(
                    "camera",
                    mc.player.getX() + " " + mc.player.getY() + " " + mc.player.getZ() + " " + mc.player.getYRot() + " "
                            + mc.player.getXRot());
        }
        ctx.result.addProperty("ok", true);
        return true;
    }
}
