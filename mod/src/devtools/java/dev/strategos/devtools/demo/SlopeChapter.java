package dev.strategos.devtools.demo;

import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.PlaceBuilding;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.sim.build.BuildingStore;
import java.util.List;
import net.minecraft.world.level.block.Blocks;

/** M4: a barracks on a hillside: its foundation fills the low side and cuts the high one, then it goes up. */
final class SlopeChapter implements DemoChapter {
    @Override
    public String id() {
        return "slope";
    }

    @Override
    public String title() {
        return "A barracks on a slope";
    }

    @Override
    public List<String> groups() {
        return List.of("m4", "construction");
    }

    @Override
    public void script(DemoScript s) {
        s.run(
                        st -> { // a hillside rising a block every four eastwards, three high at its top
                            for (int x = -12; x <= 12; x++) {
                                int h = Math.max(0, Math.min(3, (x + 8) / 4));
                                if (h > 0) {
                                    st.fill(x, 0, -12, x, h - 1, 12, Blocks.GRASS_BLOCK.defaultBlockState());
                                }
                            }
                        })
                .run(DemoStage::match)
                .run(st -> {
                    st.building(Ids.CIVIC_CENTRE, 0, -30, -20); // a building goes up within 48 blocks of one
                    st.admin(new Grant(new FactionSlot(0), "wood", 500));
                    st.admin(new Grant(new FactionSlot(0), "stone", 300));
                    st.admin(new Grant(new FactionSlot(0), "iron", 100));
                    st.units(Ids.WORKER, 0, -16, 0, 6, 1);
                })
                .look(0, 0, 40)
                .until("the builders", 10, st -> st.handles(0, Ids.WORKER).length >= 6)
                .say("A hillside, three blocks from foot to top")
                .pause(2)
                .say("A barracks across it: the foundation levels it")
                .run(st -> {
                    int[] at = st.corner(Ids.BARRACKS, 0, 0);
                    st.order(
                            0,
                            new PlaceBuilding(
                                    Ids.BARRACKS,
                                    at[0],
                                    at[1],
                                    0,
                                    UnitSet.of(st.handles(0, Ids.WORKER)),
                                    QueueMode.REPLACE));
                })
                .until("the site", 10, st -> !st.buildings(0, Ids.BARRACKS).isEmpty())
                .pause(6)
                .say("Six builders; sped up from here")
                .fast(
                        "the barracks done",
                        400,
                        st -> st.buildings(0, Ids.BARRACKS).stream().anyMatch(b -> b.state() == BuildingStore.COMPLETE))
                .say("Done: it stands level on the slope")
                .pause(4);
    }
}
