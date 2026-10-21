package dev.strategos.mod.client;

import dev.strategos.mod.Strategos;
import dev.strategos.mod.registry.ModEntityTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** The client entry, loaded only on a client; it holds no state in M0. */
@Mod(value = Strategos.MOD_ID, dist = Dist.CLIENT)
public final class StrategosClient {
    public StrategosClient(IEventBus modBus) {
        modBus.addListener(
                EntityRenderersEvent.RegisterRenderers.class,
                e -> e.registerEntityRenderer(ModEntityTypes.UNIT_HUMANOID.get(), UnitRenderer::new));
    }
}
