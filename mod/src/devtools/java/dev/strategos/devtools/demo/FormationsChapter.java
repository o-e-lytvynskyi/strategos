package dev.strategos.devtools.demo;

import dev.strategos.core.protocol.FormationMove;
import dev.strategos.core.protocol.FormationShape;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.UnitSet;
import java.util.List;

/** M10: an army marches in box and then in line; a member given its own order leaves and the rest re-slot. */
final class FormationsChapter implements DemoChapter {
    static final int ARMY = 12;

    @Override
    public String id() {
        return "formations";
    }

    @Override
    public String title() {
        return "Formations";
    }

    @Override
    public List<String> groups() {
        return List.of("m10", "orders");
    }

    @Override
    public void script(DemoScript s) {
        s.run(Stage::match)
                .run(st -> st.units(Ids.INFANTRY, 0, -30, 0, ARMY, 3))
                .look(0, 0, 60)
                .until("the army", 10, st -> Fights.alive(st, 0, Ids.INFANTRY) >= ARMY)
                .say("Twelve spearmen; a march in box formation")
                .run(st -> march(st, FormationShape.BOX, 10, 0))
                .until("the box at its place", 60, st -> st.near(st.handles(0, Ids.INFANTRY), 10, 0, 8) >= ARMY)
                .say("They keep their places and the slowest pace")
                .pause(3)
                .say("Now the same army in line")
                .run(st -> march(st, FormationShape.LINE, 10, 24))
                .until("the line at its place", 60, st -> st.near(st.handles(0, Ids.INFANTRY), 10, 24, 10) >= ARMY)
                .pause(2)
                .say("One member gets its own order and leaves")
                .run(st -> st.admin(Move.replace(UnitSet.of(st.handles(0, Ids.INFANTRY)[0]), st.at(-20, -24))))
                .until("one left the formation", 10, st -> inFormation(st) == ARMY - 1)
                .say("The rest close up over the gap")
                .until("the rest re-slotted", 30, st -> st.near(st.handles(0, Ids.INFANTRY), 10, 24, 10) >= ARMY - 1)
                .pause(4);
    }

    private static void march(Stage st, FormationShape shape, double dx, double dz) {
        st.admin(FormationMove.replace(UnitSet.of(st.handles(0, Ids.INFANTRY)), st.at(dx, dz), shape));
    }

    /** How many of the army still keep a place in a formation. */
    private static int inFormation(Stage st) {
        int n = 0;
        for (int h : st.handles(0, Ids.INFANTRY)) {
            if (st.q().formationOf(h) != 0) {
                n++;
            }
        }
        return n;
    }
}
