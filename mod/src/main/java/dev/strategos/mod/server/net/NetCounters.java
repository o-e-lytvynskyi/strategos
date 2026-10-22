package dev.strategos.mod.server.net;

import com.google.gson.JsonObject;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;

/**
 * What the gateway sent, per payload type and player (by name), with the bytes of each payload's record, and the
 * violations it counted per player and reason (ADR-0016): what perf runs report.
 */
public final class NetCounters {
    private final Map<String, long[]> sent = new TreeMap<>(); // "type player": payloads, bytes
    private final Map<String, Integer> violations = new TreeMap<>(); // "player reason"

    /** {@code who} is a player's name, or {@code trackers} for a payload vanilla's tracking delivered. */
    void sent(ResourceLocation type, String who, int bytes) {
        long[] c = sent.computeIfAbsent(type + " " + who, k -> new long[2]);
        c[0]++;
        c[1] += bytes;
    }

    void violation(String player, String reason) {
        violations.merge(player + " " + reason, 1, Integer::sum);
    }

    /** Payloads of {@code type} sent to {@code who}. */
    public long sent(ResourceLocation type, String who) {
        long[] c = sent.get(type + " " + who);
        return c == null ? 0 : c[0];
    }

    /** Bytes of every payload of {@code type} sent to anyone. */
    public long bytes(ResourceLocation type) {
        long bytes = 0;
        String prefix = type + " ";
        for (Map.Entry<String, long[]> e : sent.entrySet()) {
            if (e.getKey().startsWith(prefix)) {
                bytes += e.getValue()[1];
            }
        }
        return bytes;
    }

    /** Violations of {@code player}, of every reason. */
    public int violations(String player) {
        int n = 0;
        String prefix = player + " ";
        for (Map.Entry<String, Integer> e : violations.entrySet()) {
            if (e.getKey().startsWith(prefix)) {
                n += e.getValue();
            }
        }
        return n;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        JsonObject s = new JsonObject();
        for (Map.Entry<String, long[]> e : sent.entrySet()) {
            JsonObject c = new JsonObject();
            c.addProperty("payloads", e.getValue()[0]);
            c.addProperty("bytes", e.getValue()[1]);
            s.add(e.getKey(), c);
        }
        json.add("sent", s);
        JsonObject v = new JsonObject();
        violations.forEach(v::addProperty);
        json.add("violations", v);
        return json;
    }
}
