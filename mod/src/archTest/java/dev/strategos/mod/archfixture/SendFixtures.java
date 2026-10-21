package dev.strategos.mod.archfixture;

import net.minecraft.network.Connection;
import net.neoforged.neoforge.network.PacketDistributor;

/** B6's violating fixtures: sending around NetGateway. */
public final class SendFixtures {
    private SendFixtures() {}

    public static final class Distributor {
        static void send(Object payload) {
            PacketDistributor.sendToAllPlayers(payload);
        }
    }

    public static final class ConnectionSend {
        static void send(Connection connection, Object packet) {
            connection.send(packet);
        }
    }
}
