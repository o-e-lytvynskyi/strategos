package dev.strategos.core.client.order;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.AttackMove;
import dev.strategos.core.protocol.Follow;
import dev.strategos.core.protocol.Gather;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.Vec2d;
import java.util.List;
import org.junit.jupiter.api.Test;

/** UI-05.3–4: one right click makes exactly one Move of the selection; Shift queues it; no selection, no order. */
class OrderIntentTest {
    @Test
    void aClickMakesOneMoveAndShiftQueuesIt() {
        Move plain = OrderIntents.move(List.of(3, 1, 2), 10.5, 20.5, false);
        Move queued = OrderIntents.move(List.of(3, 1, 2), 10.5, 20.5, true);

        assertThat(plain).isNotNull();
        assertThat(plain.units().toArray()).containsExactly(1, 2, 3);
        assertThat(plain.target().x()).isEqualTo(10.5);
        assertThat(plain.mode()).isEqualTo(QueueMode.REPLACE);
        assertThat(queued).isNotNull();
        assertThat(queued.mode()).isEqualTo(QueueMode.QUEUE);
        assertThat(OrderIntents.move(List.of(), 1, 1, false)).isNull();
    }

    /** ORD-04.1: a right click on an enemy attacks it, on the ground moves there; armed, it attack-moves. */
    @Test
    void aClickOnAnEnemyAttacksOnTheGroundMovesAndArmedAttackMoves() {
        List<Integer> selection = List.of(7, 3);
        assertThat(OrderIntents.context(selection, 0x100005, 4, 5, false, false))
                .isEqualTo(new Attack(UnitSet.of(3, 7), 0x100005, QueueMode.REPLACE));
        assertThat(OrderIntents.context(selection, 0, 4, 5, true, false))
                .isEqualTo(new Move(UnitSet.of(3, 7), new Vec2d(4, 5), QueueMode.QUEUE, 0));
        assertThat(OrderIntents.context(selection, 0x100005, 4, 5, false, true))
                .as("armed, even over an enemy")
                .isEqualTo(new AttackMove(UnitSet.of(3, 7), new Vec2d(4, 5), QueueMode.REPLACE));
        assertThat(OrderIntents.context(List.of(), 0x100005, 4, 5, false, false))
                .isNull();
    }

    /** ECO-05: a right click on a resource block gathers there, queued with Shift; none without a selection. */
    @Test
    void aClickOnAResourceGathersThere() {
        assertThat(OrderIntents.gather(List.of(9, 4), 16, 70, -3, false))
                .isEqualTo(new Gather(UnitSet.of(4, 9), 16, 70, -3, QueueMode.REPLACE));
        assertThat(OrderIntents.gather(List.of(9), 16, 70, -3, true))
                .isEqualTo(new Gather(UnitSet.of(9), 16, 70, -3, QueueMode.QUEUE));
        assertThat(OrderIntents.gather(List.of(), 16, 70, -3, false)).isNull();
    }

    /** CAM-07.1: follow-me sends one Follow of the selection, none without one. */
    @Test
    void followMeSendsOneFollowOfTheSelection() {
        assertThat(OrderIntents.follow(List.of(4, 2))).isEqualTo(Follow.replace(UnitSet.of(2, 4)));
        assertThat(OrderIntents.follow(List.of())).isNull();
    }
}
