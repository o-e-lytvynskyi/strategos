package dev.strategos.core.sim.build;

import dev.strategos.core.shared.CoreApi;
import java.util.List;

/**
 * A finished palisade gate as the mod draws it (BLD-13): the building, whether its door stands open now (an allied unit
 * within {@link dev.strategos.core.sim.nav.GateDoors#RANGE} blocks), the block its lowest layer stands at, its
 * footprint's rotation (the way its fence gates face) and its two gate columns, {@code x << 16 | z} match-local.
 */
@CoreApi
public record GateDoor(int building, boolean open, int baseY, int rotation, List<Integer> columns) {
    public GateDoor {
        columns = List.copyOf(columns);
    }
}
