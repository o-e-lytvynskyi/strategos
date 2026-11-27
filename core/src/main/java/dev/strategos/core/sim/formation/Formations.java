package dev.strategos.core.sim.formation;

import dev.strategos.core.protocol.FormationShape;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.MatchState;
import org.jspecify.annotations.Nullable;

/** Forming a formation (ORD-06): the part of the {@code FormationMove} command that is not about orders. */
public final class Formations {
    private Formations() {}

    /** The unit index, of {@code handles}, whose mobility is the most confined (the highest code; the lowest index on a tie). */
    static int confined(MatchState s, int[] handles) {
        int confined = Handle.index(handles[0]);
        for (int handle : handles) {
            int i = Handle.index(handle);
            if (s.mobility(i).code() > s.mobility(confined).code()
                    || (s.mobility(i).code() == s.mobility(confined).code() && i < confined)) {
                confined = i;
            }
        }
        return confined;
    }

    /**
     * A formation of {@code shape} for the units {@code handles} (live, and at least one), marching to ({@code targetX},
     * {@code targetZ}) along one route, searched from the unit nearest the group's centroid on the grid of its most
     * confined member; each unit has its place, chosen by where it stands. Null when there is no route. The caller gives
     * the units their orders: {@link dev.strategos.core.sim.order.OrderKind#FORMATION}, the formation's id as the target
     * and the place's number as the slot.
     */
    public static @Nullable FormationController form(
            MatchState s, FormationShape shape, int[] handles, double targetX, double targetZ) {
        double cx = 0;
        double cz = 0;
        for (int handle : handles) {
            cx += s.motion().x(Handle.index(handle));
            cz += s.motion().z(Handle.index(handle));
        }
        cx /= handles.length;
        cz /= handles.length;
        int guide = Handle.index(handles[0]);
        double nearest = Double.MAX_VALUE;
        for (int handle : handles) {
            int i = Handle.index(handle);
            double dx = s.motion().x(i) - cx;
            double dz = s.motion().z(i) - cz;
            double d = dx * dx + dz * dz;
            if (d < nearest || (d == nearest && i < guide)) {
                nearest = d;
                guide = i;
            }
        }
        int confined = confined(s, handles);
        FormationStore store = s.play().formations();
        FormationRoute.Plan plan = FormationRoute.plan(s, guide, confined, targetX, targetZ, store.scratch());
        if (plan == null) {
            return null;
        }
        double[] route = FormationRoute.join(cx, cz, plan.points());
        FormationController c = store.create(s.newGroup(), shape, route, plan.more(), targetX, targetZ);
        FormationLayout.assign(c, s, handles);
        return c;
    }
}
