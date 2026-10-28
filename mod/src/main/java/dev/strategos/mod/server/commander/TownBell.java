package dev.strategos.mod.server.commander;

import dev.strategos.core.protocol.RingBell;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.BuildingView;
import dev.strategos.mod.StrategosServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.BellBlock;

/**
 * The town bell in first person (BLD-16): a right click on a bell block standing in a finished building of the match the
 * player is seated in rings that building's bell, as the card's button does, by a {@code RingBell} command the core
 * checks like any other (another faction's building refuses it). The vanilla bell's own ring is cancelled by the caller:
 * the bell's {@code fx} sounds it for everyone who sees the cell, once. A player rings at most once a second. Server
 * thread only.
 */
public final class TownBell {
    private TownBell() {}

    /**
     * The handle of the finished building of {@code player}'s match whose footprint holds bell block {@code pos}, or 0
     * for a block that is no bell, a player not seated, or a bell outside every building.
     */
    public static int buildingAt(ServerPlayer player, BlockPos pos) {
        StrategosServer server = StrategosServer.getOrNull();
        if (server == null || !(player.serverLevel().getBlockState(pos).getBlock() instanceof BellBlock)) {
            return 0;
        }
        var seat = server.matches().seatOf(player.getUUID());
        if (seat == null || !seat.match().space().contains(pos) || seat.match().core() == null) {
            return 0;
        }
        var space = seat.match().space();
        int x = (int) Math.floor(space.localX(pos.getX()));
        int z = (int) Math.floor(space.localZ(pos.getZ()));
        for (BuildingView b : seat.match().core().queries().buildings()) {
            var at = b.footprint();
            if (b.state() == BuildingStore.COMPLETE
                    && x >= at.anchorX()
                    && x < at.anchorX() + at.spanX()
                    && z >= at.anchorZ()
                    && z < at.anchorZ() + at.spanZ()
                    && pos.getY() >= b.baseY()) {
                return b.handle();
            }
        }
        return 0;
    }

    /** Rings the bell of building {@code building} for {@code player}, unless it rang within the last second. */
    public static void ring(ServerPlayer player, int building) {
        StrategosServer server = StrategosServer.getOrNull();
        if (server == null) {
            return;
        }
        var seat = server.matches().seatOf(player.getUUID());
        if (seat == null) {
            return;
        }
        if (!server.commanders().mayRing(player, player.serverLevel().getGameTime())) {
            return;
        }
        try {
            seat.match().submit(seat.slot(), new RingBell(building));
        } catch (dev.strategos.mod.server.match.MatchRefusedException e) {
            // a match that takes no commands rings no bell
        }
    }
}
