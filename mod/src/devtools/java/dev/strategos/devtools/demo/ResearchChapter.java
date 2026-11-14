package dev.strategos.devtools.demo;

import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.Research;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.sim.build.BuildingView;
import dev.strategos.core.sim.stats.StatBreakdown;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/** M6: a storehouse researches Iron axes (gather_wood_1) and the workers' wood rate rises by a fifth. */
final class ResearchChapter implements DemoChapter {
    private static final String RATE = "ResourceGatherer/rates/wood/tree";

    @Override
    public String id() {
        return "research";
    }

    @Override
    public String title() {
        return "Research at the storehouse";
    }

    @Override
    public List<String> groups() {
        return List.of("m6", "tech", "economy");
    }

    @Override
    public void script(DemoScript s) {
        Watch watch = new Watch();
        s.run(st -> {
                    for (int k = 0; k < 6; k++) {
                        st.oak(16 + (k % 3) * 4, -8 + (k / 3) * 4, 5);
                    }
                })
                .run(st -> {
                    watch.attach(st.match());
                })
                .run(st -> {
                    st.building(Ids.STOREHOUSE, 0, 6, -2);
                    st.units(Ids.WORKER, 0, 0, 8, 6, 2);
                    st.admin(new Grant(new FactionSlot(0), "food", 100));
                    st.admin(new Grant(new FactionSlot(0), "wood", 50));
                })
                .look(8, 0, 36)
                .until(
                        "the storehouse and workers",
                        10,
                        st -> store(st) != null && st.handles(0, Ids.WORKER).length >= 6)
                .run(st -> st.gather(st.handles(0, Ids.WORKER), st.needNode("wood", 18, -6)))
                .until("the first load home", 60, st -> st.gathered(0, "wood") > 0)
                .run(st -> DemoDirector.say(st, "Wood rate per worker: " + rate(st)))
                .pause(4)
                .say("The storehouse researches Iron axes; cost paid now")
                .run(st -> {
                    BuildingView b = store(st);
                    if (b != null) {
                        st.order(0, new Research(b.handle(), Ids.WOOD_1));
                    }
                })
                .fast("the research done", 120, st -> watch.researched(Ids.WOOD_1))
                .say("Researched: the feed names it and chimes")
                .run(st -> DemoDirector.say(st, "Wood rate per worker now: " + rate(st)))
                .pause(5);
    }

    private static @Nullable BuildingView store(DemoStage st) {
        List<BuildingView> b = st.buildings(0, Ids.STOREHOUSE);
        return b.isEmpty() ? null : b.getFirst();
    }

    /** The first worker's rate on trees, two decimals. */
    private static String rate(DemoStage st) {
        int[] w = st.handles(0, Ids.WORKER);
        StatBreakdown b = w.length == 0 ? null : st.q().stats(w[0], RATE);
        return b == null ? "?" : String.format(Locale.ROOT, "%.2f", b.result());
    }
}
