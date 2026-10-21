package dev.strategos.gametest;

import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.match.MatchSource;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityTypeTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * After each batch's cleanup, before vanilla releases its own forced chunks: nothing of the batch may be left — no
 * ticket held through {@link GameTestTickets}, no GameTest match, no unit body. A leftover is logged and recorded as
 * the failed case {@code batchend.<batch>}, then cleared, so the next batch starts clean (QA-04.6).
 */
public final class BatchEndCheck {
    private static final Logger LOG = LoggerFactory.getLogger(BatchEndCheck.class);

    private BatchEndCheck() {}

    public static void verify(ServerLevel level, String batch) {
        List<String> leftovers = new ArrayList<>();
        List<ChunkPos> tickets = GameTestTickets.held(level);
        if (!tickets.isEmpty()) {
            leftovers.add("tickets held for chunks " + tickets);
        }
        StrategosServer server = StrategosServer.getOrNull();
        if (server != null) {
            List<String> matches = new ArrayList<>();
            for (MatchRuntime rt : server.matches().all()) {
                if (rt.source() == MatchSource.GAMETEST && rt.space().level().equals(level.dimension())) {
                    matches.add(rt.id().toString());
                }
            }
            if (!matches.isEmpty()) {
                leftovers.add("GameTest matches " + matches);
            }
        }
        List<? extends UnitEntity> bodies = level.getEntities(EntityTypeTest.forClass(UnitEntity.class), e -> true);
        if (!bodies.isEmpty()) {
            leftovers.add(bodies.size() + " unit bodies, the first at " + bodies.get(0).blockPosition());
        }
        if (leftovers.isEmpty()) {
            return;
        }
        String message = "batch '" + batch + "' left " + String.join("; ", leftovers);
        LOG.error("Batch-end check: {}", message);
        JUnitXmlReporter.recordFailure("batchend", batch, message);
        GameTestTickets.releaseAll(level);
        for (UnitEntity body : List.copyOf(bodies)) {
            body.markRemovedByRegistry();
            body.discard();
        }
    }
}
