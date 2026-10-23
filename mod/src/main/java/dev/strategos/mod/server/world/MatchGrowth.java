package dev.strategos.mod.server.world;

import dev.strategos.mod.StrategosServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;

/**
 * Inside a match, plants grow only as the core says (ECO-05, 04 §11.9): a bush's age shows what its node holds, so a
 * random tick may not change it. {@code CropGrowEvent.Pre}, posted by {@code SweetBerryBushBlock#randomTick} and
 * {@code CropBlock#randomTick} through {@code CommonHooks#canCropGrow} (CommonHooks.java:919), is denied anywhere in
 * a match's space; a plant outside every match grows as in vanilla.
 */
public final class MatchGrowth {
    private MatchGrowth() {}

    public static void onCropGrowPre(CropGrowEvent.Pre event) {
        StrategosServer server = StrategosServer.getOrNull();
        if (server != null
                && event.getLevel() instanceof ServerLevel level
                && server.matches().at(level, event.getPos()) != null) {
            event.setResult(CropGrowEvent.Pre.Result.DO_NOT_GROW);
        }
    }
}
