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
 * that is not a fence gate yet (still building, or fallen) is left alone. Server thread only.
 */
public final class GateDoorBlocks {
    /** The gap between passes, in ticks. */
    public static final int EVERY = 4;

    /** How high a gate's fence gates stand: the two lowest layers of its blueprint. */
    private static final int LAYERS = 2;

    private final Long2ObjectOpenHashMap<BlockState> asked = new Long2ObjectOpenHashMap<>();

    /** One pass: every gate of {@code doors} brought to what the core says; the caller makes it every {@link #EVERY} ticks. */
    public void apply(ServerLevel level, MatchSpace space, MatchId match, List<GateDoor> doors, BlockWriter writer) {
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
                    } else if (!want.equals(asked.get(pos.asLong()))) {
                        asked.put(pos.asLong(), want);
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
    }
}
