package dev.strategos.devtools.demo;

import dev.strategos.core.protocol.Research;
import dev.strategos.core.sim.build.BuildingView;
import java.util.List;

/**
 * M10: one technology of an exclusive pair is researched and the other is refused. The tree screen is client-side and
 * a chapter cannot open it, so the choice is shown through the research order and its refusal.
 */
final class TreeChapter implements DemoChapter {
    @Override
    public String id() {
        return "tree";
    }

    @Override
    public String title() {
        return "Technology pairs";
    }

    @Override
    public List<String> groups() {
        return List.of("m10", "tech");
    }

    @Override
    public void script(DemoScript s) {
        Watch watch = new Watch();
        Refusals refusals = new Refusals();
        s.run(st -> {
                    st.match();
                    watch.attach(st);
                    refusals.attach(st);
                })
                .run(st -> {
                    st.building(Ids.CIVIC_CENTRE, 0, -24, 0);
                    st.building(Ids.HOUSE, 0, -24, -22);
                    st.building(Ids.HOUSE, 0, -12, -22);
                    st.building(Ids.STOREHOUSE, 0, 0, -22);
                    st.building(Ids.FARMSTEAD, 0, 12, -22);
                    st.building(Ids.BARRACKS, 0, 12, 0);
                    Chapters.grantAll(st, 0);
                })
                .look(-6, -8, 60)
                .until(
                        "the village",
                        10,
                        st -> Chapters.finished(st, 0, Ids.BARRACKS) != null
                                && Chapters.finished(st, 0, Ids.CIVIC_CENTRE) != null
                                && Chapters.finished(st, 0, Ids.FARMSTEAD) != null
                                && Chapters.finished(st, 0, Ids.STOREHOUSE) != null)
                .say("Town opens the barracks' pair: Phalanx or Skirmish")
                .run(st -> Chapters.researchAtCentre(st, Ids.PHASE_TOWN))
                .fast("Town reached", 150, st -> watch.researched(Ids.PHASE_TOWN))
                .say("Phalanx drill: spearmen hold the line")
                .run(st -> research(st, Ids.PHALANX))
                .fast("Phalanx researched", 120, st -> watch.researched(Ids.PHALANX))
                .say("Phalanx is ours; its pair is now locked")
                .pause(2)
                .run(st -> research(st, Ids.SKIRMISH_DRILL))
                .until("the refusal", 5, st -> refusals.count() > 0)
                .say("Skirmish drill is refused: the choice is made")
                .pause(4);
    }

    private static void research(Stage st, dev.strategos.core.shared.TechId tech) {
        BuildingView b = Chapters.finished(st, 0, Ids.BARRACKS);
        if (b == null) {
            throw new IllegalStateException("no barracks to research " + tech + " in");
        }
        st.order(0, new Research(b.handle(), tech));
    }
}
