package dev.strategos.core.sim;

import dev.strategos.core.protocol.Command;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.testkit.RandomCommands;
import dev.strategos.core.testkit.SimHarness;
import java.util.List;

/**
 * The seeded runs of the determinism tests: 32 x 32, two factions of 20 units, and every tick each faction's player
 * sends what {@link RandomCommands} draws. The harness checks the invariants after every tick.
 */
public final class SeededRuns {
    private static final List<FactionSlot> FACTIONS = List.of(new FactionSlot(0), new FactionSlot(1));

    private SeededRuns() {}

    /** Two spawn ticks, then {@code ticks} ticks of random commands. */
    public static SimHarness run(long seed, int ticks) {
        SimHarness h = SimHarness.builder().seed(seed).build();
        h.spawn(FACTIONS.get(0), new Vec2d(8, 8), 20, 6.0);
        h.spawn(FACTIONS.get(1), new Vec2d(24, 24), 20, 6.0);
        for (int t = 0; t < ticks; t++) {
            for (FactionSlot faction : FACTIONS) {
                for (Command command : RandomCommands.next(seed, h.tick(), h.queries(), faction)) {
                    h.submit(new PlayerSlot(faction.value()), command);
                }
            }
            h.step();
        }
        return h;
    }
}
