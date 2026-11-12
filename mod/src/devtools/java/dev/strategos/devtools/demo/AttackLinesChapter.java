package dev.strategos.devtools.demo;

import dev.strategos.core.protocol.AttackMove;
import dev.strategos.core.protocol.UnitSet;
import java.util.List;

/** M5: a fight with the attack lines drawn from attackers to their targets, and own units flashing when hit. */
final class AttackLinesChapter implements DemoChapter {
    @Override
    public String id() {
        return "attack-lines";
    }

    @Override
    public String title() {
        return "Attack lines and the under-attack flash";
    }

    @Override
    public List<String> groups() {
        return List.of("m5", "combat", "ui");
    }

    @Override
    public void script(DemoScript s) {
        s.run(DemoStage::match)
                .run(st -> {
                    st.units(Ids.INFANTRY, 0, -10, 0, 6, 2);
                    st.units(Ids.INFANTRY, 1, 10, 0, 4, 2);
                    st.units(Ids.ARCHER, 1, 18, 0, 3, 1);
                })
                .look(0, 0, 36)
                .until(
                        "both sides",
                        10,
                        st -> Fights.alive(st, 0, Ids.INFANTRY) >= 6
                                && Fights.alive(st, 1, Ids.INFANTRY) >= 4
                                && Fights.alive(st, 1, Ids.ARCHER) >= 3)
                .say("Six of ours against four spearmen and three archers")
                .pause(2)
                .run(st -> st.admin(AttackMove.replace(UnitSet.of(st.handles(0, Ids.INFANTRY)), st.at(18, 0))))
                .until("the first blow", 30, st -> Fights.hurt(st, 0, Ids.INFANTRY) > 0)
                .say("A line from each attacker to the one it hits")
                .pause(3)
                .say("Our units flash when they are hit")
                .until(
                        "a side gone",
                        120,
                        st -> Fights.alive(st, 0, Ids.INFANTRY) == 0
                                || Fights.alive(st, 1, Ids.INFANTRY) + Fights.alive(st, 1, Ids.ARCHER) == 0)
                .pause(3);
    }
}
