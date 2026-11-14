package dev.strategos.devtools.demo;

import dev.strategos.core.protocol.AttackMove;
import dev.strategos.core.protocol.UnitSet;
import java.util.List;

/** M6: a defence tower shoots the spearmen who walk into its range. */
final class TowerChapter implements DemoChapter {
    private static final int ENEMIES = 4;

    @Override
    public String id() {
        return "tower";
    }

    @Override
    public String title() {
        return "The defence tower";
    }

    @Override
    public List<String> groups() {
        return List.of("m6", "combat", "buildings");
    }

    @Override
    public void script(DemoScript s) {
        s.run(DemoStage::match)
                .run(st -> {
                    st.building(Ids.TOWER, 0, -6, 0);
                    st.units(Ids.INFANTRY, 0, -10, 2, 3, 1.5);
                    st.units(Ids.INFANTRY, 1, 34, 0, ENEMIES, 2);
                })
                .look(8, 0, 44)
                .until(
                        "the tower and both sides",
                        10,
                        st -> !st.buildings(0, Ids.TOWER).isEmpty()
                                && Fights.alive(st, 1, Ids.INFANTRY) >= ENEMIES
                                && Fights.alive(st, 0, Ids.INFANTRY) >= 3)
                .say("A tower and three spearmen; four enemies come")
                .run(st -> st.admin(AttackMove.replace(UnitSet.of(st.handles(1, Ids.INFANTRY)), st.at(-6, 0))))
                .until("the first arrow home", 60, st -> Fights.hurt(st, 1, Ids.INFANTRY) > 0)
                .say("The tower shoots anything that comes in range")
                .pause(6)
                .say("Arrows reach further than any spear")
                .pause(6);
    }
}
