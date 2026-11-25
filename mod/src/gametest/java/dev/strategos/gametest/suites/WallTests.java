package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;
import static dev.strategos.gametest.suites.Units.handles;
import static dev.strategos.gametest.suites.Units.unit;

import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.AttackMove;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.PlaceWall;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.BuildingView;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.world.BlockWriter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Palisades and gates in a real level (BLD-13, PATH-12; M9 spec §2.10): a placed and built line equals its layout, a
 * gate lets its owner through and an enemy not, and an enemy attack-move breaches the wall in its way.
 */
public final class WallTests {
    static final TemplateId PALISADE = new TemplateId("strategos:building/palisade");
    static final TemplateId GATE = new TemplateId("strategos:building/palisade_gate");
    private static final FactionSlot F0 = new FactionSlot(0);
    private static final FactionSlot F1 = new FactionSlot(1);
    private static final PlayerSlot P0 = new PlayerSlot(0);

    /** The wall's column and its gate: a wall along Z at x 16, the gate's columns at z 13 and 14. */
    static final int WALL_X = 16;

    static final int GATE_Z = 12;

    private WallTests() {}

    /** A finished wall across the arena (32 deep) at {@link #WALL_X}: palisades with one gate, placed whole by an ADMIN. */
    static void wallAcross(MatchRuntime rt) {
        for (int z = 0; z < 32; z += 4) {
            rt.submit(PlayerSlot.ADMIN, new SpawnBuilding(z == GATE_Z ? GATE : PALISADE, F0, WALL_X, z, 1));
        }
    }

    /** "Not yet" until every segment of the wall and its gate stand in the core. */
    static void wallStands(MatchRuntime rt) {
        check(core(rt).queries().buildings().size() == 8, "the wall is not all there");
        check(core(rt).queries().gateDoors().size() == 1, "no gate yet");
        check(core(rt).queries().footprintAt(WALL_X, 0), "the footprints are not blocked yet");
    }

    /** Whether a fence gate of the wall's gate stands open now. */
    static boolean gateBlocksOpen(GameTestHelper h, MatchRuntime rt) {
        List<dev.strategos.core.sim.build.GateDoor> doors = core(rt).queries().gateDoors();
        check(!doors.isEmpty(), "no gate");
        dev.strategos.core.sim.build.GateDoor door = doors.get(0);
        boolean open = false;
        for (int column : door.columns()) {
            BlockPos pos = new BlockPos(
                    rt.space().origin().getX() + (column >>> 16),
                    door.baseY(),
                    rt.space().origin().getZ() + (column & 0xFFFF));
            BlockState state = h.getLevel().getBlockState(pos);
            if (state.getBlock() instanceof FenceGateBlock) {
                open |= state.getValue(FenceGateBlock.OPEN);
            }
        }
        return open;
    }

    /** The level's blocks over building {@code b}'s blueprint equal it; a fence gate's open state is the door's. */
    static void equalsLayout(GameTestHelper h, MatchRuntime rt, BuildingView b) {
        Blueprint bp = Objects.requireNonNull(core(rt).queries()
                .templates()
                .blueprint(Objects.requireNonNull(core(rt).queries().templates().byId(b.template()))
                        .netIndex()));
        Footprint at = b.footprint();
        BlockWriter writer = StrategosServer.get().writer();
        BlockPos origin = rt.space().origin();
        for (int k = 0; k < bp.count(); k++) {
            BlockPos pos = new BlockPos(
                    origin.getX() + at.worldX(bp.x(k), bp.z(k)),
                    b.baseY() + bp.y(k),
                    origin.getZ() + at.worldZ(bp.x(k), bp.z(k)));
            BlockState want = writer.state(bp.palette().get(bp.state(k)));
            BlockState got = h.getLevel().getBlockState(pos);
            boolean same;
            if (want.getBlock()
                    instanceof FenceGateBlock) { // the door's open state and facing are the core's, not the layout's
                same = got.is(want.getBlock())
                        && got.setValue(FenceGateBlock.OPEN, want.getValue(FenceGateBlock.OPEN))
                                .setValue(FenceGateBlock.FACING, want.getValue(FenceGateBlock.FACING))
                                .equals(want);
            } else if (want.getBlock()
                    instanceof FenceBlock) { // a fence's arms are the world's: it joins what it touches
                same = got.is(want.getBlock());
            } else {
                same = got.equals(want);
            }
            check(same, pos + " is " + got + ", not " + want);
        }
    }

