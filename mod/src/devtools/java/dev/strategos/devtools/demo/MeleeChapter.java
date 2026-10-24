package dev.strategos.devtools.demo;

import dev.strategos.core.protocol.AttackMove;
import dev.strategos.core.protocol.UnitSet;
import java.util.List;

/** M2: two squads attack-move into each other and fight it out in melee, health bars over their heads. */
final class MeleeChapter implements DemoChapter {
    private static final int SQUAD = 12;

    @Override
    public String id() {
        return "melee";
    }

    @Override
    public String title() {
        return "Attack-move and melee";
    }

    @Override
    public List<String> groups() {
        return List.of("m2", "combat");
    }

    @Override
    public void script(DemoScript s) {
        s.run(DemoStage::match)
                .run(st -> {
                    st.units(Ids.INFANTRY, 0, -16, 0, SQUAD, 3);
                    st.units(Ids.INFANTRY, 1, 16, 0, SQUAD + 2, 3);
                })
                .look(0, 0, 40)
                .until(
                        "both squads",
                        10,
                        st -> st.handles(0, Ids.INFANTRY).length >= SQUAD
                                && st.handles(1, Ids.INFANTRY).length >= SQUAD + 2)
                .say("Ours on the left, twelve; theirs on the right, fourteen")
                .pause(2)
                .say("Attack-move: walk, and fight whatever comes in sight")
                .run(st -> {
                    st.admin(AttackMove.replace(UnitSet.of(st.handles(0, Ids.INFANTRY)), st.at(16, 0)));
                    st.admin(AttackMove.replace(UnitSet.of(st.handles(1, Ids.INFANTRY)), st.at(-16, 0)));
                })
                .until("the first blow", 30, st -> alive(st, 0) + alive(st, 1) < 2 * SQUAD + 2 || hurt(st))
                .say("Each picks a foe; the health bars show who is losing")
                .until("one squad gone", 120, st -> alive(st, 0) == 0 || alive(st, 1) == 0)
                .say("The bigger squad wins")
                .pause(3);
    }

    private static int alive(DemoStage st, int faction) {
        return st.handles(faction, Ids.INFANTRY).length;
    }

    private static boolean hurt(DemoStage st) {
        for (int f = 0; f < 2; f++) {
            for (int h : st.handles(f, Ids.INFANTRY)) {
                if (st.q().hitPoints(h) < st.q().maxHitPoints(h)) {
                    return true;
                }
            }
        }
        return false;
    }
}
