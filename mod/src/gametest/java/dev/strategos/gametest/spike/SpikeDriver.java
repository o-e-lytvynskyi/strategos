package dev.strategos.gametest.spike;

import dev.strategos.mod.Strategos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a spike on the self-terminating dedicated server (spec 8.3) when {@code strategos.spike} is {@code s1},
 * {@code s2}, {@code s7}, {@code s8}, {@code s9} or {@code s12}: the world rules of an arena (04 §3.2), ticks back to back, then the spike until it is done, its report,
 * {@code DONE} and {@code halt}. A 40-minute deadline writes what it has and halts without {@code DONE}.
 */
@EventBusSubscriber(modid = Strategos.MOD_ID)
public final class SpikeDriver {
    private static final Logger LOG = LoggerFactory.getLogger(SpikeDriver.class);
    private static final long DEADLINE_NANOS = 40L * 60 * 1_000_000_000;

    /** The running spike; gametest code, so static state is fine. Server thread only. */
    private static @Nullable Running running;

    private record Running(MinecraftServer server, Spike spike, long startNanos) {}

    private SpikeDriver() {}

    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        String id = System.getProperty("strategos.spike", "");
        if (id.isBlank()) {
            return;
        }
        MinecraftServer server = event.getServer();
        for (ServerLevel level : server.getAllLevels()) {
            level.noSave = true;
        }
        arenaRules(server);
        server.getGameRules().getRule(GameRules.RULE_MAX_ENTITY_CRAMMING).set(0, server);
        if (!id.equals("s9")) { // S9's client plays at 20 ticks a second, as a player would
            server.tickRateManager().requestGameToSprint(Integer.MAX_VALUE);
        }
        Spike spike = switch (id) {
            case "s1" -> new S1Run(server);
            case "s2" -> new S2ArenaCost(server);
            case "s8" -> new S8Census(server);
            case "s7" -> new S7PathLoad(server);
            case "s12" -> new S12ContentCycle(server);
            case "s9" -> new S9Camera(server);
            default -> throw new IllegalArgumentException("no spike " + id);
        };
        running = new Running(server, spike, System.nanoTime());
    }

    /** The arena's rules (04 §3.2): no daylight or weather cycle, no mob spawning, fire, griefing or random ticks. */
    static void arenaRules(MinecraftServer server) {
        GameRules rules = server.getGameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        rules.getRule(GameRules.RULE_DOFIRETICK).set(false, server);
        rules.getRule(GameRules.RULE_MOBGRIEFING).set(false, server);
        rules.getRule(GameRules.RULE_RANDOMTICKING).set(0, server);
    }

    @SubscribeEvent
    static void onServerTickPre(ServerTickEvent.Pre event) {
        Running r = running;
        if (r != null) {
            r.spike().preTick();
        }
    }

    @SubscribeEvent
    static void onServerTickPost(ServerTickEvent.Post event) {
        Running r = running;
        if (r == null) {
            return;
        }
        boolean late = System.nanoTime() - r.startNanos() > DEADLINE_NANOS;
        if (late) {
            LOG.error("Spike deadline of 40 minutes: writing what there is");
        }
        if (late || r.spike().postTick()) {
            running = null;
            r.spike().finish(!late);
            r.server().halt(false); // never true: this handler runs on the server thread
        }
    }
}