    /**
     * BLD-13.1: a line from (12, 20) to (27, 20), the second of its four segments a gate, laid by PlaceWall for three
     * workers and built: its segments are at x 12, 16, 20 and 24 end to end, the second the gate template, and the
     * level's blocks equal each one's blueprint; the gate stands as a gate in the core.
     */
    @StrategosTest(timeoutTicks = 3_000)
    public static void segments_match_layout(GameTestHelper h) {
        MatchRuntime rt = Units.match(h);
        ConstructionTests.town(rt, 3);
        h.startSequence()
                .thenWaitUntil(() -> check(handles(rt).length == 3, "not spawned yet"))
                .thenExecute(() ->
                        rt.submit(P0, new PlaceWall(PALISADE, 12, 20, 27, 20, List.of(1), UnitSet.of(handles(rt)))))
                .thenWaitUntil(() -> {
                    List<BuildingView> line = line(rt);
                    check(line.size() == 4, line.size() + " segments, not 4");
                    for (BuildingView b : line) {
                        check(b.state() == BuildingStore.COMPLETE, "a segment is not finished");
                        equalsLayout(h, rt, b);
                    }
                })
                .thenExecute(() -> {
                    List<BuildingView> line = line(rt);
                    for (int k = 0; k < 4; k++) {
                        check(line.get(k).footprint().anchorX() == 12 + 4 * k, "segment " + k + " is off its place");
                        check(line.get(k).footprint().anchorZ() == 20, "segment " + k + " is off its row");
                        check(
                                line.get(k).template().equals(k == 1 ? GATE : PALISADE),
                                "segment " + k + " is " + line.get(k).template());
                    }
                    check(core(rt).queries().gateDoors().size() == 1, "the gate is no gate in the core");
                })
                .thenSucceed();
    }

    private static List<BuildingView> line(MatchRuntime rt) {
        return core(rt).queries().buildings().stream()
                .filter(b -> b.template().equals(PALISADE) || b.template().equals(GATE))
                .sorted(Comparator.comparingInt(b -> b.footprint().anchorX()))
                .toList();
    }

