package dev.strategos.devtools.demo;

import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.Research;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.sim.build.BuildingView;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** M6: four village buildings, then a fifth; the civic centre researches Town and the phase notice goes out. */
final class TownChapter implements DemoChapter {
    @Override
    public String id() {
        return "town";
    }

    @Override
    public String title() {
        return "Phase up to Town";
    }

    @Override
    public List<String> groups() {
        return List.of("m6", "tech", "buildings");
    }

    @Override
    public void script(DemoScript s) {
        Watch watch = new Watch();
        s.run(st -> watch.attach(st.match()))
                .run(st -> {
                    st.building(Ids.CIVIC_CENTRE, 0, 0, 8);
                    st.building(Ids.HOUSE, 0, -24, -14);
                    st.building(Ids.HOUSE, 0, -12, -14);
                    st.building(Ids.STOREHOUSE, 0, 0, -14);
                    st.building(Ids.FARMSTEAD, 0, 12, -14);
                    st.admin(new Grant(new FactionSlot(0), "food", 500));
                    st.admin(new Grant(new FactionSlot(0), "wood", 500));
                    st.admin(new Grant(new FactionSlot(0), "stone", 300));
                    st.admin(new Grant(new FactionSlot(0), "iron", 200));
                })
                .look(0, -2, 48)
                .until(
                        "the town",
                        10,
                        st -> centre(st) != null
                                && st.buildings(0, Ids.FARMSTEAD).size() == 1)
                .say("Town needs five village buildings; here are four")
                .pause(3)
                .say("The phase panel's checklist shows what is missing")
                .pause(2)
                .run(st -> st.building(Ids.BARRACKS, 0, 26, -14))
                .until("the fifth", 10, st -> !st.buildings(0, Ids.BARRACKS).isEmpty())
                .say("A barracks makes five: the checklist is green")
                .pause(2)
                .run(st -> {
                    BuildingView c = centre(st);
                    if (c != null) {
                        st.order(0, new Research(c.handle(), Ids.PHASE_TOWN));
                    }
                })
                .say("The civic centre researches Town; everyone is told")
                .fast("Town reached", 150, st -> watch.researched(Ids.PHASE_TOWN))
                .say("Town: towers and the arsenal can be built now")
                .pause(5);
    }

    private static @Nullable BuildingView centre(DemoStage st) {
        List<BuildingView> b = st.buildings(0, Ids.CIVIC_CENTRE);
        return b.isEmpty() ? null : b.getFirst();
    }
}
