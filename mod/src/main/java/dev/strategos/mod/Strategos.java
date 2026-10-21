package dev.strategos.mod;

import dev.strategos.mod.registry.ModEntityTypes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/** The common entry; it never references the client package, whose entry is {@code StrategosClient}. */
@Mod(Strategos.MOD_ID)
public final class Strategos {
    public static final String MOD_ID = "strategos";

    /** FML injects the mod bus; the game-bus listeners arrive with T7b. */
    public Strategos(IEventBus modBus) {
        ModEntityTypes.register(modBus);
    }
}
