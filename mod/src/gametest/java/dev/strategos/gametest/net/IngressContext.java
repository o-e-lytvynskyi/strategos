package dev.strategos.gametest.net;

import java.lang.reflect.Proxy;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Narrow real transport context for synchronous registered-handler boundary tests, not a wire-framing substitute. */
public final class IngressContext {
    private IngressContext() {}

    public static IPayloadContext capture(ServerPlayer player) {
        var listener = player.connection;
        var transport = listener.getConnection();
        return (IPayloadContext) Proxy.newProxyInstance(
                IPayloadContext.class.getClassLoader(),
                new Class<?>[] {IPayloadContext.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("player")) return player;
                    if (method.getName().equals("listener")) return listener;
                    if (method.getName().equals("connection")) return transport;
                    throw new UnsupportedOperationException("unused test context method " + method.getName());
                });
    }
}
