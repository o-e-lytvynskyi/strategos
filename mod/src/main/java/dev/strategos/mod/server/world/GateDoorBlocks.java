package dev.strategos.mod.server.world;

import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.MatchId;
import dev.strategos.core.sim.build.GateDoor;
import dev.strategos.core.sim.world.WritePriority;
import dev.strategos.core.sim.world.WriteSite;
import dev.strategos.mod.server.match.MatchSpace;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A palisade gate's fence gates follow the core's door state (BLD-13, M9 spec §2.10): open while an allied unit is near,
 * shut otherwise, and turned to face along the wall's run (the blueprint is written unturned). The blocks are the
 * picture only: what may cross is the core's. A gate's blocks are read where they stand and written through the {@link
 * BlockWriter} only when they differ from what is wanted, and not again while that write waits in its queue; a block
 * that is not a fence gate yet (still building, or fallen) is left alone. A write asked for is remembered for {@value
 * #RETRY} passes, then asked again in case the cosmetic write was dropped; what a pass does not reach (a gate gone from
 * the doors) is forgotten. Server thread only.
 */
public final class GateDoorBlocks {
    /** The gap between passes, in ticks. */
    public static final int EVERY = 4;

    /** How high a gate's fence gates stand: the two lowest layers of its blueprint. */
    private static final int LAYERS = 2;

    /** How many passes a write is taken as still queued before it is asked for again. */
    static final int RETRY = 20;

    /** What was asked for a block, and the pass it was asked in; {@code seen} is the last pass that reached the block. */
    private static final class Asked {
        final BlockState want;
        final int since;
        int seen;

        Asked(BlockState want, int since) {
            this.want = want;
            this.since = since;
            this.seen = since;
        }
    }

    private final Long2ObjectOpenHashMap<Asked> asked = new Long2ObjectOpenHashMap<>();
    private int pass;

    /** One pass: every gate of {@code doors} brought to what the core says; the caller makes it every {@link #EVERY} ticks. */
    public void apply(ServerLevel level, MatchSpace space, MatchId match, List<GateDoor> doors, BlockWriter writer) {
        pass++;
        if (doors.isEmpty()) {
            asked.clear();
            return;
        }
        for (GateDoor door : doors) {
            Direction facing = door.rotation() % 2 == 0 ? Direction.NORTH : Direction.EAST;
            for (int column : door.columns()) {
                for (int layer = 0; layer < LAYERS; layer++) {
                    BlockPos pos = new BlockPos(
                            space.origin().getX() + (column >>> 16),
                            door.baseY() + layer,
                            space.origin().getZ() + (column & 0xFFFF));
                    BlockState now = level.getBlockState(pos);
                    if (!(now.getBlock() instanceof FenceGateBlock)) {
                        asked.remove(pos.asLong());
                        continue;
                    }
                    BlockState want =
                            now.setValue(FenceGateBlock.OPEN, door.open()).setValue(FenceGateBlock.FACING, facing);
                    if (want.equals(now)) {
                        asked.remove(pos.asLong()); // it stands as wanted: a later change asks again
                    } else if (stale(pos.asLong(), want)) {
                        asked.put(pos.asLong(), new Asked(want, pass));
                        writer.enqueue(
                                level,
                                new WriteSite(match.value(), Handle.index(door.building())),
                                WritePriority.COSMETIC,
                                pos,
                                want,
                                door.building());
                    }
                }
            }
        }
        asked.values().removeIf(a -> a.seen != pass); // blocks of gates that are gone
    }

    /** Whether {@code want} is to be asked for at {@code key} now: not asked yet, or asked {@value #RETRY} passes ago. */
    private boolean stale(long key, BlockState want) {
        Asked a = asked.get(key);
        if (a != null) {
            a.seen = pass;
        }
        return a == null || !a.want.equals(want) || pass - a.since >= RETRY;
    }
}
