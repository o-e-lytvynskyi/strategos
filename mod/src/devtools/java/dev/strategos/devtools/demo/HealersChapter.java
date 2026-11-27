package dev.strategos.devtools.demo;

import dev.strategos.core.protocol.AttackMove;
import dev.strategos.core.protocol.Garrison;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.Train;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.sim.build.BuildingView;
import java.util.List;

/** M10: a healer trained at the temple heals the wounded; wounded troops garrisoned in the temple heal as well. */
final class HealersChapter implements DemoChapter {
    static final int OURS = 6;
    static final int ENEMIES = 4;
    static final int TEMPLE_DX = -8;
    static final int TEMPLE_DZ = 4;

    @Override
    public String id() {
        return "healers";
    }

    @Override
    public String title() {
        return "Healers and the temple";
    }

    @Override
    public List<String> groups() {
        return List.of("m10", "combat", "buildings");
    }

    @Override
    public void script(DemoScript s) {
        double[] hurtHp = {0}; // the troops' hit points when the fight ended
        s.run(Stage::match)
                .run(st -> {
                    st.building(Ids.CIVIC_CENTRE, 0, -28, 0); // a home for the population
                    st.building(Ids.TEMPLE, 0, TEMPLE_DX, TEMPLE_DZ);
                    st.units(Ids.INFANTRY, 0, 0, 12, OURS, 1.5);
                    st.units(Ids.INFANTRY, 1, 28, 12, ENEMIES, 1.5);
                    Chapters.grantAll(st, 0);
                })
                .look(4, 6, 56)
                .until(
                        "both sides and the temple",
                        10,
                        st -> Chapters.finished(st, 0, Ids.TEMPLE) != null
                                && Fights.alive(st, 0, Ids.INFANTRY) >= OURS
                                && Fights.alive(st, 1, Ids.INFANTRY) >= ENEMIES)
                .say("Six spearmen meet four raiders")
                .run(st -> st.admin(AttackMove.replace(UnitSet.of(st.handles(1, Ids.INFANTRY)), st.at(0, 12))))
                .until("the raiders beaten", 90, st -> Fights.alive(st, 1, Ids.INFANTRY) == 0)
                .until("ours wounded", 5, st -> Fights.hurt(st, 0, Ids.INFANTRY) > 0)
                .run(st -> hurtHp[0] = Chapters.hitPoints(st, 0, Ids.INFANTRY))
                .say("The raid is broken, and ours are hurt")
                .pause(2)
                .say("The temple trains a healer")
                .run(st -> {
                    BuildingView t = Chapters.finished(st, 0, Ids.TEMPLE);
                    if (t != null) {
                        st.admin(Move.replace(UnitSet.of(st.handles(0, Ids.INFANTRY)), st.at(0, 6)));
                        st.order(0, new Train(t.handle(), Ids.HEALER));
                    }
                })
                .fast("the healer", 90, st -> Fights.alive(st, 0, Ids.HEALER) >= 1)
                .say("The healer mends the most wounded first")
                .until("someone mended", 30, st -> Chapters.hitPoints(st, 0, Ids.INFANTRY) > hurtHp[0])
                .say("Wounded spearmen go into the temple to heal too")
                .run(st -> {
                    BuildingView t = Chapters.finished(st, 0, Ids.TEMPLE);
                    if (t != null) {
                        int[] ours = st.handles(0, Ids.INFANTRY);
                        st.order(0, new Garrison(UnitSet.of(DemoStage.part(ours, 0, ours.length / 2)), t.handle()));
                    }
                })
                .until("some inside", 40, st -> Chapters.garrisoned(st, 0) > 0)
                .fast("everyone healed", 200, st -> Fights.hurt(st, 0, Ids.INFANTRY) == 0)
                .say("All healed: outside by the healer, inside by the temple")
                .pause(4);
    }
}
