package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.bodiesOf;
import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;
import static dev.strategos.gametest.suites.Units.handles;
import static dev.strategos.gametest.suites.Units.unit;

import dev.strategos.core.content.PlaceholderBlueprints;
import dev.strategos.core.protocol.AllClear;
import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.Garrison;
import dev.strategos.core.protocol.Gather;
import dev.strategos.core.protocol.ModeSwitch;
import dev.strategos.core.protocol.RingBell;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnDeposit;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.build.BuildingView;
import dev.strategos.core.sim.journal.ReplayDriver;
import dev.strategos.core.sim.journal.ReplayResult;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.order.OrderQueues;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.gametest.MatchFixture;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.gametest.net.MockViewer;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.net.payload.ModeSwitchPayload;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BLD-16 on real bodies (checks 3 to 6): the bell calls every worker within 64 blocks into the civic centre within 10 s,
 * the all clear puts each back on the node it worked within 5 s, a unit inside has no body at all, and a right click on
 * the centre's bell block by a seated player in first person rings the bell. Every wait is for the work itself; a tick
 * count only fails a run that took longer than the check allows.
 */
public final class GarrisonTests {
    private static final TemplateId CENTRE = new TemplateId("strategos:building/civic_centre");
    private static final TemplateId MINE = new TemplateId("strategos:building/iron_mine");
    private static final FactionSlot F0 = new FactionSlot(0);
    private static final PlayerSlot P0 = new PlayerSlot(0);
    private static final int WORKERS = 6;
    /** An iron mine has room for three miners. */
    private static final int MINERS = 3;
    /** BLD-16.3: inside within 10 s. */
    private static final int IN_TICKS = 200;
    /** BLD-16.4: back on the node within 5 s. */
    private static final int BACK_TICKS = 100;
    /** How many must be mining before the bell rings. */
    private static final int WORKING = MINERS;

    private static final Logger LOG = LoggerFactory.getLogger(GarrisonTests.class);

    private GarrisonTests() {}

