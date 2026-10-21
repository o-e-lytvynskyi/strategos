package dev.strategos.mod.server.persist;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.MatchId;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.testkit.SimHarness;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

/** CORE-12 guard 1: a match blob loads, or is quarantined and written back unchanged; it never throws. */
class MatchDataTest {
    /** The harness's match id. */
    private static final MatchId ID = new MatchId(1);

    private static SimHarness walking() {
        SimHarness h = SimHarness.builder().build();
        int[] units = h.spawn(new FactionSlot(0), new Vec2d(8, 8), 6, 3.0);
        h.submit(new PlayerSlot(0), Move.replace(UnitSet.of(units), new Vec2d(24, 20)));
        h.runTicks(15);
        return h;
    }

    private static CompoundTag saved(SimHarness h, long epoch) {
        return MatchData.live(ID, h.core()::snapshot, () -> epoch).save(new CompoundTag(), RegistryAccess.EMPTY);
    }

    @Test
    void aLiveBlobLoadsAsAnEqualSnapshot() {
        SimHarness h = walking();

        MatchData loaded = MatchData.load(ID, saved(h, 7));

        assertThat(loaded.state()).isEqualTo(MatchData.State.LOADED);
        assertThat(loaded.loadedSnapshot()).isEqualTo(h.core().snapshot());
        assertThat(loaded.loadedEpoch()).isEqualTo(7);
        assertThat(loaded.quarantineReason()).isNull();
    }

    @Test
    void aTagWithWrongTypesIsQuarantinedAndWrittenBackUnchanged() {
        CompoundTag tag = saved(walking(), 3);
        tag.getCompound("snapshot").put("tick", StringTag.valueOf("five hundred"));

        MatchData quarantined = MatchData.load(ID, tag);

        assertThat(quarantined.state()).isEqualTo(MatchData.State.QUARANTINED);
        assertThat(quarantined.quarantineReason()).contains("does not decode");
        assertThat(quarantined.save(new CompoundTag(), RegistryAccess.EMPTY)).isEqualTo(tag);
        quarantined.setDirty();
        assertThat(quarantined.isDirty()).as("never rewritten").isFalse();
    }

    @Test
    void aBlobOfAnotherMatchOrVersionIsQuarantined() {
        CompoundTag tag = saved(walking(), 3);
        assertThat(MatchData.load(new MatchId(2), tag).state()).isEqualTo(MatchData.State.QUARANTINED);
        CompoundTag later = tag.copy();
        later.putInt("v", 2);
        assertThat(MatchData.load(ID, later).quarantineReason()).contains("blob version 2");
        assertThat(MatchData.load(ID, new CompoundTag()).quarantineReason()).contains("no version");
    }

    @Test
    void anAttachedBlobSavesItsMatchFromThenOn() {
        SimHarness h = walking();
        MatchData data = MatchData.load(ID, saved(h, 3));
        h.runTicks(10);

        data.attach(h.core()::snapshot, () -> 4);

        assertThat(data.state()).isEqualTo(MatchData.State.LIVE);
        MatchData again = MatchData.load(ID, data.save(new CompoundTag(), RegistryAccess.EMPTY));
        assertThat(again.loadedSnapshot()).isEqualTo(h.core().snapshot());
        assertThat(again.loadedEpoch()).isEqualTo(4);
    }

    @Test
    void aDisposedBlobIsNeverDirty() {
        MatchData data = MatchData.live(ID, walking().core()::snapshot, () -> 1);
        data.setDirty();

        data.dispose();
        data.setDirty();

        assertThat(data.state()).isEqualTo(MatchData.State.DISPOSED);
        assertThat(data.isDirty()).isFalse();
        assertThat(data.save(new CompoundTag(), RegistryAccess.EMPTY).isEmpty()).isTrue();
    }
}
