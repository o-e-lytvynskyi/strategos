package dev.strategos.gametest.net;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.network.protocol.Packet;

/** The packets one viewer received, in order, to assert on per type; bundles arrive unbundled. */
public final class PacketCapture {
    private final List<Packet<?>> packets = new ArrayList<>();
    private boolean keeping = true;

    void add(Packet<?> packet) {
        if (keeping) {
            packets.add(packet);
        }
    }

    /** Off for long runs that only count bytes; on by default. */
    public void keep(boolean keeping) {
        this.keeping = keeping;
    }

    public List<Packet<?>> all() {
        return List.copyOf(packets);
    }

    /** The received packets of one class, in order. */
    public <P extends Packet<?>> List<P> of(Class<P> type) {
        List<P> out = new ArrayList<>();
        for (Packet<?> p : packets) {
            if (type.isInstance(p)) {
                out.add(type.cast(p));
            }
        }
        return out;
    }

    /** How many packets of each type id arrived, such as {@code minecraft:add_entity}. */
    public Map<String, Integer> counts() {
        Map<String, Integer> counts = new TreeMap<>();
        for (Packet<?> p : packets) {
            counts.merge(ByteCounter.key(p), 1, Integer::sum);
        }
        return counts;
    }

    public void clear() {
        packets.clear();
    }
}
