package dev.strategos.devtools.demo;

import dev.strategos.core.protocol.AttackMove;
import dev.strategos.core.protocol.Guard;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.Patrol;
import dev.strategos.core.protocol.UnitSet;
import java.util.List;

/** M10: a patrol engages an intruder and resumes its rounds; a guard follows a worker and defends it. */
final class PatrolChapter implements DemoChapter {
    static final int GUARDS = 2;

    @Override
    public String id() {
        return "patrol";
    }

    @Override
    public String title() {
        return "Patrol and guard";
    }

    @Override
    public List<String> groups() {
        return List.of("m10", "orders");
    }

    @Override
    public void script(DemoScript s) {
        s.run(Stage::match)
                .run(st -> {
                    st.units(Ids.INFANTRY, 0, -30, -20, 1, 0);
                    st.units(Ids.INFANTRY, 0, -34, 20, GUARDS, 2);
                    st.units(Ids.WORKER, 0, -30, 20, 1, 0);
                })
                .look(0, 0, 70)
                .until(
                        "the patroller, the guard and the worker",
                        10,
                        st -> Fights.alive(st, 0, Ids.INFANTRY) == 1 + GUARDS && Fights.alive(st, 0, Ids.WORKER) == 1)
                .say("A spearman patrols between two points")
                .run(st ->
                        st.admin(Patrol.replace(UnitSet.of(patroller(st)), List.of(st.at(-30, -20), st.at(30, -20)))))
                .until("the patrol underway", 15, st -> st.near(new int[] {patroller(st)}, -10, -20, 6) == 1)
                .say("An intruder on its route")
                .run(st -> st.units(Ids.INFANTRY, 1, 10, -20, 1, 0))
                .until("the intruder met", 60, st -> Fights.alive(st, 1, Ids.INFANTRY) == 0)
                .say("It engages the intruder, then resumes its round")
                .until("the far point", 40, st -> st.near(new int[] {patroller(st)}, 30, -20, 4) == 1)
                .pause(2)
                .say("Two more spearmen guard a worker")
                .run(st -> {
                    st.admin(Guard.replace(UnitSet.of(guards(st)), st.handles(0, Ids.WORKER)[0]));
                    st.admin(Move.replace(UnitSet.of(st.handles(0, Ids.WORKER)), st.at(30, 20)));
                })
                .say("The guards follow wherever the worker goes")
                .until("worker and guards far east", 60, st -> st.near(guards(st), 30, 20, 10) == GUARDS)
                .say("A raider comes for the worker")
                .run(st -> st.units(Ids.INFANTRY, 1, 30, 44, 1, 0))
                .until("the raider there", 5, st -> Fights.alive(st, 1, Ids.INFANTRY) == 1)
                .run(st -> st.admin(AttackMove.replace(UnitSet.of(st.handles(1, Ids.INFANTRY)), st.at(30, 32))))
                .until("the raider beaten", 60, st -> Fights.alive(st, 1, Ids.INFANTRY) == 0)
                .say("The guards defend it")
                .pause(4);
    }

    /** The spearman that started on the patrol's leg: spawned first, so the lowest handle. */
    private static int patroller(Stage st) {
        int[] own = st.handles(0, Ids.INFANTRY);
        return java.util.Arrays.stream(own).min().orElseThrow();
    }

    /** The others, spawned after it. */
    private static int[] guards(Stage st) {
        int patroller = patroller(st);
        return java.util.Arrays.stream(st.handles(0, Ids.INFANTRY))
                .filter(h -> h != patroller)
                .toArray();
    }
}
