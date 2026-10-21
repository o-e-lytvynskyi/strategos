package dev.strategos.mod;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(Strategos.MOD_ID)
public final class Strategos {
    public static final String MOD_ID = "strategos";

    public Strategos(IEventBus modBus) {} // FML injects the mod bus; registrations arrive from T7a
}
