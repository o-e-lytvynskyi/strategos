package dev.strategos.core.sim.command;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.testkit.SimHarness;
import java.util.List;
import org.junit.jupiter.api.Test;

class MoveRuleTest {
    private static final PlayerSlot PLAYER_0 = new PlayerSlot(0);
    private static final PlayerSlot PLAYER_1 = new PlayerSlot(1);

    private final SimHarness h = SimHarness.builder().build();
    private final int[] own = h.spawn(new FactionSlot(0), new Vec2d(8, 8), 2, 2.0);
    private final int[] theirs = h.spawn(new FactionSlot(1), new Vec2d(24, 24), 1, 0.0);

    private CommandResult move(PlayerSlot issuer, Move move) {
        int seq = h.submit(issuer, move);
        h.step();
        return h.results().stream()
                .filter(r -> r.clientSeq() == seq)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void acceptsTheIssuersOwnUnits() {
        CommandResult result = move(PLAYER_0, Move.replace(UnitSet.of(own), new Vec2d(20, 8)));

        assertThat(result.outcome()).isEqualTo(CommandOutcome.ACCEPTED);
        assertThat(result.accepted()).isEqualTo(2);
        assertThat(result.rejected()).isZero();
        assertThat(result.firstReason()).isNull();
        assertThat(result.kind()).isEqualTo("strategos:move");
        assertThat(h.queries().unit(own[0]).state()).isEqualTo(UnitState.WALKING);
        assertThat(h.queries().unit(own[0]).target()).isEqualTo(new Vec2d(20, 8));
    }

    @Test
    void refusesAnotherFactionsUnitAndChangesNothing() {
        UnitView before = h.queries().unit(theirs[0]);

        CommandResult result = move(PLAYER_0, Move.replace(UnitSet.of(theirs), new Vec2d(20, 8)));

        assertThat(result.outcome()).isEqualTo(CommandOutcome.REJECTED);
        assertThat(result.firstReason()).isEqualTo(RejectReason.NOT_OWNER);
        assertThat(h.queries().unit(theirs[0])).isEqualTo(before);
        // A twin with the same two spawns (the spawn stream is keyed by tick, issuer and sequence) and an empty tick
        // where the refused Move was: equal hashes mean the Move changed no unit, order or queue.
        SimHarness twin = SimHarness.builder().build();
        twin.spawn(new FactionSlot(0), new Vec2d(8, 8), 2, 2.0);
        twin.spawn(new FactionSlot(1), new Vec2d(24, 24), 1, 0.0);
        twin.step();
        assertThat(h.stateHash()).as("the refused Move changed nothing").isEqualTo(twin.stateHash());
    }

    @Test
    void anEmptySetIsRefusedWholeBeforeTheOtherChecks() {
        for (Move empty : List.of(
                Move.replace(UnitSet.of(), new Vec2d(10, 10)),
                Move.replace(UnitSet.of(), new Vec2d(40, 8)),
                new Move(UnitSet.of(), new Vec2d(10, 10), QueueMode.QUEUE, 0))) {
            CommandResult result = move(PLAYER_0, empty);
            assertThat(result.outcome()).as("%s", empty).isEqualTo(CommandOutcome.REJECTED);
            assertThat(result.firstReason()).as("%s", empty).isEqualTo(RejectReason.INVALID_ARGUMENT);
            assertThat(result.accepted()).isZero();
            assertThat(result.rejected()).isZero();
        }
    }

    @Test
    void aNeverIssuedHandleAndAStaleOneGetTheSameUnknownTarget() {
        int neverIssued = Handle.of(500, 1);
        int stale = own[1];
        KernelAccess.state(h.core()).entities().destroy(stale);
        int reused = h.spawn(new FactionSlot(0), new Vec2d(8, 8), 1, 0.0)[0];
        assertThat(Handle.index(reused)).isEqualTo(Handle.index(stale));

        CommandResult missing = move(PLAYER_0, Move.replace(UnitSet.of(neverIssued), new Vec2d(10, 10)));
        CommandResult dead = move(PLAYER_0, Move.replace(UnitSet.of(stale), new Vec2d(10, 10)));

        for (CommandResult r : List.of(missing, dead)) {
            assertThat(r.outcome()).isEqualTo(CommandOutcome.REJECTED);
            assertThat(r.firstReason()).isEqualTo(RejectReason.UNKNOWN_TARGET);
        }
        assertThat(List.of(dead.outcome(), dead.accepted(), dead.rejected(), dead.firstReason()))
                .isEqualTo(List.of(missing.outcome(), missing.accepted(), missing.rejected(), missing.firstReason()));
        assertThat(h.queries().unit(reused).state())
                .as("the reused index is untouched")
                .isEqualTo(UnitState.IDLE);
    }

    @Test
    void aMixedSetIsPartialWithCounts() {
        CommandResult result =
                move(PLAYER_0, Move.replace(UnitSet.of(own[0], theirs[0], Handle.of(900, 1)), new Vec2d(20, 8)));

        assertThat(result.outcome()).isEqualTo(CommandOutcome.PARTIAL);
        assertThat(result.accepted()).isEqualTo(1);
        assertThat(result.rejected()).isEqualTo(2);
        assertThat(result.firstReason())
                .as("the first refusal in handle order")
                .isEqualTo(Handle.of(900, 1) < theirs[0] ? RejectReason.UNKNOWN_TARGET : RejectReason.NOT_OWNER);
    }

    @Test
    void aTargetOutsideTheBoundsIsRefusedWhole() {
        UnitView before = h.queries().unit(own[0]);
        CommandResult result = move(PLAYER_0, Move.replace(UnitSet.of(own), new Vec2d(40, 8)));

        assertThat(result.outcome()).isEqualTo(CommandOutcome.REJECTED);
        assertThat(result.firstReason()).isEqualTo(RejectReason.OUT_OF_BOUNDS);
        assertThat(result.rejected()).isEqualTo(2);
        h.runTicks(5);
        assertThat(h.queries().unit(own[0]).x()).isEqualTo(before.x());
        assertThat(h.queries().unit(own[0]).z()).isEqualTo(before.z());
    }

    @Test
    void formationMovesAreUnsupported() {
        CommandResult result = move(PLAYER_0, new Move(UnitSet.of(own), new Vec2d(10, 10), QueueMode.REPLACE, 2));

        assertThat(result.outcome()).isEqualTo(CommandOutcome.REJECTED);
        assertThat(result.firstReason()).isEqualTo(RejectReason.UNSUPPORTED);
        assertThat(h.queries().unit(own[0]).state()).isEqualTo(UnitState.IDLE);
    }

    @Test
    void adminMovesAnyUnit() {
        CommandResult result = move(PlayerSlot.ADMIN, Move.replace(UnitSet.of(own[0], theirs[0]), new Vec2d(16, 16)));

        assertThat(result.outcome()).isEqualTo(CommandOutcome.ACCEPTED);
        assertThat(result.accepted()).isEqualTo(2);
        assertThat(move(PLAYER_1, Move.replace(UnitSet.of(theirs), new Vec2d(4, 4)))
                        .outcome())
                .isEqualTo(CommandOutcome.ACCEPTED);
    }
}
