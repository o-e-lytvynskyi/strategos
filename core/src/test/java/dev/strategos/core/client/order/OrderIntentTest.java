package dev.strategos.core.client.order;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.AttackMove;
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
}
