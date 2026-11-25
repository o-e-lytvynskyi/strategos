package dev.strategos.devtools.demo;

import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.NoticeKind;
import dev.strategos.core.protocol.PlaceBuilding;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.Research;
import dev.strategos.core.protocol.Train;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.sim.build.BuildingView;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** M6: the notification feed and its sounds: a villager trained, a house built, a tech researched, an attack. */
final class NoticesChapter implements DemoChapter {
    // the scene's places, plot-relative: the workers start clear of every building, the house goes up beside them
    static final int CENTRE_DX = -12;
    static final int STOREHOUSE_DX = 10;
    static final int WORKERS_DX = 0;
    static final int WORKERS_DZ = 2;
    static final int HOUSE_DZ = 12;

    @Override
    public String id() {
        return "notices";
    }

    @Override
    public String title() {
        return "The feed and its sounds";
    }

    @Override
    public List<String> groups() {
        return List.of("m6", "ui", "audio");
    }

    @Override
    public void script(DemoScript s) {
        Watch watch = new Watch();
        s.run(st -> {
                    st.match();
                    watch.attach(st);
                })
                .run(st -> {
                    st.building(Ids.CIVIC_CENTRE, 0, CENTRE_DX, -6);
                    st.building(Ids.STOREHOUSE, 0, STOREHOUSE_DX, -6);
                    st.units(Ids.WORKER, 0, WORKERS_DX, WORKERS_DZ, 4, 1.5);
                    st.admin(new Grant(new FactionSlot(0), "food", 300));
                    st.admin(new Grant(new FactionSlot(0), "wood", 300));
                })
                .look(0, 0, 40)
                .until("the town", 10, st -> centre(st) != null && st.handles(0, Ids.WORKER).length >= 4)
                .say("A villager is trained: the cheer, and a feed line")
                .run(st -> {
                    BuildingView c = centre(st);
                    if (c != null) {
                        st.order(0, new Train(c.handle(), Ids.WORKER));
                    }
                })
                .fast("the villager out", 60, st -> watch.notices(NoticeKind.TRAINED) > 0)
                .pause(3)
                .say("A house is built: the anvil")
                .run(st -> {
                    int[] at = st.corner(Ids.HOUSE, 0, HOUSE_DZ);
                    int[] w = st.handles(0, Ids.WORKER);
                    st.order(
                            0,
                            new PlaceBuilding(
                                    Ids.HOUSE,
                                    at[0],
                                    at[1],
                                    0,
                                    UnitSet.of(DemoStage.part(w, 0, 4)),
                                    QueueMode.REPLACE));
                })
                .fast("the house built", 200, st -> watch.notices(NoticeKind.BUILT) > 0)
                .pause(3)
                .say("A technology is researched: the level-up chime")
                .run(st -> {
                    List<BuildingView> b = st.buildings(0, Ids.STOREHOUSE);
                    if (!b.isEmpty()) {
                        st.order(0, new Research(b.getFirst().handle(), Ids.WOOD_1));
                    }
                })
                .fast("the research done", 120, st -> watch.researched(Ids.WOOD_1))
                .pause(3)
                .say("Under attack: the bell, and the alert's place")
                .run(st -> st.units(Ids.INFANTRY, 1, 30, 10, 2, 1.5))
                // the spawn is a command: the raiders exist from the next tick, so the order waits for them
                .until("the raiders", 10, st -> st.handles(1, Ids.INFANTRY).length >= 2)
                .run(st -> {
                    int[] w = st.handles(0, Ids.WORKER);
                    if (w.length > 0) { // the raiders go for a worker itself: a point beside the house may be its walls
                        st.admin(Attack.replace(UnitSet.of(st.handles(1, Ids.INFANTRY)), w[0]));
                    }
                })
                .until("a worker hurt", 60, st -> Fights.hurt(st, 0, Ids.WORKER) > 0)
                .pause(6);
    }

    private static @Nullable BuildingView centre(Stage st) {
        List<BuildingView> b = st.buildings(0, Ids.CIVIC_CENTRE);
        return b.isEmpty() ? null : b.getFirst();
    }
}
