package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.handles;
import static dev.strategos.gametest.suites.Units.match;
import static dev.strategos.gametest.suites.Units.move;
import static dev.strategos.gametest.suites.Units.spawn;

import dev.strategos.core.protocol.DebugFeedMsg;
import dev.strategos.core.protocol.DebugLayer;
import dev.strategos.core.protocol.DebugSubscribe;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.gametest.net.MockViewer;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.net.NetCounters;
import dev.strategos.mod.server.net.payload.DebugFeedPayload;
import dev.strategos.mod.server.net.payload.DebugSubscribePayload;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.world.phys.Vec3;

/** The movement overlay's dev payloads on a real server (PATH-10, spec §9.2 T25). */
public final class DebugTests {
    private static final DebugSubscribe EVERYTHING = new DebugSubscribe(DebugLayer.ALL, DebugSubscribe.DEFAULT_RADIUS);

    private DebugTests() {}

    /**
     * PATH-10.1: beside a running match with a unit walking, an operator that never subscribed gets no dev payload, and
     * a player without the debug permission that asks is refused, counted as a violation, and gets none either.
     */
    @StrategosTest(timeoutTicks = 200)
    public static void unsubscribedGetsNoDevPayload(GameTestHelper h) {
        MatchRuntime rt = match(h);
        spawn(rt, 4.5, 16.5, 1, 0);
        Vec3 at = h.absoluteVec(new Vec3(16.5, 2, 16.5));
        MockViewer operator = MockViewer.join(h.getLevel(), "strategos-op", at.x, at.y, at.z, 2)
                .operator(2);
        String name = "strategos-plain";
        MockViewer plain = MockViewer.join(h.getLevel(), name, at.x, at.y, at.z, 2);
        NetCounters counters = StrategosServer.get().net().counters();
        var state = new Object() {
            int before = -1;
            int ticks;
        };
        h.onEachTick(() -> {
            operator.poll();
            plain.poll();
            int[] units = handles(rt);
            if (units.length != 1) {
                return;
            }
            if (state.before < 0) {
                move(rt, units[0], 28.5, 16.5);
                state.before = counters.violations(name);
                plain.send(new DebugSubscribePayload(EVERYTHING));
                return;
            }
            if (++state.ticks < 40) {
                return;
            }
            check(feeds(operator).isEmpty(), "an operator that never subscribed got a dev payload");
            check(feeds(plain).isEmpty(), "a refused player got a dev payload");
            check(counters.violations(name) == state.before + 1, "the refusal was not counted");
            operator.close();
            plain.close();
            h.succeed();
        });
    }

    /**
     * PATH-10.1: an operator that subscribes to every layer beside a running match on terrain, a unit walking, gets each
     * of the five layers; once it subscribes to none, nothing newer than that tick comes.
     */
    @StrategosTest(timeoutTicks = 300)
    public static void subscribedGetsFeed(GameTestHelper h) {
        MatchRuntime rt = match(h);
        spawn(rt, 4.5, 16.5, 1, 0);
        Vec3 at = h.absoluteVec(new Vec3(16.5, 2, 16.5));
        MockViewer operator = MockViewer.join(h.getLevel(), "strategos-sub", at.x, at.y, at.z, 2)
                .operator(2);
        var state = new Object() {
            boolean subscribed;
            long unsubscribedAt = -1;
        };
        h.onEachTick(() -> {
            operator.poll();
            int[] units = handles(rt);
            if (units.length != 1) {
                return;
            }
            long now = h.getLevel().getServer().getTickCount();
            if (!state.subscribed) {
                state.subscribed = true;
                move(rt, units[0], 28.5, 16.5);
                operator.send(new DebugSubscribePayload(EVERYTHING));
                return;
            }
            List<DebugFeedMsg> feeds = feeds(operator);
            if (state.unsubscribedAt < 0) {
                Set<DebugLayer> seen = EnumSet.noneOf(DebugLayer.class);
                for (DebugFeedMsg feed : feeds) {
                    seen.add(feed.layer());
                }
                if (seen.size() == DebugLayer.values().length) {
                    operator.send(new DebugSubscribePayload(new DebugSubscribe(0, DebugSubscribe.DEFAULT_RADIUS)));
                    state.unsubscribedAt = now;
                }
                return;
            }
            if (now < state.unsubscribedAt + 20) {
                return; // every layer would have come again in 20 ticks: the slowest is sent twice a second
            }
            for (DebugFeedMsg feed : feeds) {
                check(feed.tick() <= state.unsubscribedAt, "a feed of tick " + feed.tick() + " after unsubscribing");
            }
            operator.close();
            h.succeed();
        });
    }

    private static List<DebugFeedMsg> feeds(MockViewer viewer) {
        List<DebugFeedMsg> out = new ArrayList<>();
        for (ClientboundCustomPayloadPacket p : viewer.capture().of(ClientboundCustomPayloadPacket.class)) {
            if (p.payload() instanceof DebugFeedPayload feed) {
                out.add(feed.message());
            }
        }
        return out;
    }
}
