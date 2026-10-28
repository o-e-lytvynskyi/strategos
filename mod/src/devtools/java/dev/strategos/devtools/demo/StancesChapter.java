package dev.strategos.devtools.demo;

import dev.strategos.core.protocol.AttackMove;
import dev.strategos.core.protocol.SetStance;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.Stance;
import java.util.List;

/** M5: three lanes, one raider each: a defensive squad returns to its spot, stand ground never moves, a passive worker flees. */
final class StancesChapter implements DemoChapter {
    @Override
    public String id() {
        return "stances";
    }

    @Override
    public String title() {
        return "Stances";
    }

    @Override
    public List<String> groups() {
        return List.of("m5", "combat");
    }

    @Override
    public void script(DemoScript s) {
        s.run(DemoStage::match)
                .run(st -> {
                    st.units(Ids.INFANTRY, 0, -10, -16, 3, 1);
                    st.units(Ids.INFANTRY, 0, -10, 0, 3, 1);
                    st.units(Ids.WORKER, 0, -10, 16, 3, 1);
                })
                .look(0, 0, 50)
                .until(
                        "the three lanes",
                        10,
                        st -> Fights.alive(st, 0, Ids.INFANTRY) >= 6 && Fights.alive(st, 0, Ids.WORKER) >= 3)
                .run(st -> {
                    int[] all = st.handles(0, Ids.INFANTRY);
                    st.admin(new SetStance(UnitSet.of(DemoStage.part(all, 0, 3)), Stance.DEFENSIVE));
                    st.admin(new SetStance(UnitSet.of(DemoStage.part(all, 3, 6)), Stance.STAND_GROUND));
                    st.admin(new SetStance(UnitSet.of(st.handles(0, Ids.WORKER)), Stance.PASSIVE));
                })
                .say("Top: defensive. Middle: stand ground. Bottom: passive")
                .pause(3)
                .say("A raider walks into each lane")
                .run(st -> {
                    for (int lane : new int[] {-16, 0, 16}) {
                        st.units(Ids.INFANTRY, 1, 12, lane, 1, 0);
                    }
                })
                .until("three raiders", 10, st -> Fights.alive(st, 1, Ids.INFANTRY) >= 3)
                .run(st -> {
                    for (int h : st.handles(1, Ids.INFANTRY)) {
                        var u = st.q().unit(h);
                        if (u != null) {
                            double lane = Math.round((u.z() - st.z(0)) / 16.0) * 16.0;
                            st.admin(AttackMove.replace(UnitSet.of(h), st.at(-10, lane)));
                        }
                    }
                })
                .until(
                        "the worker runs",
                        40,
                        st -> st.near(st.handles(0, Ids.WORKER), -10, 16, 3) < Fights.alive(st, 0, Ids.WORKER))
                .say("The passive worker runs from the raider")
                .until("the raiders dead", 90, st -> Fights.alive(st, 1, Ids.INFANTRY) <= 1)
                .say("Defensive steps out to fight, then walks back")
                .until(
                        "back on the spots",
                        40,
                        st -> st.near(st.handles(0, Ids.INFANTRY), -10, -16, 3)
                                        + st.near(st.handles(0, Ids.INFANTRY), -10, 0, 3)
                                >= Fights.alive(st, 0, Ids.INFANTRY))
                .say("Stand ground never left its spot")
                .pause(4);
    }
}
