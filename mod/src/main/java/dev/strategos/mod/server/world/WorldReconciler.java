package dev.strategos.mod.server.world;

import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.WorldEffect;
import dev.strategos.mod.server.match.MatchRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A restored match's world made the core's again (MATCH-04, ADR-0018): the world was saved at another moment than the
 * match's blob, after a crash say, so every block the core owns ({@code worldClaims}: its buildings as built and staged,
 * the blocks its felling and quarrying took) is put back as the core holds it, at once and outside the writer's
 * budget, before the terrain is captured again; and a building's blocks are protected again, the index being the
 * server's memory. Every other difference is the world's: the capture that follows publishes it in the match's first
 * tick, journaled as any terrain change.
 */
public final class WorldReconciler {
    private WorldReconciler() {}

    /** What a reconcile did: the blocks rewritten and the blocks protected. */
    public record Result(int rewritten, int protectedBlocks) {}

    public static Result reconcile(BlockWriter writer, ServerLevel level, MatchRuntime rt, MatchCore core) {
        ProtectedBlockIndex index = writer.protectedBlocks(level);
        BlockPos origin = rt.space().origin();
        int rewritten = 0;
        int kept = 0;
        for (WorldEffect claim : core.queries().worldClaims()) {
            BlockState want = writer.state(claim.state());
            if (want == null) {
                continue;
            }
            BlockPos pos = new BlockPos(origin.getX() + claim.x(), claim.y(), origin.getZ() + claim.z());
            if (!level.getBlockState(pos).equals(want)) {
                writer.restore(level, pos, want);
                rewritten++;
            }
            if (claim.owner() != 0 && !want.isAir()) {
                index.protect(pos, rt.id().value(), claim.owner());
                kept++;
            } else {
                index.release(pos);
            }
        }
        return new Result(rewritten, kept);
    }
}
