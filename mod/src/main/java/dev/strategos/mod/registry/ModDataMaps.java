package dev.strategos.mod.registry;

import dev.strategos.mod.platform.Ids;
import dev.strategos.mod.server.world.NavClassOverride;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/** The data maps on {@code minecraft:block} (04 §8.5): {@code strategos:nav_class} in M1; NeoForge loads them. */
public final class ModDataMaps {
    public static final DataMapType<Block, NavClassOverride> NAV_CLASS = DataMapType.builder(
                    Ids.id("nav_class"), Registries.BLOCK, NavClassOverride.CODEC)
            .build();

    private ModDataMaps() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterDataMapTypesEvent.class, event -> event.register(NAV_CLASS));
    }
}
