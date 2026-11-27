package dev.strategos.devtools.demo;

import dev.strategos.core.protocol.AttackMove;
import dev.strategos.core.protocol.UnitSet;
import java.util.List;

/** M10: survivors of a fight gain a rank, and its insignia shows on them. */
final class VeteransChapter implements DemoChapter {
    static final int OURS = 3;
    static final int ENEMIES = 3;
    static final int WORKERS = 2;

    @Override
    public String id() {
        return "veterans";
    }

    @Override
    public String title() {
        return "Veterans and their insignia";
    }

    @Override
    public List<String> groups() {
        return List.of("m10", "combat");
    }

    @Override
    public void script(DemoScript s) {
        s.run(Stage::match)
                .run(st -> {
                    st.units(Ids.HORSEMAN, 0, -14, 0, OURS, 2);
                    st.units(Ids.ARCHER, 1, 14, 0, ENEMIES, 2);
                    st.units(Ids.WORKER, 1, 14, 6, WORKERS, 2);
                })
                .look(0, 0, 36)
                .until(
                        "both sides",
                        10,
                        st -> Fights.alive(st, 0, Ids.HORSEMAN) >= OURS && Fights.alive(st, 1, Ids.ARCHER) >= ENEMIES)
                .say("Three fresh horsemen, no rank yet")
                .pause(3)
                .say("They ride at archers and workers: kills earn it")
                .run(st -> st.admin(AttackMove.replace(UnitSet.of(st.handles(0, Ids.HORSEMAN)), st.at(14, 0))))
                .until("a rank earned", 120, st -> ranked(st) > 0)
                .say("A rank: the insignia shows, and the stats grow")
                .until("the fight over", 60, st -> Fights.alive(st, 1, Ids.ARCHER) == 0)
                .pause(5);
    }

    private static int ranked(Stage st) {
        int n = 0;
        for (int h : st.handles(0, Ids.HORSEMAN)) {
            if (st.q().rank(h) > 0) {
                n++;
            }
        }
        return n;
    }
}
