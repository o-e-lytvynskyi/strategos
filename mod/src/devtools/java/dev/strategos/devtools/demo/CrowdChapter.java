package dev.strategos.devtools.demo;

import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.UnitSet;
import java.util.List;
import net.minecraft.world.level.block.Blocks;

/** M1: a crowd of forty finds its way round a wall to the far side and back, flowing round each other. */
final class CrowdChapter implements DemoChapter {
    private static final int COUNT = 40;

    @Override
    public String id() {
        return "crowd";
    }

    @Override
    public String title() {
        return "A crowd round a wall";
    }

    @Override
    public List<String> groups() {
        return List.of("m1", "movement");
    }

    @Override
    public void script(DemoScript s) {
        s.run(st -> st.fill(0, 0, -14, 1, 2, 14, Blocks.STONE_BRICKS.defaultBlockState()))
                .run(DemoStage::match)
                .run(st -> st.units(Ids.INFANTRY, 0, -20, 0, COUNT, 5))
                .look(0, 0, 55)
                .until("the crowd", 10, st -> st.handles(0, Ids.INFANTRY).length >= COUNT)
                .say("Forty soldiers, one order: to the far side of the wall")
                .run(st -> st.admin(Move.replace(UnitSet.of(st.handles(0, Ids.INFANTRY)), st.at(20, 0))))
                .until("the crowd past the wall", 60, st -> arrived(st, 20, 0))
                .pause(2)
                .say("Round the wall's ends, nobody walking through anybody")
                .run(st -> st.admin(Move.replace(UnitSet.of(st.handles(0, Ids.INFANTRY)), st.at(-20, 6))))
                .until("the crowd back", 60, st -> arrived(st, -20, 6))
                .pause(2);
    }

    /** Most of the crowd round its goal: a crowd of forty spreads some six blocks from it. */
    private static boolean arrived(DemoStage st, double dx, double dz) {
        int[] units = st.handles(0, Ids.INFANTRY);
        return st.near(units, dx, dz, 12) >= units.length * 8 / 10;
    }
}
