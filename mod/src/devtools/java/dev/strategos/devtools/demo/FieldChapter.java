package dev.strategos.devtools.demo;

import dev.strategos.core.protocol.Gather;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.SetAutoReseed;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.sim.build.BuildingView;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** M4: workers reap a field bare; the farmstead beside it sows it again on its own, and the wheat grows back. */
final class FieldChapter implements DemoChapter {
    @Override
    public String id() {
        return "fields";
    }

    @Override
    public String title() {
        return "Fields and reseeding";
    }

    @Override
    public List<String> groups() {
        return List.of("m4", "economy");
    }

    @Override
    public void script(DemoScript s) {
        s.run(DemoStage::match)
                .run(st -> {
                    st.building(Ids.FARMSTEAD, 0, -8, 0);
                    st.building(Ids.FIELD, 0, 4, 0);
                    st.admin(new Grant(new FactionSlot(0), "wood", 500));
                    st.units(Ids.WORKER, 0, -2, 5, 4, 1);
                })
                .look(0, 0, 30)
                .until("the farm", 10, st -> st.handles(0, Ids.WORKER).length >= 4 && field(st) != null)
                .say("A field of 300 food; the farmstead reseeds it")
                .run(st -> {
                    for (BuildingView b : st.buildings(0, Ids.FARMSTEAD)) {
                        st.admin(new SetAutoReseed(b.handle(), true));
                    }
                    BuildingView f = field(st);
                    if (f != null) { // a click on the field's middle
                        st.admin(Gather.replace(
                                UnitSet.of(st.handles(0, Ids.WORKER)),
                                f.footprint().anchorX() + f.footprint().spanX() / 2,
                                st.floor(),
                                f.footprint().anchorZ() + f.footprint().spanZ() / 2));
                    }
                })
                .until("the reaping begun", 60, st -> st.gathered(0, "food") > 0)
                .say("Four workers reap it; sped up from here")
                .fast("the field reaped and sown again", 900, st -> {
                    BuildingView f = field(st);
                    return f != null && f.fieldRipening() > 0;
                })
                .say("Reaped bare: sown again for 40 wood")
                .pause(4)
                .fast("the wheat ripe again", 120, st -> {
                    BuildingView f = field(st);
                    return f != null && f.fieldRipening() == 0 && f.fieldAmount() > 0;
                })
                .say("Ripe again, and the workers go on reaping")
                .pause(5);
    }

    private static @Nullable BuildingView field(DemoStage st) {
        List<BuildingView> fields = st.buildings(0, Ids.FIELD);
        return fields.isEmpty() ? null : fields.getFirst();
    }
}
