package dev.strategos.mod;

import dev.strategos.mod.registry.ModDataMaps;
import dev.strategos.mod.registry.ModEntityTypes;
import dev.strategos.mod.registry.ModPayloads;
import dev.strategos.mod.registry.ModTickets;
import dev.strategos.mod.server.body.BodyEvents;
import dev.strategos.mod.server.command.DebugCommands;
import dev.strategos.mod.server.command.StrategosCommands;
import dev.strategos.mod.server.tick.TickDriver;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.datamaps.DataMapsUpdatedEvent;

/** The common entry; it never references the client package, whose entry is {@code StrategosClient}. */
@Mod(Strategos.MOD_ID)
public final class Strategos {
    public static final String MOD_ID = "strategos";

    /**
     * FML injects the mod bus and the container (FMLModContainer#constructMod); the server's lifecycle, ticks and entity
     * events come from the game bus.
     */
    public Strategos(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, StrategosConfig.SPEC, StrategosConfig.FILE);
        ModEntityTypes.register(modBus);
        ModDataMaps.register(modBus);
        ModTickets.register(modBus);
        ModPayloads.register(modBus);
        IEventBus game = NeoForge.EVENT_BUS;
        game.addListener(ServerAboutToStartEvent.class, StrategosServer::onAboutToStart);
        game.addListener(ServerStartedEvent.class, StrategosServer::onStarted);
        game.addListener(ServerStoppingEvent.class, StrategosServer::onStopping);
        game.addListener(ServerStoppedEvent.class, StrategosServer::onStopped);
        game.addListener(ServerTickEvent.Pre.class, TickDriver::onServerTickPre);
        game.addListener(ServerTickEvent.Post.class, StrategosServer::onTickPost);
        game.addListener(PlayerEvent.PlayerLoggedInEvent.class, StrategosServer::onLoggedIn);
        game.addListener(PlayerEvent.PlayerLoggedOutEvent.class, StrategosServer::onLoggedOut);
        game.addListener(LevelTickEvent.Pre.class, TickDriver::onLevelTickPre);
        game.addListener(EntityJoinLevelEvent.class, BodyEvents::onJoin);
        game.addListener(EntityLeaveLevelEvent.class, BodyEvents::onLeave);
        game.addListener(RegisterCommandsEvent.class, StrategosCommands::onRegister);
        game.addListener(RegisterCommandsEvent.class, DebugCommands::onRegister);
        game.addListener(DataMapsUpdatedEvent.class, StrategosServer::onDataMapsUpdated);
        game.addListener(TagsUpdatedEvent.class, StrategosServer::onTagsUpdated);
    }
}
