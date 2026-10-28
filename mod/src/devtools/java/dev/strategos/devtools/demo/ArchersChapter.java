package dev.strategos.devtools.demo;

import dev.strategos.core.protocol.AttackMove;
import dev.strategos.core.protocol.UnitSet;
import java.util.List;

/** M5: archers loose volleys at a squad of spearmen walking in; the arrows are seen in flight. */
final class ArchersChapter implements DemoChapter {
    @Override
    public String id() {
        return "archers";
    }

    @Override
    public String title() {
        return "Archers and the volley";
    }

    @Override
    public List<String> groups() {
        return List.of("m5", "combat");
    }

    @Override
    public void script(DemoScript s) {
        s.run(DemoStage::match)
                .run(st -> {
                    st.units(Ids.ARCHER, 0, -14, 0, 6, 1.5);
                    st.units(Ids.INFANTRY, 1, 16, 0, 5, 2);
                })
                .look(0, 0, 40)
                .until(
                        "both squads",
                        10,
                        st -> st.handles(0, Ids.ARCHER).length >= 6 && st.handles(1, Ids.INFANTRY).length >= 5)
                .say("Six archers on the left, five spearmen on the right")
                .pause(2)
                .say("Attack-move: they shoot from afar, at what they see")
                .run(st -> st.admin(AttackMove.replace(UnitSet.of(st.handles(0, Ids.ARCHER)), st.at(16, 0))))
                .until("the first arrow home", 30, st -> Fights.hurt(st, 1, Ids.INFANTRY) > 0)
                .say("Each volley is aimed ahead of a moving target")
                .until("the spearmen gone", 90, st -> Fights.alive(st, 1, Ids.INFANTRY) == 0)
                .say("The spearmen fell before they reached the bows")
                .pause(3);
    }
}
