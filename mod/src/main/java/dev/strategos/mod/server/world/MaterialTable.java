package dev.strategos.mod.server.world;

import dev.strategos.core.sim.nav.Material;
import java.util.function.Function;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Every block state's material (ECO-02), from the {@code strategos:resource_yield} data map: a block's entry applies to
 * all its states. Immutable, so page jobs read it on any thread; rebuilt when the data maps reload, as the class table
 * is.
 */
public final class MaterialTable {
    private final @Nullable Material[] materials;

    private MaterialTable(@Nullable Material[] materials) {
        this.materials = materials;
    }

    public static MaterialTable build(Function<Block, @Nullable ResourceYield> yields) {
        @Nullable Material[] materials = new Material[Block.BLOCK_STATE_REGISTRY.size()];
        for (BlockState state : Block.BLOCK_STATE_REGISTRY) {
            ResourceYield y = yields.apply(state.getBlock());
            if (y != null) {
                materials[Block.BLOCK_STATE_REGISTRY.getId(state)] = y.material();
            }
        }
        return new MaterialTable(materials);
    }

    /** No materials at all. */
    public static MaterialTable none() {
        return new MaterialTable(new Material[Block.BLOCK_STATE_REGISTRY.size()]);
    }

    public @Nullable Material of(BlockState state) {
        return materials[Block.BLOCK_STATE_REGISTRY.getId(state)];
    }
}
