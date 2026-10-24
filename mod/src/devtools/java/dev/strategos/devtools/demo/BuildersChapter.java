package dev.strategos.devtools.demo;

import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.PlaceBuilding;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.BuildingView;
import java.util.List;

/** M4: two houses at once, one builder at the left, five at the right; the right one goes up far sooner. */
final class BuildersChapter implements DemoChapter {
    @Override
    public String id() {
        return "builders";
    }

    @Override
    public String title() {
        return "One builder against five";
    }

    @Override
    public List<String> groups() {
        return List.of("m4", "construction");
    }

    @Override
    public void script(DemoScript s) {
        s.run(DemoStage::match)
                .run(st -> {
                    st.building(Ids.CIVIC_CENTRE, 0, 0, -22); // a building goes up within 48 blocks of one
                    st.admin(new Grant(new FactionSlot(0), "wood", 500));
                    st.units(Ids.WORKER, 0, -9, 8, 1, 0);
                    st.units(Ids.WORKER, 0, 9, 8, 5, 1);
                })
                .look(0, 0, 32)
                .until("the builders", 10, st -> st.handles(0, Ids.WORKER).length >= 6)
                .say("Two houses: one worker builds the left, five the right")
                .run(st -> {
                    int[] w = st.handles(0, Ids.WORKER); // the lone one spawned first
                    int[] one = DemoStage.part(w, 0, 1);
                    int[] five = DemoStage.part(w, 1, 6);
                    place(st, -9, one);
                    place(st, 9, five);
                })
                .until("the sites", 10, st -> st.buildings(0, Ids.HOUSE).size() == 2)
                .say("The house rises block by block as the builders work")
                .until("the right house done", 120, st -> done(st, true))
                .say("Five builders done; one still at it, sped up from here")
                .fast("the left house done", 300, st -> done(st, false))
                .say("Both done: more hands, sooner, each adding less")
                .pause(3);
    }

    private static void place(DemoStage st, double dx, int[] builders) {
        int[] at = st.corner(Ids.HOUSE, dx, 0);
        st.order(0, new PlaceBuilding(Ids.HOUSE, at[0], at[1], 0, UnitSet.of(builders), QueueMode.REPLACE));
    }

    /** Whether the right ({@code east}) or the left house is finished. */
    private static boolean done(DemoStage st, boolean east) {
        for (BuildingView b : st.buildings(0, Ids.HOUSE)) {
            boolean isEast = b.footprint().anchorX() > st.x(0);
            if (isEast == east && b.state() == BuildingStore.COMPLETE) {
                return true;
            }
        }
        return false;
    }
}
