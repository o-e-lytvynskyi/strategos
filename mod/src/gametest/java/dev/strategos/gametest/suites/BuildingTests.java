package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;
import static dev.strategos.gametest.suites.Units.handles;
import static dev.strategos.gametest.suites.Units.match;
import static dev.strategos.gametest.suites.Units.move;
import static dev.strategos.gametest.suites.Units.spawn;
import static dev.strategos.gametest.suites.Units.unit;

import dev.strategos.core.protocol.BuildingState;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.build.BuildingView;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.gametest.net.MockViewer;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.net.payload.BuildingStatePayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Buildings in a real level (BLD-01): placed, walked round, told to their owners. */
public final class BuildingTests {
    private static final TemplateId CIVIC_CENTRE = new TemplateId("strategos:building/civic_centre");
    private static final TemplateId HOUSE = new TemplateId("strategos:building/house");

    private BuildingTests() {}

    /**
     * BLD-01.3: a civic centre placed for a scenario takes every resource and houses twenty, and the writer raises its
     * blocks in the level: its floor's corner is stone brick's neighbour cobblestone, its wall stone brick.
     */
    @StrategosTest(batch = "buildings", timeoutTicks = 200)
    public static void civicCentreAcceptsEverythingAndHouses(GameTestHelper h) {
        MatchRuntime rt = match(h);
        rt.submit(PlayerSlot.ADMIN, new SpawnBuilding(CIVIC_CENTRE, new FactionSlot(0), 4, 4, 0));
        h.startSequence()
                .thenWaitUntil(() -> check(core(rt).queries().buildings().size() == 1, "no civic centre yet"))
                .thenExecute(() -> {
                    BuildingView cc = core(rt).queries().buildings().get(0);
                    check(cc.dropsite().equals(List.of("food", "wood", "stone", "iron")), "it takes " + cc.dropsite());
                    check(cc.housing() == 20, "it houses " + cc.housing());
                })
                .thenWaitUntil(() -> {
                    BlockPos origin = rt.space().origin();
                    BuildingView cc = core(rt).queries().buildings().get(0);
                    BlockPos wall = new BlockPos(origin.getX() + 4, cc.baseY() + 1, origin.getZ() + 4);
                    BlockPos floor = new BlockPos(origin.getX() + 5, cc.baseY(), origin.getZ() + 5);
                    check(h.getLevel().getBlockState(wall).is(Blocks.STONE_BRICKS), "the wall is not up yet");
                    check(h.getLevel().getBlockState(floor).is(Blocks.COBBLESTONE), "the floor is not down yet");
                })
                .thenSucceed();
    }

    /**
     * BLD-01.4: a walker crosses past a civic centre without setting foot in its footprint, and the writer's blocks start
     * no page job: the core blocked the columns itself.
     */
    @StrategosTest(batch = "buildings", timeoutTicks = 600)
    public static void unitsPathAroundAFootprintWithNoPageJob(GameTestHelper h) {
        MatchRuntime rt = match(h);
        var state = new Object() {
            long captured;
            int walker;
        };
        h.startSequence()
                .thenExecute(() -> {
                    state.captured = rt.pages().captured();
                    rt.submit(PlayerSlot.ADMIN, new SpawnBuilding(CIVIC_CENTRE, new FactionSlot(0), 10, 10, 0));
                    spawn(rt, 6.5, 15.5, 1, 0);
                })
                .thenWaitUntil(() -> check(handles(rt).length == 1, "no walker yet"))
                .thenExecuteAfter(10, () -> {
                    state.walker = handles(rt)[0];
                    move(rt, state.walker, 25.5, 15.5);
                })
                .thenWaitUntil(() -> {
                    var u = unit(rt, state.walker);
                    int x = (int) Math.floor(u.x());
                    int z = (int) Math.floor(u.z());
                    if (x >= 10 && x <= 20 && z >= 10 && z <= 20) {
                        h.fail("the walker stands in the footprint at " + u.x() + ", " + u.z());
                    }
                    check(Math.hypot(u.x() - 25.5, u.z() - 15.5) < 1.5, "not there yet: " + u.x() + ", " + u.z());
                })
                .thenExecute(() -> check(
                        rt.pages().captured() == state.captured,
                        (rt.pages().captured() - state.captured) + " pages captured after the placement"))
                .thenSucceed();
    }

    /**
     * BLD-01.5: each seated player gets the buildings; the owner's entry is its own, with the owner's fields, the enemy's
     * is not and carries none.
     */
    @StrategosTest(batch = "buildings", timeoutTicks = 200)
    public static void buildingStateOwnerFieldsToTheOwnerOnly(GameTestHelper h) {
        MatchRuntime rt = match(h);
        MockViewer owner = seated(h, rt, "strategos-bs-own", 0);
        MockViewer enemy = seated(h, rt, "strategos-bs-foe", 1);
        rt.submit(PlayerSlot.ADMIN, new SpawnBuilding(HOUSE, new FactionSlot(0), 4, 4, 0));
        h.startSequence()
                .thenWaitUntil(() -> check(core(rt).queries().buildings().size() == 1, "no house yet"))
                .thenExecuteAfter(25, () -> {
                    List<BuildingState> toOwner = states(owner, rt);
                    List<BuildingState> toEnemy = states(enemy, rt);
                    check(
                            !toOwner.isEmpty() && !toEnemy.isEmpty(),
                            toOwner.size() + " and " + toEnemy.size() + " states");
                    BuildingState.Entry mine =
                            toOwner.get(toOwner.size() - 1).buildings().get(0);
                    BuildingState.Entry theirs =
                            toEnemy.get(toEnemy.size() - 1).buildings().get(0);
                    check(mine.own() && mine.faction() == 0, "the owner's entry " + mine);
                    check(!theirs.own() && theirs.queue().isEmpty() && !theirs.rally(), "the enemy's entry " + theirs);
                    check(
                            theirs.health() == 255 && theirs.x() == 4,
                            "the enemy sees the house as it stands: " + theirs);
                    owner.close();
                    enemy.close();
                })
                .thenSucceed();
    }

    static MockViewer seated(GameTestHelper h, MatchRuntime rt, String name, int slot) {
        Vec3 at = h.absoluteVec(new Vec3(8.5, 20, 8.5));
        MockViewer viewer = MockViewer.join(h.getLevel(), name, at.x, at.y, at.z, 4);
        StrategosServer.get().matches().join(rt, viewer.player().getUUID(), new PlayerSlot(slot));
        StrategosServer.get().net().joined(rt, viewer.player(), new PlayerSlot(slot));
        return viewer;
    }

    static List<BuildingState> states(MockViewer viewer, MatchRuntime rt) {
        viewer.poll();
        List<BuildingState> out = new ArrayList<>();
        for (ClientboundCustomPayloadPacket p : viewer.capture().of(ClientboundCustomPayloadPacket.class)) {
            if (p.payload() instanceof BuildingStatePayload b
                    && b.state().matchId() == rt.id().value()) {
                out.add(b.state());
            }
        }
        return out;
    }
}
