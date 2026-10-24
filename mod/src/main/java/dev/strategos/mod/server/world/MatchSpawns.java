package dev.strategos.mod.server.world;

import dev.strategos.mod.StrategosServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

/**
 * No hostile mob spawns inside a match (MATCH-01, M3 spec §2.7): {@code MobSpawnEvent.PositionCheck}, posted for
 * natural, chunk-generation, patrol, structure and spawner spawns ({@code EventHooks#checkSpawnPosition}), fails for a
 * monster anywhere in a match's space; a command or a spawn egg still spawns one, and outside every match vanilla
 * decides. World game rules are never touched.
 */
public final class MatchSpawns {
    private MatchSpawns() {}

    public static void onPositionCheck(MobSpawnEvent.PositionCheck event) {
        StrategosServer server = StrategosServer.getOrNull();
        if (server == null
                || event.getSpawnType() == MobSpawnType.COMMAND
                || event.getSpawnType() == MobSpawnType.SPAWN_EGG
                || event.getEntity().getType().getCategory() != MobCategory.MONSTER
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (server.matches().at(level, BlockPos.containing(event.getX(), event.getY(), event.getZ())) != null) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }
}
