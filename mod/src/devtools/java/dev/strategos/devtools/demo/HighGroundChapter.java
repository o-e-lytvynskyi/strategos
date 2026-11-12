package dev.strategos.devtools.demo;

import dev.strategos.core.protocol.SetStance;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.Stance;
import java.util.List;
import net.minecraft.world.level.block.Blocks;

/** M5: archers on a six-block plateau reach archers on the plain, which cannot answer: the bonus is range. */
final class HighGroundChapter implements DemoChapter {
    @Override
    public String id() {
        return "high-ground";
    }

    @Override
    public String title() {
        return "High ground";
    }

    @Override
    public List<String> groups() {
        return List.of("m5", "combat");
    }

    @Override
    public void script(DemoScript s) {
        s.run(st -> st.fill(-18, 0, -5, -10, 5, 5, Blocks.STONE.defaultBlockState()))
                .run(DemoStage::match)
                .run(st -> {
                    st.units(Ids.ARCHER, 0, -14, 0, 3, 0.5);
                    st.units(Ids.ARCHER, 1, 7.5, 0, 3, 0.5);
                })
                .look(-3, 0, 40)
                .until(
                        "both bows",
                        10,
                        st -> st.handles(0, Ids.ARCHER).length >= 3 && st.handles(1, Ids.ARCHER).length >= 3)
                .run(st -> {
                    st.admin(new SetStance(UnitSet.of(st.handles(0, Ids.ARCHER)), Stance.STAND_GROUND));
                    st.admin(new SetStance(UnitSet.of(st.handles(1, Ids.ARCHER)), Stance.STAND_GROUND));
                })
                .say("Both hold their ground, 21 blocks apart")
                .pause(2)
                .say("Archers on the rock reach farther than a bow's range")
                .until("a hit from the rock", 40, st -> Fights.hurt(st, 1, Ids.ARCHER) > 0)
                .say("Those below cannot answer: the hill is the range")
                .pause(6);
    }
}
