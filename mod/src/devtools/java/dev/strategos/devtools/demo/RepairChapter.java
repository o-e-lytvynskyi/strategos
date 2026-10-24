package dev.strategos.devtools.demo;

import dev.strategos.core.protocol.Build;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.sim.ExternalDamage;
import dev.strategos.core.sim.build.BuildingView;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** M4: a house knocked down through its damage stages, then repaired back to whole by workers. */
final class RepairChapter implements DemoChapter {
    @Override
    public String id() {
        return "repair";
    }

    @Override
    public String title() {
        return "Damage and repair";
    }

    @Override
    public List<String> groups() {
        return List.of("m4", "construction");
    }

    @Override
    public void script(DemoScript s) {
        s.run(DemoStage::match)
                .run(st -> {
                    st.building(Ids.HOUSE, 0, 0, 0);
                    st.units(Ids.WORKER, 0, -8, 6, 3, 1);
                })
                .look(0, 0, 24)
                .until("the house", 10, st -> house(st) != null && st.handles(0, Ids.WORKER).length >= 3)
                .say("A house, whole")
                .pause(2)
                .say("Blows: it loses blocks at 85, 65 and 35 % health")
                .run(st -> hit(st, 0.2))
                .pause(3)
                .run(st -> hit(st, 0.2))
                .pause(3)
                .run(st -> hit(st, 0.3))
                .pause(3)
                .say("Three workers repair it; the blocks come back")
                .run(st -> {
                    BuildingView h = house(st);
                    if (h != null) {
                        st.admin(Build.replace(UnitSet.of(st.handles(0, Ids.WORKER)), h.handle()));
                    }
                })
                .until("the repair begun", 30, st -> {
                    BuildingView h = house(st);
                    return h != null && h.hitPoints() > 0.3 * h.maxHitPoints() + 1;
                })
                .fast("the house whole", 300, st -> {
                    BuildingView h = house(st);
                    return h != null && h.hitPoints() >= h.maxHitPoints();
                })
                .say("Whole again; repair costs nothing but time")
                .pause(3);
    }

    private static void hit(DemoStage st, double share) {
        BuildingView h = house(st);
        if (h == null) {
            throw new IllegalStateException("no house to hit");
        }
        st.rt()
                .damage(new ExternalDamage(
                        h.handle(),
                        ExternalDamage.Kind.CRUSH,
                        (float) (share * h.maxHitPoints()),
                        ExternalDamage.SourceKind.NONE));
    }

    static @Nullable BuildingView house(DemoStage st) {
        List<BuildingView> houses = st.buildings(0, Ids.HOUSE);
        return houses.isEmpty() ? null : houses.getFirst();
    }
}
