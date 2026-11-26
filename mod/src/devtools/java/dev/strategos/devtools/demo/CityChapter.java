package dev.strategos.devtools.demo;

import dev.strategos.core.protocol.RejectReason;
import java.util.List;

/** M10: the City phase widens the civic centre's border, so a house refused before it goes up; its arrows grow. */
final class CityChapter implements DemoChapter {
    static final int CENTRE_DX = -50;
    /** 70 blocks from the centre: past the Town border (56), inside City's (56 x 1.3 = 72.8). */
    static final int FAR_DX = 20;

    static final int WORKERS = 8;

    @Override
    public String id() {
        return "city";
    }

    @Override
    public String title() {
        return "The City phase";
    }

    @Override
    public List<String> groups() {
        return List.of("m10", "tech", "buildings");
    }

    @Override
    public void script(DemoScript s) {
        Watch watch = new Watch();
        Refusals refusals = new Refusals();
        s.run(st -> {
                    st.match(DemoStage.PLOT);
                    watch.attach(st);
                    refusals.attach(st);
                })
                .run(st -> {
                    Chapters.village(st, CENTRE_DX, 0);
                    // the three Town buildings City asks for, behind the centre so they do not stretch the border
                    st.building(Ids.TOWER, 0, CENTRE_DX - 24, 14);
                    st.building(Ids.ARSENAL, 0, CENTRE_DX - 24, -4);
                    st.building(Ids.TEMPLE, 0, CENTRE_DX - 36, 30);
                    Chapters.grantAll(st, 0);
                    st.units(Ids.WORKER, 0, CENTRE_DX, 14, WORKERS, 1.5);
                })
                .look(-10, 0, 90)
                .until(
                        "the village and the Town buildings",
                        10,
                        st -> Chapters.finished(st, 0, Ids.TEMPLE) != null
                                && Chapters.finished(st, 0, Ids.ARSENAL) != null
                                && Chapters.finished(st, 0, Ids.TOWER) != null
                                && Chapters.finished(st, 0, Ids.BARRACKS) != null
                                && Fights.alive(st, 0, Ids.WORKER) >= WORKERS)
                .say("One civic centre: its border and three arrows")
                .run(st -> Chapters.place(st, Ids.HOUSE, FAR_DX, 0, st.handles(0, Ids.WORKER)))
                .until("the refusal", 5, st -> refusals.count() > 0)
                .run(st -> Chapters.require(
                        refusals.last() == RejectReason.OUTSIDE_TERRITORY,
                        "the house was refused for " + refusals.last() + ", not OUTSIDE_TERRITORY"))
                .say("A house this far out is refused")
                .pause(3)
                .say("Town, then City: two phases of research")
                .run(st -> Chapters.researchAtCentre(st, Ids.PHASE_TOWN))
                .fast("Town reached", 150, st -> watch.researched(Ids.PHASE_TOWN))
                .run(st -> Chapters.researchAtCentre(st, Ids.PHASE_CITY))
                .fast("City reached", 200, st -> watch.researched(Ids.PHASE_CITY))
                .say("City: the border grows by a third")
                .run(st -> Chapters.place(st, Ids.HOUSE, FAR_DX, 0, st.handles(0, Ids.WORKER)))
                .until(
                        "the house where it was refused",
                        15,
                        st -> st.buildings(0, Ids.HOUSE).size() >= 3)
                .say("The house refused before now goes up")
                .pause(3)
                .say("And the centre shoots five arrows, not three")
                .pause(4);
    }
}
