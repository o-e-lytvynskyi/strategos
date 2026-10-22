package dev.strategos.devtools;

import com.mojang.blaze3d.platform.GlUtil;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import org.lwjgl.glfw.GLFW;

/** S3's smoke (QA-06.1): five units at the player, a fixed camera, three PNGs and the window's facts. */
final class SmokeScenario implements Scenario {
    @Override
    public String name() {
        return "smoke";
    }

    @Override
    public void setup(IntegratedServer server, ScenarioContext ctx) {
        BlockPos anchor = ScenarioWorld.anchor(server);
        ScenarioWorld.matchAtAnchor(server, 16, 5, false);
        ScenarioWorld.camera(server, anchor.getX() + 0.5, anchor.getY() + 6, anchor.getZ() - 5.5, 0, 45);
        ctx.holdCamera(anchor.getX() + 0.5, anchor.getY() + 6, anchor.getZ() - 5.5, 0, 45);
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
        float[] scaleX = new float[1];
        float[] scaleY = new float[1];
        GLFW.glfwGetWindowContentScale(window.getWindow(), scaleX, scaleY);
        ctx.result.addProperty("windowFocused", GLFW.glfwGetWindowAttrib(window.getWindow(), GLFW.GLFW_FOCUSED) != 0);
        ctx.result.addProperty("contentScale", scaleX[0]);
        ctx.result.addProperty("windowWidth", window.getScreenWidth());
        ctx.result.addProperty("windowHeight", window.getScreenHeight());
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
