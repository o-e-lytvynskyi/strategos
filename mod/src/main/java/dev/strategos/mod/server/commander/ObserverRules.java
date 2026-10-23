package dev.strategos.mod.server.commander;

import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.entity.UnitEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The first-person observer's rules that vanilla's own events enforce (CAM-05, D-08, 04 §13.7): an observer cannot be
 * hurt, cannot attack a match body ({@code AttackEntityEvent}, posted from {@code Player#attack}, Player.java:1223),
 * and uses no block or entity, so it edits nothing; adventure mode already keeps it from breaking and placing. Its
 * other rules live elsewhere: no vision of its own and units passing through it, because a match's own players never
 * become avoidance agents ({@code AgentTracker}); standing only inside the space, in {@link CommanderService}.
 */
public final class ObserverRules {
    private ObserverRules() {}

    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && observes(player)) {
            event.setCanceled(true);
        }
    }

    public static void onAttack(AttackEntityEvent event) {
        if (event.getTarget() instanceof UnitEntity) {
            cancelFor(event.getEntity(), event);
        }
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        cancelFor(event.getEntity(), event);
    }

    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        cancelFor(event.getEntity(), event);
    }

    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        cancelFor(event.getEntity(), event);
    }

    private static void cancelFor(Player player, ICancellableEvent event) {
        if (player instanceof ServerPlayer server && observes(server)) {
            event.setCanceled(true);
        }
    }

    private static boolean observes(ServerPlayer player) {
        StrategosServer server = StrategosServer.getOrNull();
        return server != null && server.commanders().observes(player);
    }
}
