package dev.strategos.gametest.suites;

import dev.strategos.gametest.GameTestTickets;
import dev.strategos.gametest.StrategosTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;

/**
 * Run only with {@code -Pstrategos.selftest=ticketLeak}: passes while leaving one forced chunk behind, so the batch-end
 * check must record {@code batchend.selftest} (QA-04.6, S5 check 17).
 */
public final class TicketLeakSelfTest {
    private TicketLeakSelfTest() {}

    @StrategosTest(batch = "selftest")
    public static void leaksOneTicket(GameTestHelper h) {
        GameTestTickets.force(h.getLevel(), new ChunkPos(h.absolutePos(BlockPos.ZERO)));
        h.succeed();
    }
}
