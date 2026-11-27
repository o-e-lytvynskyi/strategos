package dev.strategos.core.sim.command;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.DeleteUnits;
import dev.strategos.core.protocol.NoticeKind;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.Notice;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.UnitDied;
import dev.strategos.core.testkit.SimHarness;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * UI-17 in L1: delete kills the issuer's own units at once with no killer, no notice of a loss and no loot; another
 * faction's units, stale handles and an empty set are refused; the journal replays to the same state.
 */
class DeleteUnitsTest {
    private static final FactionSlot F0 = new FactionSlot(0);
    private static final FactionSlot F1 = new FactionSlot(1);
    private static final PlayerSlot P0 = new PlayerSlot(0);

    private static int[] spawn(SimHarness h, FactionSlot faction, int count) {
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.INFANTRY, faction, new Vec2d(16, 16), count, 1));
        return h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .mapToInt(e -> ((UnitCreated) e).handle())
                .toArray();
    }

    private static CommandResult last(SimHarness h) {
        List<CommandResult> all = h.results();
        return all.get(all.size() - 1);
    }

    @Test
    void ownUnitsDieAtOnceWithNoKillerAndNoLossNotice() {
        SimHarness h = SimHarness.builder().factions(2).build();
        int[] mine = spawn(h, F0, 3);
        int[] theirs = spawn(h, F1, 1);

        h.submit(P0, new DeleteUnits(UnitSet.of(mine[0], mine[2])));
        h.step();

        assertThat(last(h).accepted()).isEqualTo(2);
        assertThat(h.events(UnitDied.class))
                .containsExactly(new UnitDied(mine[0], F0, Handle.NONE), new UnitDied(mine[2], F0, Handle.NONE));
        assertThat(h.events(Notice.class)).noneMatch(n -> n.kind() == NoticeKind.UNIT_LOST);
        assertThat(h.queries().unit(mine[0])).isNull();
        assertThat(h.queries().unit(mine[2])).isNull();
        assertThat(h.queries().unit(mine[1])).isNotNull();
        assertThat(h.queries().unit(theirs[0])).isNotNull();
        h.assertInvariants();
    }

    @Test
    void anotherFactionsUnitIsRefusedAndTheRestGoesThrough() {
        SimHarness h = SimHarness.builder().factions(2).build();
        int[] mine = spawn(h, F0, 1);
        int[] theirs = spawn(h, F1, 1);

        h.submit(P0, new DeleteUnits(UnitSet.of(mine[0], theirs[0])));
        h.step();

        CommandResult r = last(h);
        assertThat(r.accepted()).isEqualTo(1);
        assertThat(r.rejected()).isEqualTo(1);
        assertThat(r.firstReason()).isEqualTo(RejectReason.NOT_OWNER);
        assertThat(h.queries().unit(mine[0])).isNull();
        assertThat(h.queries().unit(theirs[0])).isNotNull();
    }

    @Test
    void aStaleHandleAndAnEmptySetAreRefused() {
        SimHarness h = SimHarness.builder().factions(2).build();
        int[] mine = spawn(h, F0, 1);
        h.submit(P0, new DeleteUnits(UnitSet.of(mine[0])));
        h.step();

        h.submit(P0, new DeleteUnits(UnitSet.of(mine[0]))); // already gone
        h.step();
        assertThat(last(h).accepted()).isZero();
        assertThat(last(h).firstReason()).isEqualTo(RejectReason.UNKNOWN_TARGET);

        h.submit(P0, new DeleteUnits(UnitSet.of()));
        h.step();
        assertThat(last(h).firstReason()).isEqualTo(RejectReason.INVALID_ARGUMENT);
    }

    @Test
    void theJournalReplaysToTheSameState() {
        SimHarness h = SimHarness.builder().factions(2).build();
        int[] mine = spawn(h, F0, 4);
        spawn(h, F1, 2);
        h.runTicks(5);
        h.submit(P0, new DeleteUnits(UnitSet.of(mine[1], mine[3])));
        h.runTicks(20);

        assertThat(h.replay().matches()).isTrue();
    }
}
