package dev.strategos.devtools.demo;

import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.sim.build.BuildingView;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** M6: two rams raze a civic centre, which loses its blocks stage by stage until it falls. */
final class SiegeChapter implements DemoChapter {
    @Override
    public String id() {
        return "siege";
    }

    @Override
    public String title() {
        return "Rams raze a civic centre";
    }

    @Override
    public List<String> groups() {
        return List.of("m6", "combat", "buildings");
    }

    @Override
    public void script(DemoScript s) {
        s.run(DemoStage::match)
                .run(st -> {
                    st.building(Ids.CIVIC_CENTRE, 1, 16, 0);
                    st.units(Ids.RAM, 0, -22, 0, 2, 3);
                })
                .look(-2, 0, 48)
                .until("the rams and the centre", 10, st -> target(st) != null && st.handles(0, Ids.RAM).length >= 2)
                .say("Two rams against an enemy civic centre")
                .pause(2)
                .run(st -> {
                    BuildingView c = target(st);
                    if (c != null) {
                        st.admin(Attack.replace(UnitSet.of(st.handles(0, Ids.RAM)), c.handle()));
                    }
                })
                .say("Rams hit buildings only; spears barely scratch them")
                .until("the first damage", 60, st -> hurt(st, 0.95))
                .say("Each blow cracks the walls")
                .until("a third gone", 120, st -> hurt(st, 0.65))
                .say("Blocks fall as it passes 85, 65 and 35 % health")
                .fast("the centre razed", 400, st -> target(st) == null)
                .say("Razed: the rams stand among the rubble")
                .pause(5);
    }

    private static @Nullable BuildingView target(DemoStage st) {
        List<BuildingView> b = st.buildings(1, Ids.CIVIC_CENTRE);
        return b.isEmpty() ? null : b.getFirst();
    }

    private static boolean hurt(DemoStage st, double below) {
        BuildingView c = target(st);
        return c == null || c.hitPoints() < below * c.maxHitPoints();
    }
}
