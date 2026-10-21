package dev.strategos.mod;

import dev.strategos.mod.registry.ModEntityTypes;
import dev.strategos.mod.server.body.BodyEvents;
import dev.strategos.mod.server.command.StrategosCommands;
import dev.strategos.mod.server.tick.TickDriver;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** The common entry; it never references the client package, whose entry is {@code StrategosClient}. */
@Mod(Strategos.MOD_ID)
public final class Strategos {
    public static final String MOD_ID = "strategos";

    /** FML injects the mod bus; the server's lifecycle, ticks and entity events come from the game bus. */
    public Strategos(IEventBus modBus) {
        ModEntityTypes.register(modBus);
        IEventBus game = NeoForge.EVENT_BUS;
        game.addListener(ServerAboutToStartEvent.class, StrategosServer::onAboutToStart);
        game.addListener(ServerStartedEvent.class, StrategosServer::onStarted);
        game.addListener(ServerStoppingEvent.class, StrategosServer::onStopping);
        game.addListener(ServerStoppedEvent.class, StrategosServer::onStopped);
        game.addListener(ServerTickEvent.Pre.class, TickDriver::onServerTickPre);
        game.addListener(LevelTickEvent.Pre.class, TickDriver::onLevelTickPre);
        game.addListener(EntityJoinLevelEvent.class, BodyEvents::onJoin);
        game.addListener(EntityLeaveLevelEvent.class, BodyEvents::onLeave);
        game.addListener(RegisterCommandsEvent.class, StrategosCommands::onRegister);
    }
}
