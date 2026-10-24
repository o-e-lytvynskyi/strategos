package dev.strategos.devtools.demo;

import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.SetRally;
import dev.strategos.core.protocol.TrainBatch;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.sim.build.BuildingView;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** M4: a barracks trains a batch of five spearmen, who walk to its rally point as they come out. */
final class RallyChapter implements DemoChapter {
    private static final int BATCH = 5;

    @Override
    public String id() {
        return "rally";
    }

    @Override
    public String title() {
        return "A batch and a rally point";
    }

    @Override
    public List<String> groups() {
        return List.of("m4", "production");
    }

    @Override
    public void script(DemoScript s) {
        s.run(DemoStage::match)
                .run(st -> {
                    st.building(Ids.CIVIC_CENTRE, 0, -24, -10);
                    st.building(Ids.BARRACKS, 0, -4, 0);
                    st.admin(new Grant(new FactionSlot(0), "food", 1_000));
                    st.admin(new Grant(new FactionSlot(0), "wood", 1_000));
                })
                .look(4, 0, 40)
                .until("the barracks", 10, st -> barracks(st) != null)
                .say("A barracks, its rally point to the east")
                .run(st -> {
                    BuildingView b = barracks(st);
                    if (b != null) {
                        st.admin(new SetRally(UnitSet.of(b.handle()), (int) st.x(18), st.floor(), (int) st.z(6)));
                    }
                })
                .pause(2)
                .say("A batch of five: quicker than five one by one")
                .run(st -> {
                    BuildingView b = barracks(st);
                    if (b != null) {
                        st.admin(new TrainBatch(b.handle(), Ids.INFANTRY, BATCH));
                    }
                })
                .fast("the batch trained", 300, st -> st.handles(0, Ids.INFANTRY).length >= BATCH)
                .say("Out together, and off to the rally point")
                .until(
                        "the batch at the rally point",
                        40,
                        st -> st.near(st.handles(0, Ids.INFANTRY), 18, 6, 6) >= BATCH)
                .pause(3);
    }

    private static @Nullable BuildingView barracks(DemoStage st) {
        List<BuildingView> b = st.buildings(0, Ids.BARRACKS);
        return b.isEmpty() ? null : b.getFirst();
    }
}
