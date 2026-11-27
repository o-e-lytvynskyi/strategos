package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.FormationMove;
import dev.strategos.core.protocol.FormationShape;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.UnitState;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.mod.server.match.MatchRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/** Formations on a real server (ORD-06, M10 spec §2.13): the march through the world's own terrain. */
public final class FormationTests {
    private static final TemplateId ARCHER = new TemplateId("strategos:unit/archer");
    private static final int MELEE = 6;
    private static final int RANGED = 4;

    private FormationTests() {}

    /**
     * A box of six spearmen and four archers, mixed in the spawn, marches east across the arena through a wall three
     * high with one opening six wide, at x 24. Every member arrives, still keeping its place in the one formation,
     * within a block of the place it is given, and the ranged stand behind the melee, where the march faces.
     */
    @StrategosTest(arena = "arena_flat_48", timeoutTicks = 2_400)
    public static void march(GameTestHelper h) {
        MatchRuntime rt = Units.match(h);
        for (int z = 0; z < 48; z++) {
            if (z >= 21 && z <= 26) {
                continue;
            }
            for (int y = 2; y <= 4; y++) { // relative y 1 is the floor layer
                h.setBlock(new BlockPos(24, y, z), Blocks.STONE);
            }
        }
        rt.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.INFANTRY, Units.FACTION, new Vec2d(8, 20), MELEE, 1.5));
        rt.submit(PlayerSlot.ADMIN, new SpawnUnits(ARCHER, Units.FACTION, new Vec2d(8, 28), RANGED, 1.5));
        var state = new Object() {
            boolean ordered;
            int wallTicks;
        };
        h.onEachTick(() -> {
            MatchQueries q = core(rt).queries();
            int[] all = q.unitHandles();
            if (all.length != MELEE + RANGED) {
                return;
            }
            if (!state.ordered) {
                state.ordered = true;
                rt.submit(
                        PlayerSlot.ADMIN,
                        FormationMove.replace(UnitSet.of(all), new Vec2d(40, 24), FormationShape.BOX));
                return;
            }
            int formation = q.formationOf(all[0]);
            if (formation == 0) {
                return; // the order has not applied yet
            }
            boolean arrived = true;
            for (int handle : all) {
                var unit = q.unit(handle);
                check(unit != null, "a member of the formation died");
                check(q.formationOf(handle) == formation, "unit " + handle + " left the formation");
                if (unit.x() >= 24 && unit.x() < 25) {
                    state.wallTicks++;
                    check(unit.z() >= 21 && unit.z() < 27, "a member is in the wall at z " + unit.z());
                }
                arrived &= unit.x() > 36 && q.formationSlotError(handle) < 1.0 && unit.state() == UnitState.IDLE;
            }
            if (!arrived) {
                return;
            }
            check(state.wallTicks > 0, "the formation never crossed the wall");
            double melee = 0;
            double ranged = 0;
            for (int handle : all) {
                var unit = q.unit(handle);
                if (unit.template().equals(ARCHER)) {
                    ranged += unit.x() / RANGED;
                } else {
                    melee += unit.x() / MELEE;
                }
            }
            check(melee > ranged + 0.5, "the ranged are not behind the melee: " + ranged + " against " + melee);
            h.succeed();
        });
    }
}
