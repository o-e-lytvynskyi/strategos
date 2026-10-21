package dev.strategos.gametest.selftest;

import dev.strategos.mod.Strategos;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Deliberate failures for the harness self-tests, selected by {@code strategos.selftest}: the guards must catch each one
 * (verifyHarness, S5 checks 6 and 9).
 */
@EventBusSubscriber(modid = Strategos.MOD_ID)
public final class SelfTestHooks {
    private SelfTestHooks() {}

    /** {@code failModLoad}: a mod-loading error, which the GameTest server survives with exit code 0. */
    @SubscribeEvent
    static void onCommonSetup(FMLCommonSetupEvent event) {
        if ("failModLoad".equals(selftest())) {
            throw new IllegalStateException("self-test failModLoad: a deliberate mod-loading error");
        }
    }

    /** {@code crashOnStart}: a crash on start; {@code clientRef}: a client class on the dedicated server. */
    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        switch (selftest()) {
            case "crashOnStart" ->
                throw new IllegalStateException("self-test crashOnStart: a deliberate crash on start");
            case "clientRef" -> Minecraft.getInstance(); // NoClassDefFoundError on the server dist
            default -> {}
        }
    }

    private static String selftest() {
        return System.getProperty("strategos.selftest", "");
    }
}
