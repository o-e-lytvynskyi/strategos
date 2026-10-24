package dev.strategos.devtools.demo;

import java.util.List;

/** M3: sheep and pigs graze; a worker slaughters one and carries its meat to the farmstead. */
final class LivestockChapter implements DemoChapter {
    @Override
    public String id() {
        return "livestock";
    }

    @Override
    public String title() {
        return "Livestock";
    }

    @Override
    public List<String> groups() {
        return List.of("m3", "economy");
    }

    @Override
    public void script(DemoScript s) {
        s.run(DemoStage::match)
                .run(st -> {
                    st.building(Ids.FARMSTEAD, 0, -10, 0);
                    st.units(Ids.SHEEP, Ids.GAIA, 8, -4, 3, 2);
                    st.units(Ids.PIG, Ids.GAIA, 8, 5, 2, 2);
                    st.units(Ids.WORKER, 0, -4, 0, 2, 1);
                })
                .look(0, 0, 35)
                .until("the herd", 10, st -> st.handles(0, Ids.WORKER).length >= 2 && st.node("food", 8, -4) != null)
                .say("Sheep and pigs, Gaia's: they wander and graze")
                .pause(4)
                .say("Two workers slaughter a sheep and carry its meat")
                .run(st -> st.gather(st.handles(0, Ids.WORKER), st.needNode("food", 8, -4)))
                .until("meat at the farmstead", 90, st -> st.gathered(0, "food") >= 20)
                .say("The carcass shrinks with each load")
                .pause(6);
    }
}
