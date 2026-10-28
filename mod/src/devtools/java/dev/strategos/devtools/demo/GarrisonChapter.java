package dev.strategos.devtools.demo;

import dev.strategos.core.protocol.AllClear;
import dev.strategos.core.protocol.AttackMove;
import dev.strategos.core.protocol.RingBell;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.build.BuildingView;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** M5: horsemen raid a town; the bell sends the workers inside, the centre's arrows break the raid, all clear. */
final class GarrisonChapter implements DemoChapter {
    private static final int WORKERS = 10;
    private static final int RAIDERS = 6;

    @Override
    public String id() {
        return "garrison";
    }

    @Override
    public String title() {
        return "The bell and the town's arrows";
    }

    @Override
    public List<String> groups() {
        return List.of("m5", "combat", "buildings");
    }

    @Override
    public void script(DemoScript s) {
        s.run(DemoStage::match)
                .run(st -> {
                    st.building(Ids.CIVIC_CENTRE, 0, -10, 0);
                    st.units(Ids.WORKER, 0, -10, 12, WORKERS, 1.5);
                })
                .look(0, 0, 50)
                .until("the town", 10, st -> centre(st) != null && Fights.alive(st, 0, Ids.WORKER) >= WORKERS)
                .say("A town of ten workers; horsemen are coming")
                .run(st -> st.units(Ids.HORSEMAN, 1, 30, 0, RAIDERS, 2))
                .until("the raiders", 10, st -> Fights.alive(st, 1, Ids.HORSEMAN) >= RAIDERS)
                .run(st -> {
                    BuildingView c = centre(st);
                    if (c != null) { // horsemen cannot attack a building: they ride at the town and fight what is in it
                        st.admin(AttackMove.replace(UnitSet.of(st.handles(1, Ids.HORSEMAN)), st.at(-10, 0)));
                        st.order(0, new RingBell(c.handle()));
                    }
                })
                .say("The bell rings: workers run into the centre")
                .until("all inside", 30, GarrisonChapter::allInside)
                .say("Inside, each worker adds an arrow to the volley")
                .until("the raid broken", 90, st -> Fights.alive(st, 1, Ids.HORSEMAN) == 0)
                .say("The town's arrows broke the raid")
                .pause(2)
                .say("All clear: everyone goes back out")
                .run(st -> {
                    BuildingView c = centre(st);
                    if (c != null) {
                        st.order(0, new AllClear(c.handle()));
                    }
                })
                .until("workers out", 30, st -> !anyInside(st))
                .pause(3);
    }

    private static @Nullable BuildingView centre(DemoStage st) {
        List<BuildingView> b = st.buildings(0, Ids.CIVIC_CENTRE);
        return b.isEmpty() ? null : b.getFirst();
    }

    private static boolean inside(DemoStage st, int handle) {
        var u = st.q().unit(handle);
        return u != null && u.state() == UnitState.GARRISONED;
    }

    private static boolean allInside(DemoStage st) {
        int[] workers = st.handles(0, Ids.WORKER);
        for (int h : workers) {
            if (!inside(st, h)) {
                return false;
            }
        }
        return workers.length > 0;
    }

    private static boolean anyInside(DemoStage st) {
        for (int h : st.handles(0, Ids.WORKER)) {
            if (inside(st, h)) {
                return true;
            }
        }
        return false;
    }
}