    /** The civic centre at (18, 18), an iron mine at (10, 10) and {@value #WORKERS} workers beside it, {@value #MINERS} when there is a mine. */
    private static MatchRuntime town(GameTestHelper h, boolean mine) {
        MatchRuntime rt = MatchFixture.create(h, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        rt.submit(PlayerSlot.ADMIN, new SpawnBuilding(CENTRE, F0, 18, 18, 0));
        if (mine) {
            rt.submit(PlayerSlot.ADMIN, new SpawnDeposit(MINE, 10, 10));
        }
        rt.submit(
                PlayerSlot.ADMIN,
                new SpawnUnits(
                        dev.strategos.core.content.TemplateSet.WORKER,
                        F0,
                        new Vec2d(6, 11),
                        mine ? MINERS : WORKERS,
                        1.5));
        return rt;
    }

    private static int centre(MatchRuntime rt) {
        List<BuildingView> buildings = core(rt).queries().buildings();
        check(buildings.size() == 1, "the centre is not there yet");
        check(
                buildings.get(0).state() == dev.strategos.core.sim.build.BuildingStore.COMPLETE,
                "the centre is not finished");
        return buildings.get(0).handle();
    }

    private static void ready(MatchRuntime rt, int workers) {
        check(handles(rt).length == workers, "not all workers yet");
        centre(rt);
        for (int handle : handles(rt)) {
            check(rt.bodies().get(handle) != null, "a worker has no body yet");
        }
    }

    private static boolean allIn(MatchRuntime rt) {
        for (int handle : handles(rt)) {
            if (unit(rt, handle).state() != UnitState.GARRISONED) {
                return false;
            }
        }
        return true;
    }

    private static OrderQueues.Order front(MatchRuntime rt, int handle) {
        List<OrderQueues.Order> orders = core(rt).queries().orders(handle);
        check(!orders.isEmpty(), "unit has no order");
        return orders.get(0);
    }

    /** Every worker is on the gather order of node {@code node}, or "not yet". */
    private static void onTheNode(MatchRuntime rt, int node) {
        for (int handle : handles(rt)) {
            OrderQueues.Order order = front(rt, handle);
            check(order.kind() == OrderKind.GATHER && order.target() == node, "a worker is not on the node's order");
        }
    }

    /** The workers mining right now. */
    private static Set<Integer> mining(MatchRuntime rt) {
        Set<Integer> out = new TreeSet<>();
        for (int handle : handles(rt)) {
            if (unit(rt, handle).state() == UnitState.GATHERING) {
                out.add(handle);
            }
        }
        return out;
    }

    private static final class Run {
        Set<Integer> working = Set.of();
        long rung;
        long cleared;
        int node;
    }

    /** Workers mining the iron mine, then the bell, then (when {@code clear}) the all clear. */
    private static void bellAndClear(GameTestHelper h, boolean clear) {
        MatchRuntime rt = town(h, true);
        Run run = new Run();
        int top = h.absolutePos(new BlockPos(11, 2, 11)).getY();
        var sequence = h.startSequence()
                .thenWaitUntil(() -> {
                    ready(rt, MINERS);
                    check(!core(rt).queries().nodes().isEmpty(), "no node yet");
                    var ore = h.getBlockState(new BlockPos(11, 2, 11));
                    check(
                            ore.is(net.minecraft.world.level.block.Blocks.IRON_ORE)
                                    || ore.is(net.minecraft.world.level.block.Blocks.DEEPSLATE_IRON_ORE),
                            "the mine's ore is not in the world yet");
                })
                .thenExecute(() -> {
                    run.node = core(rt).queries().nodes().get(0).id();
                    rt.submit(PlayerSlot.ADMIN, Gather.replace(UnitSet.of(handles(rt)), 11, top, 11));
                })
                .thenWaitUntil(() -> {
                    onTheNode(rt, run.node);
                    check(mining(rt).size() >= WORKING, "not enough of them mining yet");
                })
                .thenExecute(() -> {
                    run.working = mining(rt);
                    run.rung = core(rt).queries().tick();
                    rt.submit(P0, new RingBell(centre(rt)));
                })
                .thenWaitUntil(() -> check(allIn(rt), "not all inside"))
                .thenExecute(() -> {
                    long took = core(rt).queries().tick() - run.rung;
                    LOG.info("GARR bell: {} workers inside {} ticks after the ring", handles(rt).length, took);
                    check(took <= IN_TICKS, "the workers took " + took + " ticks to go in, not " + IN_TICKS);
                    for (int handle : handles(rt)) {
                        check(rt.bodies().get(handle) == null, "a worker inside still has a body");
                    }
                });
        if (!clear) {
            sequence.thenSucceed();
            return;
        }
        sequence.thenExecute(() -> {
                    run.cleared = core(rt).queries().tick();
                    rt.submit(P0, new AllClear(centre(rt)));
                })
                .thenWaitUntil(() -> {
                    onTheNode(rt, run.node);
                    check(mining(rt).containsAll(run.working), "those that mined are not all mining again");
                })
                .thenExecute(() -> {
                    long took = core(rt).queries().tick() - run.cleared;
                    LOG.info("GARR all clear: back on the node {} ticks after it", took);
                    check(took <= BACK_TICKS, "the workers took " + took + " ticks to get back, not " + BACK_TICKS);
                })
                .thenSucceed();
    }

    /** BLD-16.3: after the bell, every worker within 64 blocks is inside within 10 s. */
    @StrategosTest(batch = "eco_gather", timeoutTicks = 3_000)
    public static void the_bell_brings_them_in(GameTestHelper h) {
        bellAndClear(h, false);
    }

    /** BLD-16.4: after the all clear, each worker is back on its previous node within 5 s. */
    @StrategosTest(batch = "eco_gather", timeoutTicks = 3_000)
    public static void all_clear_returns_them(GameTestHelper h) {
        bellAndClear(h, true);
    }

    /**
     * BLD-16.5: the workers' bodies are in the level before the bell; once they are inside the level holds none of them
     * and the registry neither has nor wants one (no entity ticks for a unit inside), and at the all clear the bodies
     * come back, one for each.
     */
    @StrategosTest(timeoutTicks = 3_000)
    public static void garrisoned_units_have_no_body(GameTestHelper h) {
        MatchRuntime rt = town(h, false);
        h.startSequence()
                .thenWaitUntil(() -> {
                    ready(rt, WORKERS);
                    check(bodiesOf(h.getLevel(), rt).size() == WORKERS, "the level holds another number of bodies");
                })
                .thenExecute(() -> rt.submit(P0, new RingBell(centre(rt))))
                .thenWaitUntil(() -> check(allIn(rt), "not all inside"))
                .thenWaitUntil(() -> {
                    check(bodiesOf(h.getLevel(), rt).isEmpty(), "the level still holds bodies");
                    check(
                            rt.bodies().count() == 0 && rt.bodies().wantedCount() == 0,
                            "the registry keeps or wants bodies");
                })
                .thenExecute(() -> rt.submit(P0, new AllClear(centre(rt))))
                .thenWaitUntil(() -> {
                    List<UnitEntity> bodies = bodiesOf(h.getLevel(), rt);
                    check(bodies.size() == WORKERS, "only " + bodies.size() + " bodies came back");
                    for (int handle : handles(rt)) {
                        check(unit(rt, handle).state() != UnitState.GARRISONED, "a worker is still inside");
                        check(bodiesOf(h.getLevel(), rt, handle).size() == 1, "a worker has not exactly one body");
                    }
                })
                .thenSucceed();
    }

    /**
     * BLD-16.6: a seated commander in first person right-clicks the centre's bell block through the server's own
     * interaction path: the vanilla ring is cancelled, the bell is rung as a command and every worker starts walking in.
     */
    @StrategosTest(timeoutTicks = 3_000)
    public static void the_bell_block_rings(GameTestHelper h) {
        MatchRuntime rt = town(h, false);
        MockViewer viewer = FirstPersonTests.commander(h, rt, "strategos-bell");
        var state = new Object() {
            BlockPos bell;
            InteractionResult result;
        };
        h.startSequence()
                .thenWaitUntil(() -> {
                    ready(rt, WORKERS);
                    BuildingView centre = core(rt).queries().buildings().get(0);
                    state.bell = new BlockPos(
                            rt.space().origin().getX()
                                    + centre.footprint().anchorX()
                                    + PlaceholderBlueprints.CENTRE_BELL_X,
                            centre.baseY() + PlaceholderBlueprints.CENTRE_BELL_Y,
                            rt.space().origin().getZ()
                                    + centre.footprint().anchorZ()
                                    + PlaceholderBlueprints.CENTRE_BELL_Z);
                    check(h.getLevel().getBlockState(state.bell).getBlock() instanceof BellBlock, "no bell block yet");
                })
                .thenExecute(() -> {
                    FirstPersonTests.accept(viewer);
                    viewer.send(new ModeSwitchPayload(new ModeSwitch(
                            true, state.bell.getX() + 0.5, state.bell.getZ() - 4.5))); // outside the door
                })
                .thenWaitUntil(() -> {
                    viewer.poll();
                    check(
                            dev.strategos.mod.StrategosServer.get().commanders().observes(viewer.player()),
                            "not in first person yet");
                })
                .thenExecute(() -> {
                    ServerPlayer player = viewer.player();
                    for (int handle : handles(rt)) {
                        check(core(rt).queries().orders(handle).isEmpty(), "a worker has an order before the bell");
                    }
                    state.result = player.gameMode.useItemOn(
                            player,
                            h.getLevel(),
                            ItemStack.EMPTY,
                            InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(state.bell), Direction.NORTH, state.bell, false));
                    check(state.result == InteractionResult.SUCCESS, "the click's result was " + state.result);
                })
                .thenWaitUntil(() -> {
                    for (int handle : handles(rt)) {
                        OrderQueues.Order order = front(rt, handle);
                        check(order.kind() == OrderKind.GARRISON, "a worker is not walking in");
                    }
                })
                .thenExecute(viewer::close)
                .thenSucceed();
    }

