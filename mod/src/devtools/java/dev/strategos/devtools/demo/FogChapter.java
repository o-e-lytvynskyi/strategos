package dev.strategos.devtools.demo;

import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.UnitSet;
import java.util.List;

/** M6: the match starts in the dark; a scout rides out, explores, finds the enemy's centre and comes home. */
final class FogChapter implements DemoChapter {
    @Override
    public String id() {
        return "fog";
    }

    @Override
    public String title() {
        return "Fog of war and the scout";
    }

    @Override
    public List<String> groups() {
        return List.of("m6", "vision", "ui");
    }

    @Override
    public boolean fogged() {
        return true;
    }

    @Override
    public void script(DemoScript s) {
        s.run(DemoStage::fogMatch)
                .run(st -> {
                    st.building(Ids.CIVIC_CENTRE, 0, -34, 0);
                    st.units(Ids.WORKER, 0, -34, 10, 4, 1.5);
                    st.units(Ids.HORSEMAN, 0, -26, 0, 1, 0);
                    st.building(Ids.CIVIC_CENTRE, 1, 34, 0);
                })
                .look(0, 0, 80)
                .until("the town and the scout", 10, st -> st.handles(0, Ids.HORSEMAN).length == 1)
                .say("Black ground is unexplored: we see only our own")
                .pause(4)
                .say("The horseman scouts east")
                .run(st -> ride(st, 0, 0))
                .until("the scout mid-map", 40, st -> scoutNear(st, 0, 0, 4))
                .say("Ground it has seen stays dim: explored, not seen")
                .pause(3)
                .run(st -> ride(st, 22, 0))
                .until("the scout at the enemy", 40, st -> scoutNear(st, 22, 0, 4))
                .say("The enemy's centre shows only while it is seen")
                .pause(4)
                .run(st -> ride(st, -26, 0))
                .until("the scout home", 60, st -> scoutNear(st, -26, 0, 4))
                .say("Home again: the trail stays explored")
                .pause(3);
    }

    private static void ride(DemoStage st, double dx, double dz) {
        st.admin(Move.replace(UnitSet.of(st.handles(0, Ids.HORSEMAN)), st.at(dx, dz)));
    }

    private static boolean scoutNear(DemoStage st, double dx, double dz, double radius) {
        return st.near(st.handles(0, Ids.HORSEMAN), dx, dz, radius) == 1;
    }
}
