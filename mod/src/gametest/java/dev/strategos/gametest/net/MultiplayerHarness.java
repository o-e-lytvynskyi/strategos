package dev.strategos.gametest.net;

import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.sim.MatchLifecycle;
import dev.strategos.gametest.MatchFixture;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.match.MatchOptions;
import dev.strategos.mod.server.match.MatchRuntime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jspecify.annotations.Nullable;

/**
 * QA-15: up to four real acknowledging mock connections, their owned match and separate captures/counters.
 * Admission is explicit after the first completed core tick; closing disposes only this group's assets.
 * Uses QA-19's encoder and capture without a second byte-counting implementation. Server thread only.
 */
public final class MultiplayerHarness implements AutoCloseable {
    private final GameTestHelper helper;
    private final MatchRuntime match;
    private final List<MockViewer> viewers = new ArrayList<>();
    private boolean closed;
    private int ticks;

    private MultiplayerHarness(GameTestHelper helper, MatchOptions options) {
        this.helper = helper;
        this.match = MatchFixture.create(helper, options);
        helper.onEachTick(this::poll);
    }

    public static MultiplayerHarness create(GameTestHelper helper, MatchOptions options) {
        return new MultiplayerHarness(helper, options);
    }

    public MatchRuntime match() {
        return match;
    }

    /** Waiting here avoids seating before the production authority initialization boundary. */
    public boolean ready() {
        var core = match.core();
        return !closed
                && match.lifecycle() == MatchLifecycle.RUNNING
                && core != null
                && core.queries().tick() > 0;
    }

    /** Invalid plans create no players; a runtime failure during admission closes all assets already acquired. */
    public void admit(String prefix, List<PlayerSlot> planned) {
        requireOpen();
        if (!ready() || !viewers.isEmpty()) throw new IllegalStateException("group is not ready for initial admission");
        List<PlayerSlot> slots = List.copyOf(planned);
        if (!prefix.matches("[A-Za-z0-9_-]{1,8}")
                || slots.isEmpty()
                || slots.size() > 4
                || new HashSet<>(slots).size() != slots.size()) {
            throw new IllegalArgumentException("group needs a bounded unique player plan and short profile prefix");
        }
        var core = java.util.Objects.requireNonNull(match.core());
        for (PlayerSlot slot : slots) {
            if (slot.isAdmin() || core.queries().diplomacy().factionOf(slot).isEmpty()) {
                throw new IllegalArgumentException("every mock player needs a primary faction");
            }
        }
        var at = helper.absolutePos(new BlockPos(8, 20, 8));
        try {
            for (int i = 0; i < slots.size(); i++) {
                MockViewer viewer =
                        MockViewer.join(helper.getLevel(), prefix + "-" + i, at.getX() + i, at.getY(), at.getZ(), 2);
                viewers.add(viewer);
                StrategosServer.get().matches().join(match, viewer.player().getUUID(), slots.get(i));
                StrategosServer.get().net().joined(match, viewer.player(), slots.get(i));
            }
        } catch (RuntimeException failure) {
            try {
                close();
            } catch (RuntimeException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    /** Immutable membership view, also available after closing for cleanup assertions. */
    public List<MockViewer> viewers() {
        return List.copyOf(viewers);
    }

    public MockViewer viewer(int index) {
        return viewers.get(index);
    }

    /** Drain before asserting so an empty capture cannot accidentally count as a privacy success. */
    public <P extends CustomPacketPayload> List<P> payloads(int player, Class<P> type) {
        MockViewer viewer = viewer(player);
        if (!closed) viewer.poll();
        List<P> result = new ArrayList<>();
        for (ClientboundCustomPayloadPacket packet : viewer.capture().of(ClientboundCustomPayloadPacket.class)) {
            if (type.isInstance(packet.payload())) result.add(type.cast(packet.payload()));
        }
        return List.copyOf(result);
    }

    /** Once per GameTest tick, acknowledge chunks and periodically report the standing viewer's position. */
    public void poll() {
        if (closed) return;
        ticks++;
        for (MockViewer viewer : viewers) {
            viewer.poll();
            if (ticks % 20 == 0) viewer.moved();
        }
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("multiplayer group is closed");
    }

    /** Idempotent and local: never calls MockViewer.closeAll or removes another test's match/player. */
    @Override
    public void close() {
        if (closed) return;
        closed = true;
        RuntimeException failure = null;
        for (MockViewer viewer : viewers) {
            try {
                viewer.poll();
            } catch (RuntimeException problem) {
                failure = accumulate(failure, problem);
            }
        }
        try {
            var registry = StrategosServer.get().matches();
            if (registry.all().contains(match)) registry.dispose(match.id());
        } catch (RuntimeException problem) {
            failure = accumulate(failure, problem);
        }
        for (MockViewer viewer : viewers) {
            try {
                viewer.close();
            } catch (RuntimeException problem) {
                failure = accumulate(failure, problem);
            }
        }
        if (failure != null) throw failure;
    }

    private static RuntimeException accumulate(@Nullable RuntimeException failure, RuntimeException next) {
        if (failure == null) return next;
        failure.addSuppressed(next);
        return failure;
    }
}