    private static final TemplateId HORSEMAN = new TemplateId("strategos:unit/horseman");
    private static final FactionSlot F1 = new FactionSlot(1);
    /** BLD-11: TownArrowsTest sizes the raid; 6 horsemen leave the bare centre's raid alive, 10 inside break it. */
    private static final int RAIDERS = 6;

    private static final int GARRISONED = 10;

    /**
     * BLD-11.3: a civic centre with {@value #GARRISONED} workers inside (thirteen arrows a volley) breaks a raid of
     * {@value #RAIDERS} horsemen that three arrows would not, and stands. The raiders are sent only when every worker is
     * inside; the match's journal, replayed in L1 from its start, ends on the live match's hash. TownArrowsTest runs the
     * same layout in the core, the bare centre as well.
     */
    @StrategosTest(timeoutTicks = 6_000)
    public static void a_garrisoned_centre_breaks_a_horse_raid(GameTestHelper h) {
        MatchRuntime rt = MatchFixture.create(h, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        rt.submit(PlayerSlot.ADMIN, new SpawnBuilding(CENTRE, F0, 6, 10, 0));
        rt.submit(
                PlayerSlot.ADMIN,
                new SpawnUnits(dev.strategos.core.content.TemplateSet.WORKER, F0, new Vec2d(8, 22), GARRISONED, 1.5));
        var state = new Object() {
            boolean sent;
            boolean raided;
            boolean ordered;
            int centre;
        };
        h.onEachTick(() -> {
            var core = core(rt);
            if (!state.sent) {
                int[] workers = handles(rt);
                var buildings = core.queries().buildings();
                if (workers.length != GARRISONED
                        || buildings.size() != 1
                        || buildings.get(0).state() != dev.strategos.core.sim.build.BuildingStore.COMPLETE) {
                    return;
                }
                state.centre = buildings.get(0).handle();
                rt.submit(P0, new Garrison(UnitSet.of(workers), state.centre));
                state.sent = true;
                return;
            }
            if (!state.raided) {
                if (!allIn(rt)) {
                    return;
                }
                rt.submit(PlayerSlot.ADMIN, new SpawnUnits(HORSEMAN, F1, new Vec2d(28, 10), RAIDERS, 2));
                state.raided = true;
                return;
            }
            int[] raiders = core.queries().unitHandlesOf(F1);
            if (!state.ordered) {
                if (raiders.length != RAIDERS) {
                    return;
                }
                rt.submit(PlayerSlot.ADMIN, Attack.replace(UnitSet.of(raiders), state.centre));
                state.ordered = true;
                return;
            }
            check(core.queries().buildings().size() == 1, "the raid burnt the centre down");
            if (raiders.length > 0) {
                return;
            }
            check(core.queries().buildings().get(0).hitPoints() > 0, "the centre is down");
            ReplayResult replay = ReplayDriver.replay(core.journal(), dev.strategos.core.content.TemplateSet.builtin());
            check(replay.matches(), "the journal does not replay: " + replay);
            check(replay.finalHash() == core.stateHash(), "the replay ends on another hash: " + replay);
            h.succeed();
        });
    }
}
