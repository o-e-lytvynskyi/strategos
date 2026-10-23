package dev.strategos.mod.registry;

import dev.strategos.mod.server.net.C2sHandlers;
import dev.strategos.mod.server.net.PayloadSpec;
import dev.strategos.mod.server.net.payload.CommandsPayload;
import dev.strategos.mod.server.net.payload.DebugFeedPayload;
import dev.strategos.mod.server.net.payload.DebugSubscribePayload;
import dev.strategos.mod.server.net.payload.FeedbackPayload;
import dev.strategos.mod.server.net.payload.MatchJoinPayload;
import dev.strategos.mod.server.net.payload.MatchStatePayload;
import dev.strategos.mod.server.net.payload.PayloadArrived;
import dev.strategos.mod.server.net.payload.TemplateAckPayload;
import dev.strategos.mod.server.net.payload.TemplateSetPayload;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jspecify.annotations.Nullable;

/**
 * Every payload of spec §2.7, registered in {@code RegisterPayloadHandlersEvent#registrar} with the protocol version:
 * client-to-server ones with their handlers on the server thread, timed for budget C, server-to-client ones with
 * {@link PayloadArrived}, which the client takes (B5); the dev payloads optional (PayloadRegistrar.java:158). Each has
 * its {@link PayloadSpec}.
 */
public final class ModPayloads {
    /** The payload protocol's version: a client of another version cannot join. */
    public static final String VERSION = "1";

    private ModPayloads() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterPayloadHandlersEvent.class, ModPayloads::onRegister);
    }

    private static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToServer(
                CommandsPayload.TYPE, CommandsPayload.STREAM_CODEC, C2sHandlers.timed(C2sHandlers::commands));
        registrar.playToClient(MatchJoinPayload.TYPE, MatchJoinPayload.STREAM_CODEC, PayloadArrived::post);
        registrar.playToClient(MatchStatePayload.TYPE, MatchStatePayload.STREAM_CODEC, PayloadArrived::post);
        registrar.playToClient(FeedbackPayload.TYPE, FeedbackPayload.STREAM_CODEC, PayloadArrived::post);
        registrar.playToClient(TemplateSetPayload.TYPE, TemplateSetPayload.STREAM_CODEC, PayloadArrived::post);
        registrar.playToServer(
                TemplateAckPayload.TYPE, TemplateAckPayload.STREAM_CODEC, C2sHandlers.timed(C2sHandlers::templateAck));
        // The dev payloads (PATH-10): a client or server without them still connects.
        PayloadRegistrar optional = registrar.optional();
        optional.playToServer(
                DebugSubscribePayload.TYPE,
                DebugSubscribePayload.STREAM_CODEC,
                C2sHandlers.timed(C2sHandlers::debugSubscribe));
        optional.playToClient(DebugFeedPayload.TYPE, DebugFeedPayload.STREAM_CODEC, PayloadArrived::post);
    }

    /** Every registered payload's spec. */
    public static List<PayloadSpec> specs() {
        return List.of(
                CommandsPayload.SPEC,
                MatchJoinPayload.SPEC,
                MatchStatePayload.SPEC,
                FeedbackPayload.SPEC,
                TemplateSetPayload.SPEC,
                TemplateAckPayload.SPEC,
                DebugSubscribePayload.SPEC,
                DebugFeedPayload.SPEC);
    }

    public static @Nullable PayloadSpec spec(ResourceLocation id) {
        for (PayloadSpec spec : specs()) {
            if (spec.id().equals(id)) {
                return spec;
            }
        }
        return null;
    }
}
