package dev.strategos.devtools.demo;

import dev.strategos.core.protocol.AttackMove;
import dev.strategos.core.protocol.Garrison;
import dev.strategos.core.protocol.SetRally;
import dev.strategos.core.protocol.Train;
import dev.strategos.core.protocol.Ungarrison;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.sim.build.BuildingView;
import java.util.List;

/** M9: wounded soldiers heal inside the civic centre; a rally point on a tower garrisons the units trained after. */
final class GarrisonHealChapter implements DemoChapter {
    static final int OURS = 6;
    static final int ENEMIES = 4;
    static final int CENTRE_DX = -24;
    static final int BARRACKS_DX = -24;
    static final int BARRACKS_DZ = -24;
    static final int TOWER_DX = 24;
    static final int TOWER_DZ = 10;
    static final int RECRUITS = 3;

    @Override
    public String id() {
        return "garrison_heal";
    }

    @Override
    public String title() {
        return "Healing and rallying inside";
    }

    @Override
    public List<String> groups() {
        return List.of("m9", "buildings", "combat");
    }

    @Override
    public void script(DemoScript s) {
        s.run(Stage::match)
                .run(st -> {
                    st.building(Ids.CIVIC_CENTRE, 0, CENTRE_DX, 0);
                    st.building(Ids.BARRACKS, 0, BARRACKS_DX, BARRACKS_DZ);
                    st.units(Ids.INFANTRY, 0, 10, 24, OURS, 1.5);
                    st.units(Ids.INFANTRY, 1, 34, 24, ENEMIES, 1.5);
                    Chapters.grantAll(st, 0);
                })
                .look(0, 8, 56)
                .until(
                        "both sides and the centre",
                        10,
                        st -> Chapters.finished(st, 0, Ids.CIVIC_CENTRE) != null
                                && Fights.alive(st, 0, Ids.INFANTRY) >= OURS
                                && Fights.alive(st, 1, Ids.INFANTRY) >= ENEMIES)
                .say("Six spearmen meet four raiders")
                .run(st -> st.admin(AttackMove.replace(UnitSet.of(st.handles(1, Ids.INFANTRY)), st.at(10, 24))))
                .until("the raiders beaten", 90, st -> Fights.alive(st, 1, Ids.INFANTRY) == 0)
                .until("ours wounded", 5, st -> Fights.hurt(st, 0, Ids.INFANTRY) > 0)
                .say("The raid is broken, and ours are hurt")
                .pause(2)
                .run(st -> {
                    BuildingView c = Chapters.finished(st, 0, Ids.CIVIC_CENTRE);
                    if (c != null) {
                        st.order(0, new Garrison(UnitSet.of(st.handles(0, Ids.INFANTRY)), c.handle()));
                    }
                })
                .say("They garrison the civic centre")
                .until("all inside", 40, st -> Chapters.garrisoned(st, 0) == st.handles(0, Ids.INFANTRY).length)
                .say("Inside, each heals a hit point a second")
                .fast("everyone healed", 200, st -> Fights.hurt(st, 0, Ids.INFANTRY) == 0)
                .run(st -> {
                    BuildingView c = Chapters.finished(st, 0, Ids.CIVIC_CENTRE);
                    if (c != null) {
                        st.order(0, new Ungarrison(c.handle(), UnitSet.of()));
                    }
                })
                .say("Healed: they walk out whole")
                .until("all out", 20, st -> Chapters.garrisoned(st, 0) == 0)
                .pause(3)
                .run(st -> st.building(Ids.SENTRY_TOWER, 0, TOWER_DX, TOWER_DZ))
                .until("the tower", 10, st -> Chapters.finished(st, 0, Ids.SENTRY_TOWER) != null)
                .say("The barracks rallies on the sentry tower")
                .run(st -> {
                    BuildingView b = Chapters.finished(st, 0, Ids.BARRACKS);
                    BuildingView t = Chapters.finished(st, 0, Ids.SENTRY_TOWER);
                    if (b != null && t != null) {
                        st.order(
                                0,
                                new SetRally(
                                        UnitSet.of(b.handle()),
                                        t.footprint().anchorX() + 1,
                                        t.baseY(),
                                        t.footprint().anchorZ() + 1));
                        for (int k = 0; k < RECRUITS; k++) {
                            st.order(0, new Train(b.handle(), Ids.INFANTRY));
                        }
                    }
                })
                .say("New spearmen go straight into the tower")
                .fast("the recruits garrisoned", 120, st -> Chapters.garrisoned(st, 0) >= RECRUITS)
                .say("Garrisoned: each adds an arrow")
                .pause(4);
    }
}
