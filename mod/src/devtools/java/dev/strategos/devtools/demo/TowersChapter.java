package dev.strategos.devtools.demo;

import dev.strategos.core.protocol.AttackMove;
import dev.strategos.core.protocol.Garrison;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.protocol.UpgradeBuilding;
import dev.strategos.core.sim.build.BuildingView;
import java.util.List;
import net.minecraft.world.level.block.Blocks;

/** M9: a sentry tower at a chokepoint, garrisoned; in Town it upgrades in place to a defence tower and keeps them. */
final class TowersChapter implements DemoChapter {
    static final int CENTRE_DX = -12;
    static final int TOWER_DX = 14;
    static final int WALL_DX = 20;
    static final int GAP = 4;
    static final int GARRISON = 3;
    static final int RAIDERS = 4;

    @Override
    public String id() {
        return "towers";
    }

    @Override
    public String title() {
        return "Towers at the chokepoint";
    }

    @Override
    public List<String> groups() {
        return List.of("m9", "buildings", "combat");
    }

    @Override
    public void script(DemoScript s) {
        Watch watch = new Watch();
        int[] towerHandle = new int[1];
        s.run(st -> {
                    var stone = Blocks.COBBLESTONE.defaultBlockState();
                    st.fill(WALL_DX, 0, -30, WALL_DX, 2, -GAP - 1, stone);
                    st.fill(WALL_DX, 0, GAP + 1, WALL_DX, 2, 30, stone);
                    st.match();
                    watch.attach(st);
                })
                .run(st -> {
                    Chapters.village(st, CENTRE_DX, 0);
                    Chapters.grantAll(st, 0);
                    st.building(Ids.SENTRY_TOWER, 0, TOWER_DX, 0);
                    st.units(Ids.INFANTRY, 0, TOWER_DX - 6, 8, GARRISON, 1.5);
                })
                .look(4, 0, 56)
                .until(
                        "the village, the tower and its guards",
                        10,
                        st -> Chapters.finished(st, 0, Ids.BARRACKS) != null
                                && Chapters.finished(st, 0, Ids.SENTRY_TOWER) != null
                                && Fights.alive(st, 0, Ids.INFANTRY) >= GARRISON)
                .say("Two walls of rock leave one gap: the chokepoint")
                .pause(3)
                .say("A sentry tower covers it, and three men go in")
                .run(st -> {
                    BuildingView t = Chapters.finished(st, 0, Ids.SENTRY_TOWER);
                    if (t != null) {
                        towerHandle[0] = t.handle();
                        st.order(0, new Garrison(UnitSet.of(st.handles(0, Ids.INFANTRY)), t.handle()));
                    }
                })
                .until("the guards inside", 40, st -> Chapters.garrisoned(st, 0) >= GARRISON)
                .say("Raiders come for the town through the gap")
                .run(st -> st.units(Ids.INFANTRY, 1, 42, 0, RAIDERS, 2))
                .until("the raiders", 10, st -> Fights.alive(st, 1, Ids.INFANTRY) >= RAIDERS)
                .run(st -> st.admin(AttackMove.replace(UnitSet.of(st.handles(1, Ids.INFANTRY)), st.at(CENTRE_DX, 0))))
                .until("the raid broken", 120, st -> Fights.alive(st, 1, Ids.INFANTRY) == 0)
                .say("The tower and its garrison broke the raid")
                .pause(2)
                .say("Town: the sentry tower can be upgraded")
                .run(Chapters::researchTown)
                .fast("Town reached", 150, st -> watch.researched(Ids.PHASE_TOWN))
                .run(st -> st.order(0, new UpgradeBuilding(towerHandle[0])))
                .say("The upgrade runs in place; the tower stays")
                .fast(
                        "the defence tower",
                        120,
                        st -> !st.buildings(0, Ids.TOWER).isEmpty())
                .run(st -> {
                    Chapters.require(
                            st.buildings(0, Ids.TOWER).getFirst().handle() == towerHandle[0],
                            "the upgrade made another building");
                    Chapters.require(
                            Chapters.garrisoned(st, 0) >= GARRISON, "the garrison did not stay in the upgraded tower");
                })
                .say("Upgraded, and the garrison is still inside")
                .pause(5);
    }
}
