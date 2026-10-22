package dev.strategos.core.sim.order;

import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.Vec2d;

/**
 * What a right-click means (ORD-02, 04 §7.2): the client resolves it into a command before sending, and server commands
 * resolve theirs the same way. On the ground it is a move there; later cards add units (attack, follow), buildings
 * (garrison, repair) and resource nodes (gather).
 */
@CoreApi
public final class ContextResolver {
    private ContextResolver() {}

    /** {@code units} ordered to {@code point} on the ground, joined to their queues as {@code mode}. */
    public static Command ground(UnitSet units, Vec2d point, QueueMode mode) {
        return new Move(units, point, mode, 0);
    }
}
