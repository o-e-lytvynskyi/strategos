package dev.strategos.mod.server.persist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.shared.MatchId;
import dev.strategos.core.sim.MatchLifecycle;
import dev.strategos.mod.server.match.MatchRecord;
import dev.strategos.mod.server.match.MatchRefusedException;
import dev.strategos.mod.server.match.MatchSource;
import dev.strategos.mod.server.match.MatchSpace;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

/** CORE-12: the registry blob keeps its records and counters, and a corrupt one is quarantined, never rewritten. */
class RegistryDataTest {
    private static MatchRecord record(MatchId id, MatchLifecycle lifecycle) {
        MatchSpace space = new MatchSpace(Level.OVERWORLD, new BlockPos(id.value() * 100, 56, 0), 16, 16, 56, 88);
        return new MatchRecord(id, space, -42L * id.value(), lifecycle, MatchSource.GENERATED, 3, Optional.empty());
    }

    @Test
    void recordsAndCountersRoundTrip() {
        RegistryData data = new RegistryData();
        MatchId first = data.allocateId();
        MatchId second = data.allocateId();
        data.put(record(first, MatchLifecycle.RUNNING));
        data.put(record(second, MatchLifecycle.FAULTED).withLifecycle(MatchLifecycle.FAULTED, Optional.of("boom")));
        assertThat(data.nextEpoch()).isEqualTo(1);
        assertThat(data.nextEpoch()).isEqualTo(2);

        RegistryData loaded =
                RegistryData.load(data.save(new CompoundTag(), RegistryAccess.EMPTY), RegistryAccess.EMPTY);

        assertThat(loaded.quarantineReason()).isNull();
        assertThat(loaded.records()).containsExactlyElementsOf(data.records());
        assertThat(loaded.record(second).faultMessage()).contains("boom");
        assertThat(loaded.epoch()).isEqualTo(2);
        assertThat(loaded.allocateId()).as("ids are never reused").isEqualTo(new MatchId(3));
        assertThat(first).isEqualTo(new MatchId(1));
    }

    @Test
    void removeForgetsARecord() {
        RegistryData data = new RegistryData();
        MatchId id = data.allocateId();
        data.put(record(id, MatchLifecycle.PAUSED));

        data.remove(id);

        assertThat(data.record(id)).isNull();
        assertThat(data.records()).isEmpty();
    }

    @Test
    void aCorruptTagIsQuarantinedKeepingItsRawTagAndRefusingWrites() {
        RegistryData data = new RegistryData();
        data.put(record(data.allocateId(), MatchLifecycle.RUNNING));
        CompoundTag tag = data.save(new CompoundTag(), RegistryAccess.EMPTY);
        tag.put("records", StringTag.valueOf("not a list"));

        RegistryData quarantined = RegistryData.load(tag, RegistryAccess.EMPTY);

        assertThat(quarantined.quarantineReason()).contains("do not decode");
        assertThat(quarantined.save(new CompoundTag(), RegistryAccess.EMPTY)).isEqualTo(tag);
        assertThatThrownBy(() -> quarantined.put(record(new MatchId(9), MatchLifecycle.RUNNING)))
                .isInstanceOf(MatchRefusedException.class)
                .hasMessageContaining("quarantined");
        assertThatThrownBy(quarantined::allocateId).isInstanceOf(MatchRefusedException.class);
        quarantined.setDirty();
        assertThat(quarantined.isDirty()).as("never rewritten").isFalse();
    }

    @Test
    void aRecordAtOrAboveTheNextIdIsQuarantined() {
        RegistryData data = new RegistryData();
        data.put(record(data.allocateId(), MatchLifecycle.RUNNING));
        CompoundTag tag = data.save(new CompoundTag(), RegistryAccess.EMPTY);
        tag.putInt("nextId", 1);

        assertThat(RegistryData.load(tag, RegistryAccess.EMPTY).quarantineReason())
                .contains("not below the next id");
    }
}
