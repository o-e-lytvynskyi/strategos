package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;
import static dev.strategos.gametest.suites.Units.handles;
import static dev.strategos.gametest.suites.Units.match;
import static dev.strategos.gametest.suites.Units.unit;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.DepositState;
import dev.strategos.core.protocol.FactionState;
import dev.strategos.core.protocol.Gather;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UiQuery;
import dev.strategos.core.protocol.UnitDetail;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.protocol.UnitSummary;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.ExternalDamage;
import dev.strategos.core.sim.replicate.ReplicationService;
import dev.strategos.core.sim.replicate.Visibility;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.gametest.MatchFixture;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.gametest.net.MockViewer;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.net.Replication;
import dev.strategos.mod.server.net.payload.DepositStatePayload;
import dev.strategos.mod.server.net.payload.FactionStatePayload;
import dev.strategos.mod.server.net.payload.UiQueryPayload;
import dev.strategos.mod.server.net.payload.UnitDetailPayload;
import dev.strategos.mod.server.net.payload.UnitHudPayload;
import dev.strategos.mod.server.net.payload.UnitSummaryPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.world.phys.Vec3;

/**
 * Replication tier 2 to mock players (NET-08): who gets which units' summaries, and how often; and each faction's
 * {@code faction_state} to its own players only (ECO-01).
 */
public final class ReplicationTests {
    private ReplicationTests() {}

