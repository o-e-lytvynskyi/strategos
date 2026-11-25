package dev.strategos.devtools.demo;

import dev.strategos.core.protocol.Capture;
import dev.strategos.core.protocol.Train;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.sim.build.BuildingView;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** M9: twenty infantry capture an enemy barracks; it turns to their colour and trains for the new owner. */
final class CaptureChapter implements DemoChapter {
    static final int ATTACKERS = 20;
    static final int BARRACKS_DX = 20;

    @Override
    public String id() {
        return "capture";
    }

    @Override
    public String title() {
        return "Capture a barracks";
    }

    @Override
    public List<String> groups() {
        return List.of("m9", "combat", "buildings");
    }

    @Override
    public void script(DemoScript s) {
        s.run(Stage::match)
                .run(st -> {
                    st.building(Ids.BARRACKS, 1, BARRACKS_DX, 0);
                    st.building(
                            Ids.CIVIC_CENTRE, 0, -34, -20); // housing: the new soldier needs a place in the population
                    st.building(Ids.HOUSE, 0, -34, 0);
                    st.building(Ids.HOUSE, 0, -22, 0);
                    st.units(Ids.INFANTRY, 0, -20, 14, ATTACKERS, 4);
                    Chapters.grantAll(st, 0);
                })
                .look(0, 0, 50)
                .until(
                        "the barracks and twenty infantry",
                        10,
                        st -> barracks(st) != null && Fights.alive(st, 0, Ids.INFANTRY) >= ATTACKERS)
                .say("An enemy barracks, nobody inside, twenty of ours")
                .pause(3)
                .run(st -> {
                    BuildingView b = barracks(st);
                    if (b != null) {
                        st.order(0, new Capture(UnitSet.of(st.handles(0, Ids.INFANTRY)), b.handle()));
                    }
                })
                .say("Capture: each soldier adds to the bar")
                .until("the barracks taken", 90, st -> owned(st) != null)
                .say("Taken: the barracks is ours")
                .pause(3)
                .say("It trains for its new owner")
                .run(st -> {
                    BuildingView b = owned(st);
                    if (b != null) {
                        st.order(0, new Train(b.handle(), Ids.INFANTRY));
                    }
                })
                .until("a new soldier", 60, st -> Fights.alive(st, 0, Ids.INFANTRY) > ATTACKERS)
                .say("A fresh spearman walks out for us")
                .pause(4);
    }

    /** The barracks wherever it stands now, whoever owns it. */
    private static @Nullable BuildingView barracks(Stage st) {
        for (int f = 0; f < 2; f++) {
            List<BuildingView> b = st.buildings(f, Ids.BARRACKS);
            if (!b.isEmpty()) {
                return b.getFirst();
            }
        }
        return null;
    }

    /** The barracks once faction 0 holds it. */
    private static @Nullable BuildingView owned(Stage st) {
        List<BuildingView> b = st.buildings(0, Ids.BARRACKS);
        return b.isEmpty() ? null : b.getFirst();
    }
}
