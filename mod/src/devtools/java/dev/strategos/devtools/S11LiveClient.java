package dev.strategos.devtools;

import dev.strategos.mod.Strategos;
import dev.strategos.mod.client.StrategosClient;
import dev.strategos.mod.client.StrategosClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** The dedicated S11 fixture's clients send an actual vanilla move every tick of the pan. */
@EventBusSubscriber(modid = Strategos.MOD_ID, value = Dist.CLIENT)
public final class S11LiveClient {
    private static int currentMatch;
    private static double x;
    private static double y;
    private static double z;
    private static double direction;
    private static int settle;

    private S11LiveClient() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void beforeMovement(PlayerTickEvent.Pre event) {
        if (!Boolean.getBoolean("strategos.s11.client") || !(event.getEntity() instanceof LocalPlayer local)) return;
        var match = StrategosClient.match();
        if (match == null) {
            currentMatch = 0;
            return;
        }
        if (match.matchId() != currentMatch) {
            currentMatch = match.matchId();
            settle = 20;
            x = match.originX() + match.sizeX() / 2.0;
            y = local.getY();
            z = match.originZ() + 143.5;
            direction = 2;
            StrategosClientConfig.INSTANCE.edgePan.set(false);
            ScenarioWorld.renderDistance(Minecraft.getInstance(), 12);
        }
        if (settle > 0) {
            settle--;
            y = local.getY();
            return;
        }
        double anchor = match.originX() + match.sizeX() / 2.0;
        if (match.originX() == S11Live.LANE_SPACING * S11Live.PAN_LANE) {
            if (Math.abs(x + direction - anchor) > 48) direction = -direction;
            x += direction;
        }
        local.setPos(x, y, z);
        var camera = StrategosClient.rts().rig();
        if (camera != null) camera.rebaseOnEye(x, y + local.getEyeHeight(), z);
    }
}
