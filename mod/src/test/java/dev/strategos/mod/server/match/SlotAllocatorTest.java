package dev.strategos.mod.server.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.mod.server.persist.RegistryData;
import dev.strategos.mod.server.world.ArenaPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

/** MAP-07.5: new arena matches take slots 2,048 blocks apart, chunk-aligned, never reused (spec §1.6 D row 14). */
class SlotAllocatorTest {
    @Test
    void slotsAre2048BlocksApartOnChunkCorners() {
        for (int slot = 0; slot < 8; slot++) {
            assertThat(SlotAllocator.originX(slot)).isEqualTo(slot * 2_048);
            assertThat(SlotAllocator.originX(slot) % 16).as("a chunk corner").isZero();
        }
        assertThat(SlotAllocator.originX(1) - SlotAllocator.originX(0)).isEqualTo(SlotAllocator.SPACING);
    }

    @Test
    void theLastSlotsLargestSpaceEndsInsideTheWorldBorder() {
        int lastEnd = SlotAllocator.originX(SlotAllocator.LAST_SLOT) + MatchRegistry.MAX_SIZE;

        assertThat(lastEnd).isLessThanOrEqualTo(SlotAllocator.WORLD_EDGE);
        assertThat(lastEnd + SlotAllocator.SPACING).isGreaterThan(SlotAllocator.WORLD_EDGE);
        assertThatThrownBy(() -> SlotAllocator.originX(SlotAllocator.LAST_SLOT + 1))
                .isInstanceOf(MatchRefusedException.class);
        assertThatThrownBy(() -> SlotAllocator.originX(-1)).isInstanceOf(MatchRefusedException.class);
    }

    @Test
    void neighbouringSlotsNeverOverlapAtTheLargestSize() {
        MatchSpace first = ArenaPolicy.slotSpace(0, MatchRegistry.MAX_SIZE, MatchRegistry.MAX_SIZE);
        MatchSpace second = ArenaPolicy.slotSpace(1, MatchRegistry.MAX_SIZE, MatchRegistry.MAX_SIZE);

        assertThat(first.origin()).isEqualTo(new BlockPos(0, ArenaPolicy.MIN_Y, 0));
        assertThat(second.origin()).isEqualTo(new BlockPos(2_048, ArenaPolicy.MIN_Y, 0));
        assertThat(first.level()).isEqualTo(Level.OVERWORLD);
        assertThat(first.overlaps(second, MatchRegistry.MARGIN)).isFalse();
    }

    @Test
    void theRegistryNeverHandsOutASlotTwiceAcrossASave() {
        RegistryData data = new RegistryData();
        assertThat(data.allocateArenaSlot()).isZero();
        assertThat(data.allocateArenaSlot()).isEqualTo(1);

        RegistryData loaded =
                RegistryData.load(data.save(new CompoundTag(), RegistryAccess.EMPTY), RegistryAccess.EMPTY);

        assertThat(loaded.allocateArenaSlot()).isEqualTo(2);
    }
}
