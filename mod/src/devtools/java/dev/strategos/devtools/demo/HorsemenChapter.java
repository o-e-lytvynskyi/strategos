package dev.strategos.devtools.demo;

import dev.strategos.core.protocol.AttackMove;
import dev.strategos.core.protocol.UnitSet;
import java.util.List;

/** M5: mounted units, and the cavalry bonus: spearmen meet horsemen head on and win. */
final class HorsemenChapter implements DemoChapter {
    private static final int SQUAD = 4;

    @Override
    public String id() {
        return "horsemen";
    }

    @Override
    public String title() {
        return "Horsemen and the spearmen's bonus";
    }

    @Override
    public List<String> groups() {
        return List.of("m5", "combat");
    }

    @Override
    public void script(DemoScript s) {
        s.run(DemoStage::match)
                .run(st -> {
                    st.units(Ids.HORSEMAN, 0, -16, 0, SQUAD, 2);
                    st.units(Ids.INFANTRY, 1, 16, 0, SQUAD, 2);
                })
                .look(0, 0, 32)
                .until(
                        "both squads",
                        10,
                        st -> Fights.alive(st, 0, Ids.HORSEMAN) >= SQUAD && Fights.alive(st, 1, Ids.INFANTRY) >= SQUAD)
                .say("Four horsemen on the left: one mount, one rider")
                .pause(3)
                .say("Four spearmen on the right, a spear for the horse")
                .run(st -> {
                    st.admin(AttackMove.replace(UnitSet.of(st.handles(0, Ids.HORSEMAN)), st.at(16, 0)));
                    st.admin(AttackMove.replace(UnitSet.of(st.handles(1, Ids.INFANTRY)), st.at(-16, 0)));
                })
                .until(
                        "the first blow",
                        30,
                        st -> Fights.hurt(st, 0, Ids.HORSEMAN) + Fights.hurt(st, 1, Ids.INFANTRY) > 0)
                .say("Spearmen deal extra damage to cavalry")
                .until(
                        "one squad gone",
                        120,
                        st -> Fights.alive(st, 0, Ids.HORSEMAN) == 0 || Fights.alive(st, 1, Ids.INFANTRY) == 0)
                .say("The horsemen fall; the spearmen stand")
                .pause(3);
    }
}
