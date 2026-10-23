package dev.strategos.mod.registry;

import dev.strategos.mod.platform.Ids;
import dev.strategos.mod.server.world.NavClassOverride;
import dev.strategos.mod.server.world.ResourceYield;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/** The data maps on {@code minecraft:block} (04 §8.5): {@code strategos:nav_class} in M1, {@code strategos:resource_yield} in M3; NeoForge loads them. */
public final class ModDataMaps {
    public static final DataMapType<Block, NavClassOverride> NAV_CLASS = DataMapType.builder(
                    Ids.id("nav_class"), Registries.BLOCK, NavClassOverride.CODEC)
            .build();

    /**
     * What a block is to the economy (ECO-02): a log, leaves, rock, ore, a forage plant, a crop, and its yield. Synced to
     * clients, not required of them (ECO-05): a right click on such a block gathers there.
     */
    public static final DataMapType<Block, ResourceYield> RESOURCE_YIELD = DataMapType.builder(
                    Ids.id("resource_yield"), Registries.BLOCK, ResourceYield.CODEC)
            .synced(ResourceYield.CODEC, false)
            .build();

    private ModDataMaps() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterDataMapTypesEvent.class, event -> {
            event.register(NAV_CLASS);
            event.register(RESOURCE_YIELD);
        });
    }
}
