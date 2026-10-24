package dev.strategos.devtools.demo;

import dev.strategos.core.sim.build.BuildingView;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;

/** M4: TNT goes off beside a house and a piston pushes at it: no block of the house moves; the blast hurts it instead. */
final class ProtectionChapter implements DemoChapter {
    @Override
    public String id() {
        return "protection";
    }

    @Override
    public String title() {
        return "Buildings change only by the rules";
    }

    @Override
    public List<String> groups() {
        return List.of("m4", "construction");
    }

    @Override
    public void script(DemoScript s) {
        s.run(DemoStage::match)
                .run(st -> st.building(Ids.HOUSE, 0, 0, 0))
                .look(0, 0, 24)
                .until("the house", 10, st -> RepairChapter.house(st) != null)
                .say("A powered piston at the wall: it does not move")
                .run(st -> {
                    BuildingView h = house(st);
                    BlockPos west = new BlockPos(
                            (int) st.rt().space().worldX(h.footprint().anchorX()) - 1, st.floor(), (int) st.rt()
                                    .space()
                                    .worldZ(h.footprint().anchorZ()
                                            + h.footprint().spanZ() / 2));
                    st.level()
                            .setBlockAndUpdate(
                                    west,
                                    Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
                    st.level().setBlockAndUpdate(west.west(), Blocks.REDSTONE_BLOCK.defaultBlockState());
                })
                .pause(4)
                .say("TNT against its wall")
                .run(st -> {
                    BuildingView h = house(st);
                    st.command(String.format(
                            java.util.Locale.ROOT,
                            "summon tnt %d %d %d {fuse:60}",
                            (int) st.rt()
                                    .space()
                                    .worldX(h.footprint().anchorX()
                                            + h.footprint().spanX() / 2),
                            st.floor(),
                            (int) st.rt()
                                    .space()
                                    .worldZ(h.footprint().anchorZ()
                                            + h.footprint().spanZ())));
                })
                .pause(4)
                .say("The ground goes; the house stands, and takes damage")
                .until("the house hurt", 10, st -> {
                    BuildingView h = RepairChapter.house(st);
                    return h != null && h.hitPoints() < h.maxHitPoints();
                })
                .pause(4);
    }

    private static BuildingView house(DemoStage st) {
        BuildingView h = RepairChapter.house(st);
        if (h == null) {
            throw new IllegalStateException("no house");
        }
        return h;
    }
}
