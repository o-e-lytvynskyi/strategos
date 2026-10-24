package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.core;
import static dev.strategos.gametest.suites.Units.handles;

import dev.strategos.core.protocol.Fx;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.InputEvent;
import dev.strategos.core.sim.TickInputs;
import dev.strategos.core.sim.journal.Journal;
import dev.strategos.gametest.net.MockViewer;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.net.payload.CommandsPayload;
import dev.strategos.mod.server.net.payload.FxPayload;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.jspecify.annotations.Nullable;

/** What {@link NetTests} reads its viewers' packets and its matches' journals with. Split out in M4 T1. */
final class NetFixtures {
    private NetFixtures() {}

    /** The {@code fx} events of {@code rt} the viewer was sent since its capture was last cleared. */
    static List<Fx.Event> fxOf(MockViewer viewer, MatchRuntime rt) {
        List<Fx.Event> events = new ArrayList<>();
        for (FxPayload p : payloads(viewer, FxPayload.class)) {
            if (p.fx().matchId() == rt.id().value()) {
                events.addAll(p.fx().events());
            }
        }
        return events;
    }

    static void seat(MatchRuntime rt, MockViewer viewer, int slot) {
        StrategosServer.get().matches().join(rt, viewer.player().getUUID(), new PlayerSlot(slot));
        StrategosServer.get().net().joined(rt, viewer.player(), new PlayerSlot(slot));
    }

    static Set<Integer> bodyIds(MatchRuntime rt) {
        Set<Integer> ids = new HashSet<>();
        for (int handle : handles(rt)) {
            UnitEntity body = rt.bodies().get(handle);
            if (body != null) {
                ids.add(body.getId());
            }
        }
        return ids;
    }

    static Set<Integer> added(MockViewer viewer) {
        Set<Integer> ids = new HashSet<>();
        for (ClientboundAddEntityPacket p : viewer.capture().of(ClientboundAddEntityPacket.class)) {
            ids.add(p.getId());
        }
        return ids;
    }

    static <P extends CustomPacketPayload> List<P> payloads(MockViewer viewer, Class<P> type) {
        List<P> out = new ArrayList<>();
        for (ClientboundCustomPayloadPacket p : viewer.capture().of(ClientboundCustomPayloadPacket.class)) {
            if (type.isInstance(p.payload())) {
                out.add(type.cast(p.payload()));
            }
        }
        return out;
    }

    /** The commands the match's journal holds from player slots at or above {@code minSlot}, in order. */
    static List<CommandEnvelope> envelopes(MatchRuntime rt, int minSlot) {
        List<CommandEnvelope> out = new ArrayList<>();
        for (Journal.Segment segment : core(rt).journal().segments()) {
            for (TickInputs tick : segment.nonEmptyTicks()) {
                for (InputEvent event : tick.events()) {
                    if (event instanceof CommandEnvelope e && e.issuer().value() >= minSlot) {
                        out.add(e);
                    }
                }
            }
        }
        return out;
    }

    static boolean isBody(@Nullable Entity entity, UnitEntity body) {
        return entity != null && entity.getId() == body.getId();
    }

    static CommandsPayload read(GameTestHelper h, byte[] bytes) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(
                Unpooled.wrappedBuffer(bytes), h.getLevel().registryAccess(), ConnectionType.NEOFORGE);
        try {
            return CommandsPayload.STREAM_CODEC.decode(buf);
        } finally {
            buf.release();
        }
    }
}
