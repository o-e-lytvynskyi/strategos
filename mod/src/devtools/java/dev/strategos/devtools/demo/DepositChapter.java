package dev.strategos.devtools.demo;

import java.util.List;

/** M4: an iron mine and a quarry, mined by workers who carry ore and stone to a storehouse. */
final class DepositChapter implements DemoChapter {
    @Override
    public String id() {
        return "deposits";
    }

    @Override
    public String title() {
        return "Mines and quarries";
    }

    @Override
    public List<String> groups() {
        return List.of("m4", "economy");
    }

    @Override
    public void script(DemoScript s) {
        s.run(DemoStage::match)
                .run(st -> {
                    st.admin(
                            new dev.strategos.core.protocol.SpawnDeposit(Ids.IRON_MINE, (int) st.x(6), (int) st.z(-6)));
                    st.admin(new dev.strategos.core.protocol.SpawnDeposit(Ids.QUARRY, (int) st.x(6), (int) st.z(6)));
                    st.building(Ids.STOREHOUSE, 0, -6, 0);
                    st.units(Ids.WORKER, 0, 0, 0, 6, 2);
                })
                .look(0, 0, 35)
                .until(
                        "the deposits",
                        10,
                        st -> st.handles(0, Ids.WORKER).length >= 6
                                && st.node("iron", 7, -5) != null
                                && st.node("stone", 7, 7) != null)
                .say("An iron mine and a quarry, three workers each")
                .run(st -> {
                    int[] w = st.handles(0, Ids.WORKER);
                    st.gather(DemoStage.part(w, 0, 3), st.needNode("iron", 7, -5));
                    st.gather(DemoStage.part(w, 3, 6), st.needNode("stone", 7, 7));
                })
                .until("ore and stone in", 120, st -> st.gathered(0, "iron") >= 20 && st.gathered(0, "stone") >= 20)
                .say("A deposit sinks into the ground as it empties")
                .pause(5);
    }
}
