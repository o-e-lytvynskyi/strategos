package dev.strategos.core.sim;

import dev.strategos.core.sim.unitai.AiMessage;

/**
 * A unit's stuck ladder reached {@code rung} (PATH-07, spec §5.11): 1 asks for a path again, 2 moves to a free surface
 * nearby, 3 walks with avoidance off, 4 nudges along the path, and at {@link #GIVE_UP} the order fails.
 */
public record Stuck(int handle, int rung) implements BusMessage, AiMessage {
    /** The last rung: the unit gives its order up. */
    public static final int GIVE_UP = 5;

    public Stuck {
        if (rung < 1 || rung > GIVE_UP) {
            throw new IllegalArgumentException("stuck rung " + rung + " outside 1.." + GIVE_UP);
        }
    }
}
