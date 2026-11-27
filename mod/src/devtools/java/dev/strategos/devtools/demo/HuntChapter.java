package dev.strategos.devtools.demo;

import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.UnitSet;
import java.util.List;
import java.util.Locale;

/** M10: horsemen hunt wild boars and gather the carcass; a kill in a fight pays its loot. */
final class HuntChapter implements DemoChapter {
    static final int HUNTERS = 3;
    static final int BOARS = 3;

    @Override
    public String id() {
        return "hunt";
    }

    @Override
    public String title() {
        return "Hunting and loot";
    }

    @Override
    public List<String> groups() {
        return List.of("m10", "economy");
    }

    @Override
    public void script(DemoScript s) {
        long[] before = new long[2]; // food and wood looted before the kill
        s.run(Stage::match)
                .run(st -> {
                    st.building(Ids.CIVIC_CENTRE, 0, -26, 0);
                    st.units(Ids.HORSEMAN, 0, -8, 0, HUNTERS, 2);
                    st.units(Ids.WILD_BOAR, Ids.GAIA, 12, -4, BOARS, 3);
                })
                .look(-4, 0, 60)
                .until(
                        "the hunters, the boars and the centre",
                        10,
                        st -> Chapters.finished(st, 0, Ids.CIVIC_CENTRE) != null
                                && Fights.alive(st, 0, Ids.HORSEMAN) >= HUNTERS
                                && st.handles(Ids.GAIA, Ids.WILD_BOAR).length >= BOARS)
                .say("Wild boars graze: a herd nobody owns")
                .pause(3)
                .say("Horsemen hunt: only they can bring one down")
                .run(st -> st.order(
                        0,
                        Attack.replace(
                                UnitSet.of(st.handles(0, Ids.HORSEMAN)), st.handles(Ids.GAIA, Ids.WILD_BOAR)[0])))
                .until("a kill and a load home", 90, st -> st.gathered(0, "food") >= 20)
                .say("The kill leaves a carcass; the hunters carry it home")
                .pause(4)
                .say("A fight: a raider walks in")
                .run(st -> {
                    before[0] = Chapters.looted(st, 0, "food");
                    before[1] = Chapters.looted(st, 0, "wood");
                    st.units(Ids.INFANTRY, 1, 4, 14, 1, 0);
                })
                .until("the raider there", 5, st -> Fights.alive(st, 1, Ids.INFANTRY) == 1)
                .run(st -> st.order(
                        0, Attack.replace(UnitSet.of(st.handles(0, Ids.HORSEMAN)), st.handles(1, Ids.INFANTRY)[0])))
                .until("the raider dead", 40, st -> Fights.alive(st, 1, Ids.INFANTRY) == 0)
                .run(st -> DemoDirector.say(
                        st,
                        String.format(
                                Locale.ROOT,
                                "Loot: +%d food, +%d wood from the kill",
                                Chapters.looted(st, 0, "food") - before[0],
                                Chapters.looted(st, 0, "wood") - before[1])))
                .pause(5);
    }
}
