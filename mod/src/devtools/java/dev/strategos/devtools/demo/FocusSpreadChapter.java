package dev.strategos.devtools.demo;

import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.AttackSpread;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.SetStance;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.Stance;
import java.util.List;

/** M5: eight archers focus one spearman (Ctrl), then spread over the rest (Alt). */
final class FocusSpreadChapter implements DemoChapter {
    private static final int FOES = 6;

    @Override
    public String id() {
        return "focus-spread";
    }

    @Override
    public String title() {
        return "Focus and spread";
    }

    @Override
    public List<String> groups() {
        return List.of("m5", "combat", "orders");
    }

    @Override
    public void script(DemoScript s) {
        s.run(DemoStage::match)
                .run(st -> {
                    st.units(Ids.ARCHER, 0, -12, 0, 8, 2);
                    st.units(Ids.INFANTRY, 1, 8, 0, FOES, 4);
                })
                .look(0, 0, 40)
                .until(
                        "both sides",
                        10,
                        st -> Fights.alive(st, 0, Ids.ARCHER) >= 8 && Fights.alive(st, 1, Ids.INFANTRY) >= FOES)
                .run(st -> st.admin(new SetStance(UnitSet.of(st.handles(1, Ids.INFANTRY)), Stance.STAND_GROUND)))
                .say("Eight archers, six spearmen holding their ground")
                .pause(2)
                .say("Ctrl-right-click: every archer on one target")
                .run(st -> st.admin(new Attack(
                        UnitSet.of(st.handles(0, Ids.ARCHER)),
                        st.handles(1, Ids.INFANTRY)[0],
                        QueueMode.REPLACE,
                        true)))
                .until("the target down", 60, st -> Fights.alive(st, 1, Ids.INFANTRY) < FOES)
                .say("Focused: it fell at once")
                .pause(2)
                .say("Alt-right-click: the group spreads over the foes")
                .run(st -> st.admin(new AttackSpread(
                        UnitSet.of(st.handles(0, Ids.ARCHER)), st.handles(1, Ids.INFANTRY)[0], QueueMode.REPLACE)))
                .until("several foes hurt", 40, st -> Fights.hurt(st, 1, Ids.INFANTRY) >= 3)
                .say("Each archer picks a different spearman")
                .pause(5);
    }
}
