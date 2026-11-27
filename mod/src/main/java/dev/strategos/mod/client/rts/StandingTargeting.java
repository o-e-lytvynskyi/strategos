package dev.strategos.mod.client.rts;

import dev.strategos.core.client.input.Action;
import dev.strategos.core.client.order.OrderIntents;
import dev.strategos.core.protocol.BuildingState;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.MatchJoin;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.mod.client.StrategosClient;
import dev.strategos.mod.client.rts.RtsMode.Pick;
import dev.strategos.mod.entity.UnitEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;

/**
 * The targeting of the standing orders (ORD-07), armed from the command card or a key. Patrol: each click on the ground
 * adds a point; a plain click sends the patrol with that point at once, a Shift-click keeps on adding, and a right
 * click or Enter sends the points so far (a right click with none cancels). Guard: the next click on a friendly unit or
 * building sends the order, a click on anything else cancels it. Cancel, a box selection or another targeting drops
 * either.
 */
public final class StandingTargeting {
    private final RtsMode mode;
    private final RtsOrders orders;
    private boolean patrol;
    private boolean guard;
    private final List<Vec2d> points = new ArrayList<>();

    StandingTargeting(RtsMode mode, RtsOrders orders) {
        this.mode = mode;
        this.orders = orders;
    }

    /** The command card's patrol button: the next ground clicks are its points. */
    public void armPatrol() {
        disarm();
        orders.disarmOthers();
        patrol = true;
    }

    /** The command card's guard button: the next click on a friendly unit or building is the charge. */
    public void armGuard() {
        disarm();
        orders.disarmOthers();
        guard = true;
    }

    /** Whether patrol targeting is armed, for the card's frame. */
    public boolean patrolArmed() {
        return patrol;
    }

    /** Whether guard targeting is armed, for the card's frame. */
    public boolean guardArmed() {
        return guard;
    }

    /** Drops what is armed. */
    void disarm() {
        patrol = false;
        guard = false;
        points.clear();
    }

    /** Escape: drops what is armed; true when something was, so the key does not also pause. */
    public boolean cancel() {
        boolean armed = patrol || guard;
        disarm();
        return armed;
    }

    /** A key or click of {@code action}: the keys arm, Enter sends a patrol, a cancel or a box drops them. */
    void fired(Action action) {
        switch (action) {
            case PATROL -> armPatrol();
            case GUARD -> armGuard();
            case CONFIRM -> commit();
            case CANCEL, BOX_SELECT, ATTACK_MOVE, FORMATION_BOX, FORMATION_LINE -> disarm();
            default -> {}
        }
    }

    /** A right click while a patrol is armed sends it, or cancels it with no point; true when a targeting was armed. */
    boolean rightClick() {
        if (patrol) {
            commit();
            return true;
        }
        return cancel();
    }

    /**
     * The left click at window points: for patrol a point on the ground, for guard the unit or building under the
     * cursor. True when a targeting took the click.
     */
    boolean fire(double x, double y, boolean shift) {
        if (!patrol && !guard) {
            return false;
        }
        Pick pick = mode.pick(x, y);
        MatchJoin join = StrategosClient.match();
        Minecraft mc = Minecraft.getInstance();
        if (pick == null || join == null || mc.level == null) {
            disarm();
            return true;
        }
        double wx = pick.entityId() < 0 ? pick.x() + 0.5 : pick.x();
        double wz = pick.entityId() < 0 ? pick.z() + 0.5 : pick.z();
        double wy = pick.entityId() < 0 ? pick.y() + 1 : pick.y();
        if (guard) {
            int charge = chargeAt(pick, mc, join);
            disarm();
            Command command = OrderIntents.guard(mode.selection().handles(), charge);
            if (command != null && mode.send(command)) {
                orders.mark(wx, wy, wz, 3);
            }
            return true;
        }
        points.add(new Vec2d(wx - join.originX(), wz - join.originZ()));
        orders.mark(wx, wy, wz, 2);
        if (!shift) {
            commit();
        }
        return true;
    }

    /** Sends the patrol of the points so far, if any, and disarms. */
    private void commit() {
        if (!patrol) {
            return;
        }
        List<Vec2d> held = List.copyOf(points);
        disarm();
        Command command = OrderIntents.patrol(mode.selection().handles(), held);
        if (command != null) {
            mode.send(command);
        }
    }

    /** The handle of the own unit or building at {@code pick}, 0 for anything else. */
    private int chargeAt(Pick pick, Minecraft mc, MatchJoin join) {
        if (pick.entityId() >= 0
                && mc.level != null
                && mc.level.getEntity(pick.entityId()) instanceof UnitEntity body
                && body.spawnData() != null
                && body.spawnData().matchId() == join.matchId()
                && body.spawnData().faction() == join.slot().value()) {
            return body.spawnData().handle();
        }
        BuildingState.Entry struck = mode.buildingAt(pick);
        return struck != null && struck.faction() == join.slot().value() ? struck.handle() : 0;
    }
}
