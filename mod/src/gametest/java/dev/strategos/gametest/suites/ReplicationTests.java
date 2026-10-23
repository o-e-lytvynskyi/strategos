package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;
import static dev.strategos.gametest.suites.Units.match;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSummary;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.replicate.Visibility;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.gametest.net.MockViewer;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.net.Replication;
import dev.strategos.mod.server.net.payload.UnitSummaryPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.world.phys.Vec3;

/** Replication tier 2 to mock players (NET-08): who gets which units' summaries, and how often. */
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
                    replication.visibility((viewer, unit) -> unit.handle() != hidden);
                    a.poll();
                    b.poll();
                    a.capture().clear();
                    b.capture().clear();
                })
                .thenExecuteAfter(40, () -> {
                    collect(a, state.toA, rt);
                    collect(b, state.toB, rt);
                    replication.visibility(Visibility.ALL);
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
}
