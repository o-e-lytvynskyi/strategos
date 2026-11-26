package dev.strategos.mod.server.persist;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.MatchId;
import dev.strategos.core.testkit.SimHarness;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

class ExpiryResolutionSaveTest {
    private static final MatchId ID = new MatchId(1);

    @Test
    void completedPoliciesAreSavedCanonicallyAndSurviveWithoutAnyCurrentComputerController() {
        var h = SimHarness.builder().build();
        var data = MatchData.live(ID, h.core()::snapshot, () -> 7);
        data.recordExpiry(new FactionSlot(1));
        data.recordExpiry(new FactionSlot(0));
        data.recordExpiry(new FactionSlot(1));
        var tag = data.save(new CompoundTag(), RegistryAccess.EMPTY);
        assertThat(tag.getInt("v")).isEqualTo(3);
        assertThat(tag.getIntArray("expiryResolved")).containsExactly(0, 1);
        var back = MatchData.load(ID, tag);
        assertThat(back.state()).isEqualTo(MatchData.State.LOADED);
        assertThat(back.expiryResolved(new FactionSlot(0))).isTrue();
        assertThat(back.expiryResolved(new FactionSlot(1))).isTrue();
        assertThat(back.loadedSnapshot()).isEqualTo(h.core().snapshot());
        assertThat(back.save(new CompoundTag(), RegistryAccess.EMPTY).getIntArray("expiryResolved"))
                .containsExactly(0, 1);
    }

    @Test
    void freshCurrentBlobsCarryAnExplicitEmptyResolutionEnvelope() {
        var tag = fresh();
        assertThat(tag.contains("expiryResolved", Tag.TAG_INT_ARRAY)).isTrue();
        assertThat(tag.getIntArray("expiryResolved")).isEmpty();
        assertThat(MatchData.load(ID, tag).expiryResolved(new FactionSlot(0))).isFalse();
    }

    @Test
    void versionTwoMigratesWithoutInventingResolutionOrRenewingItsSavedGrace() {
        var h = SimHarness.builder().build();
        var tag = fresh();
        tag.putInt("v", 2);
        tag.remove("expiryResolved");
        var member = new CompoundTag();
        member.put("uuid", NbtUtils.createUUID(new UUID(0, 1)));
        member.putInt("slot", 0);
        member.putLong("remainingNanos", 17);
        tag.getList("roster", Tag.TAG_COMPOUND).add(member);
        var back = MatchData.load(ID, tag, () -> 0L, 1_000);
        assertThat(back.state()).isEqualTo(MatchData.State.LOADED);
        assertThat(back.loadedSnapshot()).isEqualTo(h.core().snapshot());
        assertThat(back.expiryResolved(new FactionSlot(0))).isFalse();
        assertThat(back.roster().remainingNanos(new UUID(0, 1))).isEqualTo(17);
        assertThat(back.save(new CompoundTag(), RegistryAccess.EMPTY).getInt("v"))
                .isEqualTo(3);
    }

    @Test
    void oldSessionEnvelopeGuardsStillApplyWhileVersionTwoMigrates() {
        var missing = fresh();
        missing.putInt("v", 2);
        missing.remove("expiryResolved");
        missing.remove("launchPending");
        quarantined(missing);
        var roster = fresh();
        roster.putInt("v", 2);
        roster.remove("expiryResolved");
        roster.remove("roster");
        quarantined(roster);
    }

    @Test
    void currentResolutionMetadataIsRequiredAndStrictlyTyped() {
        var missing = fresh();
        missing.remove("expiryResolved");
        quarantined(missing);
        var wrong = fresh();
        wrong.putString("expiryResolved", "0");
        quarantined(wrong);
    }

    @Test
    void invalidResolutionFactionsCannotBeSilentlyNormalized() {
        for (int[] values : new int[][] {{-1}, {2}, {0, 0}, {1, 0}, {0, 1, 2}}) {
            var tag = fresh();
            tag.putIntArray("expiryResolved", values);
            quarantined(tag);
        }
    }

    private static CompoundTag fresh() {
        var h = SimHarness.builder().build();
        return MatchData.live(ID, h.core()::snapshot, () -> 7).save(new CompoundTag(), RegistryAccess.EMPTY);
    }

    private static void quarantined(CompoundTag tag) {
        var data = MatchData.load(ID, tag);
        assertThat(data.state()).isEqualTo(MatchData.State.QUARANTINED);
        assertThat(data.save(new CompoundTag(), RegistryAccess.EMPTY)).isEqualTo(tag);
        data.setDirty();
        assertThat(data.isDirty()).isFalse();
    }
}
