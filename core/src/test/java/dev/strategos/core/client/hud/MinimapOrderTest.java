package dev.strategos.core.client.hud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.QueueMode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** UI-20.1: a right click on the minimap is one move of the selection to the point it shows. */
class MinimapOrderTest {
    private static final Minimap MAP = new Minimap(new HudRect(10, 20, 128, 128), 256, 256);

    @Test
    void aRightClickSendsOneMove() {
        double[] p = MAP.toMap(100.5, 200.5);
        Optional<Move> sent = MinimapOrders.rightClick(MAP, p[0], p[1], List.of(3, 5, 8), false);
        assertThat(sent).isPresent();
        Move move = sent.get();
        assertThat(move.units().toArray()).containsExactly(3, 5, 8);
        assertThat(move.target().x()).isCloseTo(100.5, within(1e-9));
        assertThat(move.target().z()).isCloseTo(200.5, within(1e-9));
        assertThat(move.mode()).isEqualTo(QueueMode.REPLACE);
    }

    @Test
    void shiftQueuesIt() {
        double[] p = MAP.toMap(10, 10);
        assertThat(MinimapOrders.rightClick(MAP, p[0], p[1], List.of(1), true)
                        .orElseThrow()
                        .mode())
                .isEqualTo(QueueMode.QUEUE);
    }

    @Test
    void noSelectionOrAMissSendsNothing() {
        double[] p = MAP.toMap(10, 10);
        assertThat(MinimapOrders.rightClick(MAP, p[0], p[1], List.of(), false)).isEmpty();
        assertThat(MinimapOrders.rightClick(MAP, 0, 0, List.of(1), false)).isEmpty();
    }
}
