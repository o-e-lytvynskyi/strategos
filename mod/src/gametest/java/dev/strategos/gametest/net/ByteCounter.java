package dev.strategos.gametest.net;

import com.google.gson.JsonObject;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.GameProtocols;
import net.neoforged.neoforge.network.connection.ConnectionType;

/**
 * The bytes a viewer's packets take as the game protocol encodes them (QA-19): each packet through {@code
 * GameProtocols.CLIENTBOUND_TEMPLATE} bound to the level's registries, as a real connection's encoder writes it before
 * compression and framing. Counted per type: vanilla packets by their type id, ours ({@code strategos} custom
 * payloads) as {@code payload/<id>}, so the two can be told apart.
 */
public final class ByteCounter {
    private final ProtocolInfo<ClientGamePacketListener> protocol;
    private final Map<String, long[]> counts = new TreeMap<>(); // per key: packets, bytes
    private final List<String> failures = new ArrayList<>();
    private long packets;
    private long bytes;

    public ByteCounter(RegistryAccess registries) {
        this.protocol = GameProtocols.CLIENTBOUND_TEMPLATE.bind(
                RegistryFriendlyByteBuf.decorator(registries, ConnectionType.NEOFORGE));
    }

    /**
     * Encodes one packet and counts it. A packet the protocol cannot encode, which would disconnect a real client, is
     * kept among the failures instead.
     */
    @SuppressWarnings("unchecked") // a game connection's outbound packets are all clientbound game packets
    void count(Packet<?> packet) {
        ByteBuf buf = Unpooled.buffer();
        try {
            protocol.codec().encode(buf, (Packet<? super ClientGamePacketListener>) packet);
            int size = buf.readableBytes();
            long[] c = counts.computeIfAbsent(key(packet), k -> new long[2]);
            c[0]++;
            c[1] += size;
            packets++;
            bytes += size;
        } catch (RuntimeException e) {
            if (failures.size() < 10) {
                failures.add(key(packet) + ": " + e.getMessage());
            }
        } finally {
            buf.release();
        }
    }

    /** The first packets the protocol could not encode; none is expected. */
    public List<String> failures() {
        return List.copyOf(failures);
    }

    /** {@code minecraft:move_entity_pos}, or {@code payload/strategos:pose} for one of our payloads. */
    static String key(Packet<?> packet) {
        if (packet instanceof ClientboundCustomPayloadPacket custom) {
            return "payload/" + custom.payload().type().id();
        }
        return packet.type().id().toString();
    }

    public long bytes() {
        return bytes;
    }

    public long packets() {
        return packets;
    }

    /** Bytes of our payloads, the {@code strategos} namespace. */
    public long ours() {
        long ours = 0;
        for (Map.Entry<String, long[]> e : counts.entrySet()) {
            if (e.getKey().startsWith("payload/strategos:")) {
                ours += e.getValue()[1];
            }
        }
        return ours;
    }

    public long bytes(String key) {
        long[] c = counts.get(key);
        return c == null ? 0 : c[1];
    }

    /** Totals, ours and vanilla's, and per type its packets and bytes. */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("packets", packets);
        json.addProperty("bytes", bytes);
        json.addProperty("ours", ours());
        json.addProperty("vanilla", bytes - ours());
        json.addProperty("failures", failures.size());
        JsonObject types = new JsonObject();
        for (Map.Entry<String, long[]> e : counts.entrySet()) {
            JsonObject t = new JsonObject();
            t.addProperty("packets", e.getValue()[0]);
            t.addProperty("bytes", e.getValue()[1]);
            types.add(e.getKey(), t);
        }
        json.add("types", types);
        return json;
    }

    public void clear() {
        counts.clear();
        packets = 0;
        bytes = 0;
    }
}
