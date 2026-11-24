package dev.strategos.devtools.demo;

import dev.strategos.core.protocol.AttackMove;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.PlaceWall;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.BuildingView;
import java.util.List;
import net.minecraft.world.level.block.Blocks;

/**
 * M9: a palisade with a gate dragged across a gap between two rocks; its builders raise it, the own units walk through
 * the gate, and the enemy's raiders stop at the wall and attack a segment.
 */
final class PalisadeChapter implements DemoChapter {
    static final TemplateId PALISADE = new TemplateId("strategos:building/palisade");
    static final TemplateId GATE = new TemplateId("strategos:building/palisade_gate");
    static final int CENTRE_DX = -12;
    static final int WALL_DX = 10;
    /** The wall runs from -HALF to +HALF in Z: nine segments of four blocks (the end cell counts). */
    static final int HALF = 16;

    static final int SEGMENTS = 2 * HALF / 4 + 1;
    /** The middle segment: the gate. */
    static final int GATE_INDEX = HALF / 4;

    /** Where the walkers wait out the raid: well behind the wall. */
    static final int GATE_BACK = 8;

    static final int BUILDERS = 6;
    static final int WALKERS = 3;
    static final int RAIDERS = 4;

    @Override
    public String id() {
        return "palisade";
    }

    @Override
    public String title() {
        return "Palisade and gate";
    }

    @Override
    public List<String> groups() {
        return List.of("m9", "buildings");
    }

    /** Whether the wall stands: every segment finished, the gate among them. */
    static boolean wallDone(Stage st) {
        return complete(st, PALISADE) == SEGMENTS - 1 && complete(st, GATE) == 1;
    }

    private static int complete(Stage st, TemplateId template) {
        int n = 0;
        for (BuildingView b : st.buildings(0, template)) {
            if (b.state() == BuildingStore.COMPLETE) {
                n++;
            }
        }
        return n;
    }

    @Override
    public void script(DemoScript s) {
        s.run(st -> {
                    var stone = Blocks.COBBLESTONE.defaultBlockState();
                    // the rocks meet the wall's ends: the last segment, four blocks from +HALF, ends at HALF + 3
                    st.fill(WALL_DX, 0, -30, WALL_DX, 3, -HALF - 1, stone);
                    st.fill(WALL_DX, 0, HALF + 4, WALL_DX, 3, 30, stone);
                    st.match();
                })
                .run(st -> {
                    Chapters.village(st, CENTRE_DX, 0);
                    Chapters.grantAll(st, 0);
                    st.units(Ids.WORKER, 0, CENTRE_DX + 8, 8, BUILDERS, 1.5);
                    st.units(Ids.INFANTRY, 0, CENTRE_DX + 4, -8, WALKERS, 1.5);
                })
                .look(4, 0, 56)
                .until(
                        "the village and its people",
                        10,
                        st -> Chapters.finished(st, 0, Ids.BARRACKS) != null
                                && Fights.alive(st, 0, Ids.WORKER) >= BUILDERS
                                && Fights.alive(st, 0, Ids.INFANTRY) >= WALKERS)
                .say("Two rocks leave a gap; a palisade closes it")
                .pause(2)
                .say("Drag the line across, and name one segment a gate")
                .run(st -> st.order(
                        0,
                        new PlaceWall(
                                PALISADE,
                                (int) Math.floor(st.x(WALL_DX)),
                                (int) Math.floor(st.z(-HALF)),
                                (int) Math.floor(st.x(WALL_DX)),
                                (int) Math.floor(st.z(HALF)),
                                List.of(GATE_INDEX),
                                UnitSet.of(st.handles(0, Ids.WORKER)))))
                .until(
                        "the wall's foundations",
                        10,
                        st -> st.buildings(0, PALISADE).size()
                                        + st.buildings(0, GATE).size()
                                == SEGMENTS)
                .say("The workers raise it segment by segment")
                .fast("the wall done", 900, PalisadeChapter::wallDone)
                .run(st -> Chapters.require(
                        !st.q().gateDoors().isEmpty(), "the finished gate is not registered with the core"))
                .say("Your own units walk through the gate")
                .run(st -> st.order(
                        0, Move.replace(UnitSet.of(st.handles(0, Ids.INFANTRY)), st.at(WALL_DX + 12, GATE_INDEX))))
                .until(
                        "the units on the far side",
                        150,
                        st -> st.near(st.handles(0, Ids.INFANTRY), WALL_DX + 12, GATE_INDEX, 6) >= WALKERS)
                .say("And back, the same way")
                .run(st -> st.order(
                        0, Move.replace(UnitSet.of(st.handles(0, Ids.INFANTRY)), st.at(CENTRE_DX, -GATE_BACK))))
                .until(
                        "the units home",
                        150,
                        st -> st.near(st.handles(0, Ids.INFANTRY), CENTRE_DX, -GATE_BACK, 6) >= WALKERS)
                .say("Raiders come for the village, and meet the wall")
                .run(st -> st.units(Ids.INFANTRY, 1, WALL_DX + 30, 0, RAIDERS, 2))
                .until("the raiders", 10, st -> Fights.alive(st, 1, Ids.INFANTRY) >= RAIDERS)
                .run(st -> st.admin(AttackMove.replace(UnitSet.of(st.handles(1, Ids.INFANTRY)), st.at(CENTRE_DX, 0))))
                // With the rocks in place the raiders can only break a segment; on the fake's open ground they walk
                // round
                // the wall's ends, so the chapter waits for either end of the raid.
                .until("a segment under attack", 400, st -> wallHurt(st) || Fights.alive(st, 1, Ids.INFANTRY) == 0)
                .say("The gate is shut to them: they break a segment instead")
                .pause(5);
    }

    /** Whether a segment of the wall is hurt or gone. */
    static boolean wallHurt(Stage st) {
        int whole = 0;
        for (TemplateId t : List.of(PALISADE, GATE)) {
            for (BuildingView b : st.buildings(0, t)) {
                if (b.hitPoints() < b.maxHitPoints()) {
                    return true;
                }
                whole++;
            }
        }
        return whole < SEGMENTS;
    }
}
