package dev.strategos.core.sim.formation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.FormationMove;
import dev.strategos.core.protocol.FormationShape;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.unitai.UnitAi;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * ORD-06: what the formation command refuses, and for which units: another faction's units, units inside a building,
 * the queue modes a standing formation has no place in, and a march no route leads to; a command left with one unit to
 * march is a plain move that keeps the refusals of the others in its result.
 */
class FormationRuleTest {
    private static CommandResult last(FormationFixtures f) {
        List<CommandResult> results = f.h.results();
        return results.get(results.size() - 1);
    }

    @Test
    void anotherFactionsUnitsAreRefusedAndTheOwnMarch() {
        FormationFixtures f = FormationFixtures.flat(false);
        int[] mine = f.block(TemplateSet.INFANTRY, 5, 5, 1.5, 20, 40);
        f.h.submit(new PlayerSlot(0), FormationMove.replace(UnitSet.of(mine), new Vec2d(90, 40), FormationShape.BOX));
        f.h.step();
        assertThat(last(f).outcome()).isEqualTo(CommandOutcome.ACCEPTED);

        f.h.submit(new PlayerSlot(1), FormationMove.replace(UnitSet.of(mine), new Vec2d(90, 60), FormationShape.BOX));
        f.h.step();

        CommandResult result = last(f);
        assertThat(result.outcome()).isEqualTo(CommandOutcome.REJECTED);
        assertThat(result.firstReason())
                .as("refused as not the issuer's, or as unseen by it (fog)")
                .isIn(RejectReason.NOT_OWNER, RejectReason.UNKNOWN_TARGET);
        assertThat(result.rejected()).isEqualTo(5);
        assertThat(f.s.play().formations().count())
                .as("the first formation still stands")
                .isEqualTo(1);
    }

    @Test
    void unitsInsideABuildingAreRefused() {
        FormationFixtures f = FormationFixtures.flat(false);
        int[] units = f.block(TemplateSet.INFANTRY, 4, 4, 1.5, 20, 40);
        f.s.ai().set(Handle.index(units[0]), UnitAi.GARRISONED);

        f.h.submit(PlayerSlot.ADMIN, FormationMove.replace(UnitSet.of(units), new Vec2d(90, 40), FormationShape.BOX));
        f.h.step();

        CommandResult result = last(f);
        assertThat(result.outcome()).isEqualTo(CommandOutcome.PARTIAL);
        assertThat(result.accepted()).isEqualTo(3);
        assertThat(result.rejected()).isEqualTo(1);
        assertThat(result.firstReason()).isEqualTo(RejectReason.INVALID_ARGUMENT);
        assertThat(FormationFixtures.members(f.only())).doesNotContain(units[0]).hasSize(3);
    }

    @Test
    void theQueueModesAreRefusedAndNothingIsFormed() {
        FormationFixtures f = FormationFixtures.flat(false);
        int[] units = f.block(TemplateSet.INFANTRY, 4, 4, 1.5, 20, 40);
        for (QueueMode mode : List.of(QueueMode.QUEUE, QueueMode.PUSH_FRONT)) {
            f.h.submit(
                    PlayerSlot.ADMIN,
                    new FormationMove(UnitSet.of(units), new Vec2d(90, 40), FormationShape.BOX, mode));
            f.h.step();

            CommandResult result = last(f);
            assertThat(result.outcome()).isEqualTo(CommandOutcome.REJECTED);
            assertThat(result.firstReason()).isEqualTo(RejectReason.UNSUPPORTED);
        }
        assertThat(f.s.play().formations().empty()).isTrue();
    }

    @Test
    void aMarchNoRouteLeadsToIsRefused() {
        FormationFixtures f = FormationFixtures.flat(true);
        int[] units = f.block(TemplateSet.INFANTRY, 2, 2, 1.5, 20, 40);
        int gate = f.s.play().addGate(0, new int[] {60 << 16 | 60, 60 << 16 | 61});
        f.h.runTicks(3);
        for (int unit : units) { // both stand where no surface is: in the gate's columns
            f.s.motion().set(Handle.index(unit), 60.5, 1, 60.5, 0);
        }

        f.h.submit(PlayerSlot.ADMIN, FormationMove.replace(UnitSet.of(units), new Vec2d(90, 40), FormationShape.BOX));
        f.h.step();

        CommandResult result = last(f);
        assertThat(result.outcome()).isEqualTo(CommandOutcome.REJECTED);
        assertThat(result.firstReason()).isEqualTo(RejectReason.INVALID_ARGUMENT);
        assertThat(f.s.play().formations().empty()).isTrue();
        assertThat(gate).isGreaterThanOrEqualTo(0);
    }

    @Test
    void aCommandLeftWithOneUnitIsAMoveThatKeepsTheOthersRefusals() {
        FormationFixtures f = FormationFixtures.flat(false);
        int[] units = f.block(TemplateSet.INFANTRY, 3, 3, 1.5, 20, 40);
        f.s.ai().set(Handle.index(units[1]), UnitAi.GARRISONED);
        f.s.ai().set(Handle.index(units[2]), UnitAi.GARRISONED);

        f.h.submit(PlayerSlot.ADMIN, FormationMove.replace(UnitSet.of(units), new Vec2d(90, 40), FormationShape.LINE));
        f.h.step();

        CommandResult result = last(f);
        assertThat(result.outcome()).isEqualTo(CommandOutcome.PARTIAL);
        assertThat(result.accepted()).isEqualTo(1);
        assertThat(result.rejected()).isEqualTo(2);
        assertThat(result.firstReason()).isEqualTo(RejectReason.INVALID_ARGUMENT);
        assertThat(f.s.play().formations().empty())
                .as("a formation of one is nothing")
                .isTrue();
        assertThat(f.s.orders().kind(Handle.index(units[0]))).isEqualTo(OrderKind.MOVE);
    }

    @Test
    void aFormationThatShrinksToOneMemberDissolvesIntoAMove() {
        FormationFixtures f = FormationFixtures.flat(false);
        int[] units = f.block(TemplateSet.INFANTRY, 3, 3, 1.5, 20, 40);
        f.march(units, 120, 40, FormationShape.BOX);
        f.h.runTicks(30);

        f.h.submit(
                PlayerSlot.ADMIN,
                dev.strategos.core.protocol.Move.replace(UnitSet.of(units[0], units[1]), new Vec2d(30, 100)));
        f.h.step();

        assertThat(f.s.play().formations().empty()).isTrue();
        assertThat(f.s.orders().kind(Handle.index(units[2]))).isEqualTo(OrderKind.MOVE);
        assertThat(f.s.ai().state(Handle.index(units[2]))).isEqualTo(dev.strategos.core.sim.UnitState.WALKING);
    }
}