    /**
     * PATH-12.1 on bodies: a wall of faction 0 across the arena with a gate; faction 0's unit west of it, ordered east
     * through the gate, ends on the far side and its fence gates open while it is near; faction 1's unit, ordered the
     * same, ends its move at the foot of the wall and never passes it. (AFTER-M7: the same with an allied faction, when
     * teams are in a match; the L1 {@code GateMovementTest} covers allies with a fake {@code Allies}.)
     */
    @StrategosTest(timeoutTicks = 1_500)
    public static void gate_allies_only(GameTestHelper h) {
        MatchRuntime rt = Units.match(h);
        wallAcross(rt);
        var state = new Object() {
            int own = -1;
            int enemy = -1;
            boolean ordered;
            boolean opened;
            boolean blocksOpened;
        };
        h.onEachTick(() -> {
            if (!state.ordered) {
                return;
            }
            state.blocksOpened |= gateBlocksOpen(h, rt);
            if (state.enemy >= 0 && unit(rt, state.enemy).x() >= WALL_X) {
                h.fail("the enemy passed the wall at " + unit(rt, state.enemy).x());
            }
            state.opened |= !core(rt).queries().gateDoors().isEmpty()
                    && core(rt).queries().gateDoors().get(0).open();
        });
        h.startSequence()
                .thenWaitUntil(() -> wallStands(rt))
                .thenExecute(() -> {
                    rt.submit(
                            PlayerSlot.ADMIN,
                            new SpawnUnits(TemplateSet.DEBUG_WALKER, F0, new Vec2d(10.5, 13.5), 1, 0));
                    rt.submit(
                            PlayerSlot.ADMIN,
                            new SpawnUnits(TemplateSet.DEBUG_WALKER, F1, new Vec2d(10.5, 21.5), 1, 0));
                })
                .thenWaitUntil(() -> check(handles(rt).length == 2, "not spawned yet"))
                .thenExecute(() -> {
                    for (int handle : handles(rt)) {
                        UnitView u = unit(rt, handle);
                        if (u.faction().value() == 0) {
                            state.own = handle;
                        } else {
                            state.enemy = handle;
                        }
                    }
                    // the owner walks to the gate's foot first, and waits there until the writer has opened the blocks
                    rt.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(state.own), new Vec2d(14.5, 13.5)));
                    rt.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(state.enemy), new Vec2d(22.5, 21.5)));
                    state.ordered = true;
                })
                .thenWaitUntil(() -> {
                    check(state.opened, "its gate never opened for it");
                    check(state.blocksOpened, "its fence gates never opened");
                })
                .thenExecute(
                        () -> rt.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(state.own), new Vec2d(22.5, 13.5))))
                .thenWaitUntil(() -> {
                    UnitView own = unit(rt, state.own);
                    check(own.x() == 22.5 && own.z() == 13.5, "the owner is at " + own.x() + ", " + own.z());
                    check(!gateBlocksOpen(h, rt), "its fence gates are still open with nobody near");
                    UnitView enemy = unit(rt, state.enemy);
                    check(
                            enemy.x() > WALL_X - 3 && enemy.state() == UnitState.IDLE,
                            "the enemy has not reached the wall");
                })
                .thenExecute(() -> {
                    check(unit(rt, state.enemy).x() < WALL_X, "the enemy is past the wall");
                    dev.strategos.core.sim.build.GateDoor door =
                            core(rt).queries().gateDoors().get(0);
                    BlockPos pos = new BlockPos(
                            rt.space().origin().getX() + (door.columns().get(0) >>> 16),
                            door.baseY(),
                            rt.space().origin().getZ() + (door.columns().get(0) & 0xFFFF));
                    check(
                            h.getLevel().getBlockState(pos).getValue(FenceGateBlock.FACING)
                                    == net.minecraft.core.Direction.EAST,
                            "a gate in a wall along Z does not face east");
                })
                .thenSucceed();
    }

    /**
     * BLD-13.3: a squad of faction 1 attack-moving east across the closed wall at x 16 walks to its foot, attacks a
     * segment, and goes through the gap the fallen segment leaves: the wall has fewer buildings and the squad is east of it.
     */
    @StrategosTest(timeoutTicks = 4_000)
    public static void enemies_around_or_through(GameTestHelper h) {
        MatchRuntime rt = Units.match(h);
        wallAcross(rt);
        var state = new Object() {
            int[] squad = new int[0];
        };
        h.startSequence()
                .thenWaitUntil(() -> wallStands(rt))
                .thenExecute(() -> rt.submit(
                        PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.INFANTRY, F1, new Vec2d(10.5, 22.5), 3, 1.5)))
                .thenWaitUntil(() -> check(handles(rt).length == 3, "not spawned yet"))
                .thenExecute(() -> {
                    state.squad = handles(rt);
                    rt.submit(PlayerSlot.ADMIN, AttackMove.replace(UnitSet.of(state.squad), new Vec2d(24.5, 22.5)));
                })
                .thenWaitUntil(() -> {
                    check(core(rt).queries().buildings().size() < 8, "the wall still stands whole");
                    for (int handle : state.squad) {
                        check(unit(rt, handle).x() > WALL_X + 1, "a soldier is still west of the wall");
                    }
                })
                .thenSucceed();
    }
}
