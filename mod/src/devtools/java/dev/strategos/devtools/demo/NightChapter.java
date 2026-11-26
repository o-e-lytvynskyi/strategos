package dev.strategos.devtools.demo;

import dev.strategos.core.protocol.RuleChange;
import dev.strategos.core.sim.DayClock;
import java.util.List;

/** M10: a match with day and night on: at dusk the clock runs to night, vision shrinks, buildings' lights do not. */
final class NightChapter implements DemoChapter {
    @Override
    public String id() {
        return "night";
    }

    @Override
    public String title() {
        return "Day and night";
    }

    @Override
    public List<String> groups() {
        return List.of("m10", "vision");
    }

    @Override
    public boolean fogged() {
        return true;
    }

    @Override
    public void script(DemoScript s) {
        s.run(Stage::fogMatch)
                .run(st -> {
                    st.admin(new RuleChange("dayNight", "true"));
                    st.building(Ids.CIVIC_CENTRE, 0, -20, 0);
                    st.building(Ids.HOUSE, 0, -20, 16);
                    st.units(Ids.WORKER, 0, -8, -6, 3, 2);
                    st.building(Ids.HOUSE, 1, 24, -6);
                })
                .look(0, 0, 70)
                .until("the town", 10, st -> Fights.alive(st, 0, Ids.WORKER) >= 3)
                .say("Day: our workers see the enemy house far off")
                .pause(4)
                .say("The clock runs on to dusk")
                .fast("dusk", 400, st -> DayClock.phase(st.q().tick()) == DayClock.DayPhase.DUSK)
                .pause(3)
                .say("Night falls: vision shrinks")
                .fast("night", 100, st -> DayClock.night(st.q().tick()))
                .pause(2)
                .say("The enemy house is lost to the dark")
                .pause(3)
                .say("Our buildings' lights keep their radius")
                .pause(5);
    }
}
