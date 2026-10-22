package dev.strategos.mod.server.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.MatchId;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.mod.server.persist.MatchData;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/**
 * NET-01's roster (spec §2.7): a slot holds one player and the administrator's none; a player's slot controls the
 * faction of its number; the roster is saved with the match's blob and read back, and a match nobody sits in keeps
 * M0's blob.
 */
class MatchRosterTest {
    private static final UUID ANN = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final MatchId ID = new MatchId(1);

    @Test
    void aSlotHoldsOnePlayer() {
        MatchRoster roster = new MatchRoster();
        roster.seat(ANN, new PlayerSlot(1));
        roster.seat(ANN, new PlayerSlot(1)); // again: nothing changes

        assertThatThrownBy(() -> roster.seat(BOB, new PlayerSlot(1)))
                .isInstanceOf(MatchRefusedException.class)
                .hasMessageContaining("taken");
        assertThatThrownBy(() -> roster.seat(BOB, PlayerSlot.ADMIN)).isInstanceOf(MatchRefusedException.class);
        roster.seat(BOB, new PlayerSlot(0));
        assertThat(roster.of(new FactionSlot(1))).containsExactly(ANN);
        assertThat(roster.of(new FactionSlot(0))).containsExactly(BOB);
        roster.unseat(ANN);
        assertThat(roster.slotOf(ANN)).isNull();
        assertThat(roster.members()).containsExactly(BOB);
    }

    @Test
    void theRosterIsSavedWithTheMatch() {
        SimHarness h = SimHarness.builder().build();
        MatchData live = MatchData.live(ID, h.core()::snapshot, () -> 4);
        CompoundTag empty = live.save(new CompoundTag(), RegistryAccess.EMPTY);
        assertThat(empty.contains("roster")).as("nobody sits: M0's blob").isFalse();

        live.roster().seat(ANN, new PlayerSlot(1));
        live.roster().seat(BOB, new PlayerSlot(0));
        MatchData loaded = MatchData.load(ID, live.save(new CompoundTag(), RegistryAccess.EMPTY));

        assertThat(loaded.state()).isEqualTo(MatchData.State.LOADED);
        assertThat(loaded.roster().slotOf(ANN)).isEqualTo(new PlayerSlot(1));
        assertThat(loaded.roster().slotOf(BOB)).isEqualTo(new PlayerSlot(0));
        assertThat(loaded.roster().members()).containsExactly(ANN, BOB);
    }
}
