package dev.strategos.devtools.demo;

import java.util.List;

/** M3: workers fell trees and pick berries, and carry each load to the civic centre. */
final class GatherChapter implements DemoChapter {
    @Override
    public String id() {
        return "gather";
    }

    @Override
    public String title() {
        return "Wood and berries";
    }

    @Override
    public List<String> groups() {
        return List.of("m3", "economy");
    }

    @Override
    public void script(DemoScript s) {
        s.run(st -> {
                    for (int k = 0; k < 6; k++) {
                        st.oak(12 + (k % 3) * 4, -8 + (k / 3) * 4, 5);
                    }
                    for (int k = 0; k < 6; k++) {
                        st.bush(10 + k * 2, 10);
                        st.bush(10 + k * 2, 12);
                    }
                })
                .run(DemoStage::match)
                .run(st -> {
                    st.building(Ids.CIVIC_CENTRE, 0, -10, 0);
                    st.units(Ids.WORKER, 0, 0, 0, 8, 2);
                })
                .look(2, 0, 45)
                .until("the workers", 10, st -> st.handles(0, Ids.WORKER).length >= 8)
                .say("Four workers to the trees, four to the berries")
                .run(st -> {
                    int[] w = st.handles(0, Ids.WORKER);
                    st.gather(DemoStage.part(w, 0, 4), st.needNode("wood", 14, -6));
                    st.gather(DemoStage.part(w, 4, 8), st.needNode("food", 14, 11));
                })
                .until("the first load home", 60, st -> st.gathered(0, "wood") + st.gathered(0, "food") > 0)
                .say("Each carries a load home and goes back")
                .until(
                        "wood and food gathered",
                        120,
                        st -> st.gathered(0, "wood") >= 40 && st.gathered(0, "food") >= 40)
                .say("Trunks go block by block; bushes are picked bare")
                .pause(4);
    }
}
