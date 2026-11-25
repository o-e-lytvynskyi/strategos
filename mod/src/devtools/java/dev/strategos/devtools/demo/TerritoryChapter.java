package dev.strategos.devtools.demo;

import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.BuildingView;
import java.util.List;

/** M9: a house outside the border is refused, inside it goes up; Town lets a second centre claim neutral land. */
final class TerritoryChapter implements DemoChapter {
    static final int CENTRE_DX = -50;
    /** Past the centre's border (radius 56 blocks), and far enough from a second centre's, which stands at SECOND_DX. */
    static final int FAR_DX = 20;

    static final int INSIDE_DX = -40;
    static final int INSIDE_DZ = 20;
    /** At least the centres' spacing (64 blocks) from the first, on ground nobody owns. */
    static final int SECOND_DX = 50;

    static final int WORKERS = 8;

    @Override
    public String id() {
        return "territory";
    }

    @Override
    public String title() {
        return "Territory and the build rule";
    }

    @Override
    public List<String> groups() {
        return List.of("m9", "buildings");
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
                    Chapters.grantAll(st, 0);
                    st.units(Ids.WORKER, 0, CENTRE_DX, 14, WORKERS, 1.5);
                })
                .look(-10, 0, 90)
                .until(
                        "the village",
                        10,
                        st -> Chapters.finished(st, 0, Ids.BARRACKS) != null
                                && Fights.alive(st, 0, Ids.WORKER) >= WORKERS)
                .say("One civic centre: the border is its land")
                .pause(3)
                .say("A house beyond the border is refused")
                .run(st -> Chapters.place(st, Ids.HOUSE, FAR_DX, 0, st.handles(0, Ids.WORKER)))
                .until("the refusal", 5, st -> refusals.count() > 0)
                .run(st -> Chapters.require(
                        refusals.last() == RejectReason.OUTSIDE_TERRITORY,
                        "the house was refused for " + refusals.last() + ", not OUTSIDE_TERRITORY"))
                .say("Outside your territory: nothing goes up there")
                .pause(3)
                .say("Inside the border the same house is built")
                .run(st -> Chapters.place(st, Ids.HOUSE, INSIDE_DX, INSIDE_DZ, st.handles(0, Ids.WORKER)))
                .until("the third house", 10, st -> st.buildings(0, Ids.HOUSE).size() >= 3)
                .pause(3)
                .say("Town lets a second centre claim neutral land")
                .run(Chapters::researchTown)
                .fast("Town reached", 150, st -> watch.researched(Ids.PHASE_TOWN))
                .run(st -> Chapters.place(st, Ids.CIVIC_CENTRE, SECOND_DX, 0, st.handles(0, Ids.WORKER)))
                .until(
                        "the second centre's foundation",
                        10,
                        st -> st.buildings(0, Ids.CIVIC_CENTRE).size() == 2)
                .say("The workers raise a second civic centre")
                .fast("the second centre done", 400, TerritoryChapter::bothCentresDone)
                .say("Finished: the border grows round it")
                .run(st -> Chapters.place(st, Ids.HOUSE, FAR_DX, 0, st.handles(0, Ids.WORKER)))
                .until(
                        "a house where it was refused",
                        10,
                        st -> st.buildings(0, Ids.HOUSE).size() >= 4)
                .say("The house refused before now goes up")
                .pause(4);
    }

    private static boolean bothCentresDone(Stage st) {
        List<BuildingView> centres = st.buildings(0, Ids.CIVIC_CENTRE);
        return centres.size() == 2 && centres.stream().allMatch(b -> b.state() == BuildingStore.COMPLETE);
    }
}
