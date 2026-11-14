package dev.strategos.devtools.demo;

import dev.strategos.core.protocol.AnnounceWave;
import dev.strategos.core.protocol.AttackMove;
import dev.strategos.core.protocol.SetAi;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.mod.server.content.Scenarios;
import java.util.List;
import java.util.Objects;

/** M6: a computer player announces a wave at its camp, the raid bar fills, the horn sounds, and the wave marches. */
final class RaidChapter implements DemoChapter {
    private static final int RAIDERS = 5;

    @Override
    public String id() {
        return "raid";
    }

    @Override
    public String title() {
        return "A wave announced at its camp";
    }

    @Override
    public List<String> groups() {
        return List.of("m6", "ai", "combat");
    }

    @Override
    public void script(DemoScript s) {
        Watch watch = new Watch();
        s.run(st -> watch.attach(st.match()))
                .run(st -> {
                    st.building(Ids.CIVIC_CENTRE, 0, -30, 0);
                    st.units(Ids.WORKER, 0, -30, 10, 4, 1.5);
                    st.building(Ids.CIVIC_CENTRE, 1, 40, 0);
                    String script = Objects.requireNonNull(Scenarios.script(st.server(), "economy"), "the economy");
                    st.admin(new SetAi(new FactionSlot(1), "economy", script));
                })
                .look(0, 0, 80)
                .until(
                        "the two towns",
                        10,
                        st -> !st.buildings(1, Ids.CIVIC_CENTRE).isEmpty())
                .pause(1)
                .say("The computer is mustering a wave")
                .run(st -> {
                    st.units(Ids.INFANTRY, 1, 10, 0, RAIDERS, 2);
                    st.order(
                            1,
                            new AnnounceWave(
                                    (int) Math.floor(st.x(10)), (int) Math.floor(st.z(0)), AnnounceWave.LEAST));
                })
                .until("the announcement", 10, st -> watch.waveLeaves() > 0)
                .say("A camp: a campfire between two banners")
                .pause(3)
                .say("The raid bar fills; thirty seconds to prepare")
                .pause(8)
                .say("Sped up to the moment the wave leaves")
                .fast("the wave leaves", 40, st -> st.q().tick() >= watch.waveLeaves())
                .say("The horn: the wave marches and the bar empties")
                .run(st -> st.admin(AttackMove.replace(UnitSet.of(st.handles(1, Ids.INFANTRY)), st.at(-30, 0))))
                .pause(10)
                .say("The camp is taken down behind it")
                .pause(3);
    }
}
