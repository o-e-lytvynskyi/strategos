package dev.strategos.mod.server.persist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.MatchId;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.testkit.SimHarness;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
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
    void aFreshBlobUsesItsConfiguredFutureReservationWindow() {
        var h = walking();
        var data = MatchData.live(ID, h.core()::snapshot, () -> 7, 60_000_000_000L);
        assertThat(data.roster().reclaimWindowNanos()).isEqualTo(60_000_000_000L);
        assertThat(MatchData.live(ID, h.core()::snapshot, () -> 7).roster().reclaimWindowNanos())
                .isEqualTo(300_000_000_000L);
    }

    @Test
    void legacyMembershipGetsTheConfiguredWindowWithoutChangingTheSnapshot() {
        var h = walking();
        var tag = withSeat(saved(h, 7), 0);
        tag.putInt("v", 1);
        tag.getList("roster", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).remove("remainingNanos");
        var clock = new AtomicLong(-10_000);
        var data = MatchData.load(ID, tag, clock::get, 60_000_000_000L);
        assertThat(data.state()).isEqualTo(MatchData.State.LOADED);
        var member = data.roster().members().getFirst();
        assertThat(data.roster().isConnected(member)).isFalse();
        assertThat(data.roster().remainingNanos(member)).isEqualTo(60_000_000_000L);
        assertThat(data.roster().reclaimWindowNanos()).isEqualTo(60_000_000_000L);
        assertThat(data.loadedSnapshot()).isEqualTo(h.core().snapshot());
    }

    @Test
    void anExplicitSavedRemainingDurationIsNotClampedOrResetByANewWindow() {
        var h = walking();
        var tag = withSeat(saved(h, 7), 0);
        tag.getList("roster", net.minecraft.nbt.Tag.TAG_COMPOUND)
                .getCompound(0)
                .putLong("remainingNanos", 240_000_000_000L);
        var clock = new AtomicLong(10_000);
        var data = MatchData.load(ID, tag, clock::get, 60_000_000_000L);
        assertThat(data.state()).isEqualTo(MatchData.State.LOADED);
        var member = data.roster().members().getFirst();
        assertThat(data.roster().remainingNanos(member)).isEqualTo(240_000_000_000L);
        assertThat(data.roster().reclaimWindowNanos()).isEqualTo(60_000_000_000L);
        clock.addAndGet(17);
        assertThat(data.roster().remainingNanos(member)).isEqualTo(239_999_999_983L);
        var saved = data.save(new CompoundTag(), RegistryAccess.EMPTY);
        assertThat(saved.getList("roster", net.minecraft.nbt.Tag.TAG_COMPOUND)
                        .getCompound(0)
                        .getLong("remainingNanos"))
                .isEqualTo(239_999_999_983L);
        assertThat(data.loadedSnapshot()).isEqualTo(h.core().snapshot());
    }

    @Test
    void aLargerConfiguredWindowCannotReviveAnExplicitExpiredSave() {
        var tag = withSeat(saved(walking(), 7), 0);
        tag.getList("roster", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).putLong("remainingNanos", 0);
        var data = MatchData.load(ID, tag, () -> 10_000, 86_400_000_000_000L);
        assertThat(data.state()).isEqualTo(MatchData.State.LOADED);
        var member = data.roster().members().getFirst();
        assertThat(data.roster().isConnected(member)).isFalse();
        assertThat(data.roster().remainingNanos(member)).isZero();
        assertThat(data.roster().reclaimWindowNanos()).isEqualTo(86_400_000_000_000L);
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
        later.putInt("v", 3);
        assertThat(MatchData.load(ID, later).quarantineReason()).contains("blob version 3");
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

    @Test
    void aMalformedRosterCannotSilentlyBecomeAnEmptyRoster() {
        CompoundTag wrongType = saved(walking(), 3);
        wrongType.putString("roster", "invalid");
        assertQuarantinedUnchanged(wrongType);

        CompoundTag wrongElements = saved(walking(), 3);
        ListTag strings = new ListTag();
        strings.add(StringTag.valueOf("invalid"));
        wrongElements.put("roster", strings);
        assertQuarantinedUnchanged(wrongElements);
    }

    @Test
    void aSavedPlayerSeatNeedsAnExistingPrimaryBindingButAnObserverDoesNot() {
        CompoundTag unbound = withSeat(saved(walking(), 3), 15);
        assertQuarantinedUnchanged(unbound);

        CompoundTag observer = withSeat(saved(walking(), 3), PlayerSlot.ADMIN.value());
        MatchData loaded = MatchData.load(ID, observer);
        assertThat(loaded.state()).isEqualTo(MatchData.State.LOADED);
        assertThat(loaded.roster().members()).hasSize(1);
    }

    private static CompoundTag withSeat(CompoundTag tag, int slot) {
        CompoundTag seat = new CompoundTag();
        seat.put("uuid", NbtUtils.createUUID(UUID.fromString("00000000-0000-0000-0000-00000000000a")));
        seat.putInt("slot", slot);
        seat.putLong("remainingNanos", 300_000_000_000L);
        ListTag roster = new ListTag();
        roster.add(seat);
        tag.put("roster", roster);
        return tag;
    }

    @Test
    void aLegacyRosterMigratesWithoutRestoringAConnectionOrChangingTheSnapshot() {
        SimHarness h = walking();
        CompoundTag legacy = withSeat(saved(h, 3), 0);
        legacy.putInt("v", 1);
        legacy.getList("roster", net.minecraft.nbt.Tag.TAG_COMPOUND)
                .getCompound(0)
                .remove("remainingNanos");
        MatchData loaded = MatchData.load(ID, legacy);

        assertThat(loaded.state()).isEqualTo(MatchData.State.LOADED);
        assertThat(loaded.loadedSnapshot()).isEqualTo(h.core().snapshot());
        UUID member = loaded.roster().members().getFirst();
        assertThat(loaded.roster().isConnected(member)).isFalse();
        assertThat(loaded.roster().remainingNanos(member)).isBetween(1L, 300_000_000_000L);
        CompoundTag migrated = loaded.save(new CompoundTag(), RegistryAccess.EMPTY);
        assertThat(migrated.getInt("v")).isEqualTo(2);
        assertThat(migrated.getList("roster", net.minecraft.nbt.Tag.TAG_COMPOUND)
                        .getCompound(0)
                        .contains("remainingNanos", net.minecraft.nbt.Tag.TAG_LONG))
                .isTrue();
        assertThat(MatchData.load(ID, migrated).state()).isEqualTo(MatchData.State.LOADED);
    }

    @Test
    void aCurrentBlobRequiresTheExplicitSessionEnvelopeAndDuration() {
        CompoundTag missingRoster = saved(walking(), 3);
        missingRoster.putInt("v", 2);
        missingRoster.remove("roster");
        assertQuarantinedUnchanged(missingRoster);
        CompoundTag missingDuration = withSeat(saved(walking(), 3), 0);
        missingDuration.putInt("v", 2);
        missingDuration
                .getList("roster", net.minecraft.nbt.Tag.TAG_COMPOUND)
                .getCompound(0)
                .remove("remainingNanos");
        assertQuarantinedUnchanged(missingDuration);
    }

    @Test
    void anIncompleteLobbyLaunchCannotRestoreAsACompletedIdentityMatch() {
        MatchData live = MatchData.live(ID, walking().core()::snapshot, () -> 3);
        live.beginLobbyLaunch();
        assertThat(live.isLobbyLaunchPending()).isTrue();
        assertThatThrownBy(live::beginLobbyLaunch).isInstanceOf(IllegalStateException.class);
        CompoundTag pending = live.save(new CompoundTag(), RegistryAccess.EMPTY);
        assertThat(pending.getBoolean("launchPending")).isTrue();
        assertQuarantinedUnchanged(pending);

        live.completeLobbyLaunch();
        assertThat(live.isLobbyLaunchPending()).isFalse();
        assertThat(MatchData.load(ID, live.save(new CompoundTag(), RegistryAccess.EMPTY))
                        .state())
                .isEqualTo(MatchData.State.LOADED);
    }

    @Test
    void aCurrentBlobRequiresATypedLaunchTransactionFlag() {
        CompoundTag missing = saved(walking(), 3);
        missing.remove("launchPending");
        assertQuarantinedUnchanged(missing);
        CompoundTag malformed = saved(walking(), 3);
        malformed.putString("launchPending", "false");
        assertQuarantinedUnchanged(malformed);
    }

    private static void assertQuarantinedUnchanged(CompoundTag tag) {
        MatchData data = MatchData.load(ID, tag);
        assertThat(data.state()).isEqualTo(MatchData.State.QUARANTINED);
        assertThat(data.save(new CompoundTag(), RegistryAccess.EMPTY)).isEqualTo(tag);
        data.setDirty();
        assertThat(data.isDirty()).isFalse();
    }
}