    /**
     * NET-08.3: over 2 s, a seated player of faction 0 gets four frames, holding its three units and the two enemies it
     * sees, never the third, which the test's visibility hides; the player of faction 1, the positive control in the
     * same setup, gets its own three and all three of faction 0.
     */
    @StrategosTest(timeoutTicks = 200)
    public static void summariesAtTwoHertzOwnAndVisibleOnly(GameTestHelper h) {
        MatchRuntime rt = match(h);
        MockViewer a = seated(h, rt, "strategos-rep-a", 0);
        MockViewer b = seated(h, rt, "strategos-rep-b", 1);
        rt.submit(
                PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(0), new Vec2d(8, 8), 3, 2));
        rt.submit(
                PlayerSlot.ADMIN,
                new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(1), new Vec2d(24, 24), 3, 2));
        Replication replication = StrategosServer.get().replication();
        var state = new Object() {
            int hidden = Handle.NONE;
            final List<UnitSummary> toA = new ArrayList<>();
            final List<UnitSummary> toB = new ArrayList<>();
        };
        h.startSequence()
                .thenWaitUntil(() -> check(core(rt).queries().unitHandles().length == 6, "the units are not there"))
                .thenExecute(() -> {
                    state.hidden = core(rt).queries().unitHandlesOf(new FactionSlot(1))[0];
                    int hidden = state.hidden;
                    replication.visibility(rt.id(), (viewer, unit) -> unit.handle() != hidden);
                    a.poll();
                    b.poll();
                    a.capture().clear();
                    b.capture().clear();
                })
                .thenExecuteAfter(40, () -> {
                    collect(a, state.toA, rt);
                    collect(b, state.toB, rt);
                    replication.visibility(rt.id(), Visibility.ALL);
                    check(state.toA.size() == 4, "faction 0 got " + state.toA.size() + " frames in 2 s");
                    check(state.toB.size() == 4, "faction 1 got " + state.toB.size() + " frames in 2 s");
                    for (UnitSummary s : state.toA) {
                        check(
                                s.units().stream().noneMatch(u -> u.index() == Handle.index(state.hidden)),
                                "faction 0's frame holds the hidden unit");
                        check(
                                s.units().size() == 5,
                                "faction 0's frame holds " + s.units().size() + " units, not 5");
                    }
                    for (UnitSummary s : state.toB) {
                        check(
                                s.units().size() == 6,
                                "faction 1's frame holds " + s.units().size() + " units, not 6");
                    }
                    a.close();
                    b.close();
                })
                .thenSucceed();
    }

    /** NET-08.4: a {@code ui_query} for an own unit and an enemy is answered with the own unit's details only. */
    @StrategosTest(timeoutTicks = 200)
    public static void detailAnswersOwnUnitsOnly(GameTestHelper h) {
        MatchRuntime rt = match(h);
        MockViewer a = seated(h, rt, "strategos-rep-d", 0);
        rt.submit(
                PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(0), new Vec2d(8, 8), 1, 0));
        rt.submit(
                PlayerSlot.ADMIN,
                new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(1), new Vec2d(24, 24), 1, 0));
        var state = new Object() {
            int own;
            int enemy;
        };
        h.startSequence()
                .thenWaitUntil(() -> check(core(rt).queries().unitHandles().length == 2, "the units are not there"))
                .thenExecute(() -> {
                    state.own = core(rt).queries().unitHandlesOf(new FactionSlot(0))[0];
                    state.enemy = core(rt).queries().unitHandlesOf(new FactionSlot(1))[0];
                    a.poll();
                    a.capture().clear();
                    a.send(new UiQueryPayload(new UiQuery(List.of(state.enemy, state.own))));
                })
                .thenExecuteAfter(4, () -> {
                    a.poll();
                    List<UnitDetail> details = new ArrayList<>();
                    for (ClientboundCustomPayloadPacket p : a.capture().of(ClientboundCustomPayloadPacket.class)) {
                        if (p.payload() instanceof UnitDetailPayload d) {
                            details.add(d.detail());
                        }
                    }
                    check(!details.isEmpty(), "no detail came");
                    for (UnitDetail d : details) {
                        check(
                                d.units().size() == 1 && d.units().get(0).handle() == state.own,
                                "the detail holds " + d.units());
                    }
                    a.close();
                })
                .thenSucceed();
    }

    /**
     * CMB-06.1 (NET-08's {@code unit_hud}): two hurt units, one of each faction; faction 0's player, which the test's
     * visibility keeps from seeing faction 1's, gets only its own unit's hit points; faction 1's player, the positive
     * control, gets its own unit's and, seeing everything, faction 0's.
     */
    @StrategosTest(timeoutTicks = 200)
    public static void hudReachesOnlyThoseWhoSee(GameTestHelper h) {
        MatchRuntime rt = match(h);
        MockViewer a = seated(h, rt, "strategos-rep-h", 0);
        MockViewer b = seated(h, rt, "strategos-rep-i", 1);
        rt.submit(
                PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(0), new Vec2d(8, 8), 1, 0));
        rt.submit(
                PlayerSlot.ADMIN,
                new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(1), new Vec2d(24, 24), 1, 0));
        Replication replication = StrategosServer.get().replication();
        var state = new Object() {
            int own;
            int enemy;
        };
        h.startSequence()
                .thenWaitUntil(() -> check(core(rt).queries().unitHandles().length == 2, "the units are not there"))
                .thenExecute(() -> {
                    state.own = core(rt).queries().unitHandlesOf(new FactionSlot(0))[0];
                    state.enemy = core(rt).queries().unitHandlesOf(new FactionSlot(1))[0];
                    int hidden = state.enemy;
                    replication.visibility(rt.id(), (viewer, unit) -> viewer.value() != 0 || unit.handle() != hidden);
                    rt.damage(new ExternalDamage(
                            state.own, ExternalDamage.Kind.HACK, 30, ExternalDamage.SourceKind.NONE));
                    rt.damage(new ExternalDamage(
                            state.enemy, ExternalDamage.Kind.HACK, 60, ExternalDamage.SourceKind.NONE));
                    a.poll();
                    b.poll();
                    a.capture().clear();
                    b.capture().clear();
                })
                .thenExecuteAfter(8, () -> {
                    List<Integer> toA = hudIndices(a);
                    List<Integer> toB = hudIndices(b);
                    replication.visibility(rt.id(), Visibility.ALL);
                    check(toA.contains(Handle.index(state.own)), "faction 0 lacks its own unit's hit points: " + toA);
                    check(!toA.contains(Handle.index(state.enemy)), "faction 0 got the hidden enemy's: " + toA);
                    check(
                            toB.contains(Handle.index(state.enemy)) && toB.contains(Handle.index(state.own)),
                            "faction 1, seeing both, got " + toB);
                    a.close();
                    b.close();
                })
                .thenSucceed();
    }

    /**
     * ECO-01.3: after a grant to faction 0, its seated player gets a {@code faction_state} with the wood, the player of
     * faction 1 gets its own, empty, and never faction 0's; each gets one state, not one every look, while nothing
     * changes.
     */
    @StrategosTest(timeoutTicks = 200)
    public static void factionStateReachesItsOwnFactionOnly(GameTestHelper h) {
        MatchRuntime rt = match(h);
        MockViewer a = seated(h, rt, "strategos-fs-a", 0);
        MockViewer b = seated(h, rt, "strategos-fs-b", 1);
        rt.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(0), "wood", 120));
        h.startSequence()
                .thenWaitUntil(() -> check(
                        core(rt).queries().treasury(new FactionSlot(0)).stock().get(1) == 120_000L, "no grant yet"))
                .thenExecuteAfter(30, () -> {
                    List<FactionState> toA = factionStates(a, rt);
                    List<FactionState> toB = factionStates(b, rt);
                    check(!toA.isEmpty() && !toB.isEmpty(), "states: " + toA.size() + " to a, " + toB.size() + " to b");
                    check(toA.stream().allMatch(st -> st.faction() == 0), "faction 0's player got " + toA);
                    check(toB.stream().allMatch(st -> st.faction() == 1), "faction 1's player got " + toB);
                    FactionState lastA = toA.get(toA.size() - 1);
                    check(lastA.stock().get(1) == 120_000L, "faction 0's wood " + lastA.stock());
                    check(toB.get(toB.size() - 1).stock().stream().allMatch(v -> v == 0L), "faction 1's " + toB);
                    check(toB.size() == 1, "faction 1's unchanged state came " + toB.size() + " times");
                    a.close();
                    b.close();
                })
                .thenSucceed();
    }

    private static List<FactionState> factionStates(MockViewer viewer, MatchRuntime rt) {
        viewer.poll();
        List<FactionState> out = new ArrayList<>();
        for (ClientboundCustomPayloadPacket p : viewer.capture().of(ClientboundCustomPayloadPacket.class)) {
            if (p.payload() instanceof FactionStatePayload f
                    && f.state().matchId() == rt.id().value()) {
                out.add(f.state());
            }
        }
        return out;
    }

    private static List<Integer> hudIndices(MockViewer viewer) {
        viewer.poll();
        List<Integer> indices = new ArrayList<>();
        for (ClientboundCustomPayloadPacket p : viewer.capture().of(ClientboundCustomPayloadPacket.class)) {
            if (p.payload() instanceof UnitHudPayload hud) {
                hud.hud().units().forEach(e -> indices.add(e.index()));
            }
        }
        return indices;
    }

    private static MockViewer seated(GameTestHelper h, MatchRuntime rt, String name, int slot) {
        Vec3 at = h.absoluteVec(new Vec3(8.5, 20, 8.5));
        MockViewer viewer = MockViewer.join(h.getLevel(), name, at.x, at.y, at.z, 4);
        StrategosServer.get().matches().join(rt, viewer.player().getUUID(), new PlayerSlot(slot));
        StrategosServer.get().net().joined(rt, viewer.player(), new PlayerSlot(slot));
        return viewer;
    }

    private static void collect(MockViewer viewer, List<UnitSummary> into, MatchRuntime rt) {
        viewer.poll();
        for (ClientboundCustomPayloadPacket p : viewer.capture().of(ClientboundCustomPayloadPacket.class)) {
            if (p.payload() instanceof UnitSummaryPayload s
                    && s.summary().matchId() == rt.id().value()) {
                into.add(s.summary());
            }
        }
    }

    /**
     * UI-06.3: the queue a seated mock player receives is the server's: at the tick of each {@code building_state}, the
     * centre's queue and its head's progress equal what the core held then.
     */
    @StrategosTest(timeoutTicks = 600)
    public static void theQueueReachesItsOwnerAsTheServerHasIt(GameTestHelper h) {
        MatchRuntime rt = match(h);
        ProdTests.town(rt);
        MockViewer owner = BuildingTests.seated(h, rt, "strategos-queue", 0);
        java.util.Map<Long, String> server = new java.util.HashMap<>();
        rt.addListener(out -> {
            var q = core(rt).queries();
            for (var b : q.buildings()) {
                server.put(q.tick(), b.queue() + " " + b.trained());
            }
        });
        h.startSequence()
                .thenWaitUntil(() -> check(!core(rt).queries().buildings().isEmpty(), "no centre yet"))
                .thenExecute(() -> {
                    int centre = ProdTests.centre(rt).handle();
                    for (int k = 0; k < 3; k++) {
                        rt.submit(ProdTests.P0, new dev.strategos.core.protocol.Train(centre, TemplateSet.WORKER));
                    }
                })
                .thenExecuteAfter(160, () -> {
                    List<dev.strategos.core.protocol.BuildingState> got = BuildingTests.states(owner, rt);
                    int matched = 0;
                    boolean midway = false;
                    for (var state : got) {
                        var e = state.buildings().get(0);
                        String expected = server.get(state.tick());
                        if (expected == null || e.queue().isEmpty()) {
                            continue;
                        }
                        check(
                                (e.queue() + " " + e.trained()).equals(expected),
                                "at tick " + state.tick() + " the owner got " + e.queue() + " " + e.trained()
                                        + ", the server held " + expected);
                        matched++;
                        midway |= e.queue().size() >= 2 && e.trained() > 0 && e.trained() < 1000;
                    }
                    check(matched >= 3, "only " + matched + " states with a queue matched, of " + got.size());
                    check(midway, "no state caught a head in training with more behind it");
                    owner.close();
                })
                .thenSucceed();
    }

    /**
     * UI-07.3: each {@code faction_state} a seated mock player receives is the server's at its tick, the workers per
     * resource and the idle ones included: one worker on an oak, one on a bush, one idle.
     */
    @StrategosTest(batch = "eco_gather", timeoutTicks = 1_200)
    public static void factionStateEqualsTheServer(GameTestHelper h) {
        for (int y = 2; y <= 4; y++) {
            h.setBlock(new BlockPos(6, y, 6), net.minecraft.world.level.block.Blocks.OAK_LOG);
        }
        BlockPos bush = new BlockPos(6, 2, 26);
        h.setBlock(bush.below(), net.minecraft.world.level.block.Blocks.DIRT);
        h.setBlock(bush, net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH);
        MatchRuntime rt = MatchFixture.create(h, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        rt.submit(PlayerSlot.ADMIN, new SpawnBuilding(ProdTests.CIVIC_CENTRE, new FactionSlot(0), 14, 10, 0));
        EcoTests.workers(rt, 10, 16, 3);
        java.util.Map<Long, dev.strategos.core.protocol.FactionState> server = new java.util.HashMap<>();
        rt.addListener(out -> {
            var q = core(rt).queries();
            server.put(
                    q.tick(),
                    dev.strategos.core.sim.replicate.ReplicationService.factionState(
                            q, rt.id().value(), new FactionSlot(0)));
        });
        // the seat's first state goes out at once, before any tick: recorded as the core stands now
        server.put(
                core(rt).queries().tick(),
                dev.strategos.core.sim.replicate.ReplicationService.factionState(
                        core(rt).queries(), rt.id().value(), new FactionSlot(0)));
        MockViewer owner = BuildingTests.seated(h, rt, "strategos-fstate", 0);
        int y = h.absolutePos(bush).getY();
        h.startSequence()
                .thenWaitUntil(
                        () -> check(handles(rt).length == 3 && EcoTests.nodeAt(rt, 6.5, 6.5) != null, "not ready yet"))
                .thenExecute(() -> {
                    int[] units = handles(rt);
                    rt.submit(PlayerSlot.ADMIN, Gather.replace(UnitSet.of(units[0]), 6, y + 1, 6));
                    rt.submit(PlayerSlot.ADMIN, Gather.replace(UnitSet.of(units[1]), 6, y, 26));
                })
                .thenExecuteAfter(700, () -> {
                    owner.poll();
                    List<dev.strategos.core.protocol.FactionState> got = new ArrayList<>();
                    for (var p : owner.capture()
                            .of(net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket.class)) {
                        if (p.payload() instanceof dev.strategos.mod.server.net.payload.FactionStatePayload f
                                && f.state().matchId() == rt.id().value()) {
                            got.add(f.state());
                        }
                    }
                    int split = 0;
                    for (var state : got) {
                        var expected = server.get(state.tick());
                        check(expected != null, "no server state at tick " + state.tick());
                        check(
                                state.sameAs(expected) && state.tick() == expected.tick(),
                                "at tick " + state.tick() + " the owner got " + state + ", the server held "
                                        + expected);
                        if (state.workers().get(0) == 1 && state.workers().get(1) == 1 && state.idleWorkers() == 1) {
                            split++;
                        }
                    }
                    check(
                            got.size() >= 3,
                            "only " + got.size() + " faction states"); // sent when they change: on deliveries
                    check(
                            split > 0,
                            "no state counted one on wood, one on food and one idle: " + got.get(got.size() - 1));
                    check(got.get(got.size() - 1).gathered().get(1) > 0, "no wood came home");
                    owner.close();
                })
                .thenSucceed();
    }

    /**
     * ECO-08 on the wire: two seated mock players, one per faction, both get the iron mine's {@code deposit_state},
     * each one the server's at its tick, what it has left falling as three workers mine it.
     */
    @StrategosTest(batch = "eco_gather", timeoutTicks = 1_200)
    public static void depositsReachEveryPlayerAsTheyFall(GameTestHelper h) {
        MatchRuntime rt = MatchFixture.create(h, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        rt.submit(PlayerSlot.ADMIN, new SpawnBuilding(ProdTests.CIVIC_CENTRE, new FactionSlot(0), 18, 18, 0));
        rt.submit(
                PlayerSlot.ADMIN,
                new dev.strategos.core.protocol.SpawnDeposit(
                        new dev.strategos.core.shared.TemplateId("strategos:building/iron_mine"), 10, 10));
        EcoTests.workers(rt, 6, 11, 3);
        java.util.Map<Long, DepositState> server = new java.util.HashMap<>();
        rt.addListener(out -> server.put(
                core(rt).queries().tick(),
                ReplicationService.depositState(core(rt).queries(), rt.id().value())));
        MockViewer own = BuildingTests.seated(h, rt, "strategos-dep0", 0);
        MockViewer other = BuildingTests.seated(h, rt, "strategos-dep1", 1);
        int top = h.absolutePos(new BlockPos(11, 2, 11)).getY();
        h.startSequence()
                .thenWaitUntil(() ->
                        check(handles(rt).length == 3 && EcoTests.nodeAt(rt, 11.5, 11.5) != null, "not ready yet"))
                .thenExecute(() -> rt.submit(PlayerSlot.ADMIN, Gather.replace(UnitSet.of(handles(rt)), 11, top, 11)))
                .thenExecuteAfter(800, () -> {
                    for (MockViewer v : List.of(own, other)) {
                        List<DepositState> got = deposits(v, rt);
                        check(got.size() >= 3, "only " + got.size() + " deposit states");
                        int last = Integer.MAX_VALUE;
                        for (DepositState state : got) {
                            DepositState expected = server.get(state.tick());
                            check(
                                    expected == null || state.sameAs(expected),
                                    "at tick " + state.tick() + " got " + state + ", the server held " + expected);
                            int left = state.deposits().get(0).left();
                            check(left <= last, "what is left grew: " + last + " to " + left);
                            last = left;
                        }
                        check(last < 1_000, "nothing mined: " + last);
                        v.close();
                    }
                })
                .thenSucceed();
    }

    private static List<DepositState> deposits(MockViewer viewer, MatchRuntime rt) {
        viewer.poll();
        List<DepositState> out = new ArrayList<>();
        for (ClientboundCustomPayloadPacket p : viewer.capture().of(ClientboundCustomPayloadPacket.class)) {
            if (p.payload() instanceof DepositStatePayload d
                    && d.state().matchId() == rt.id().value()) {
                out.add(d.state());
            }
        }
        return out;
    }
}
