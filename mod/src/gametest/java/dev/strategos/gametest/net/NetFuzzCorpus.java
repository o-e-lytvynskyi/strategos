package dev.strategos.gametest.net;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Committed generated-input evidence, never commands or instructions. */
public final class NetFuzzCorpus {
    private NetFuzzCorpus() {}

    public record Entry(String id, String payload, String hex, boolean refused) {
        public byte[] bytes() {
            return HexFormat.of().parseHex(hex);
        }

        @Override
        public String toString() {
            return id;
        }
    }

    public static List<Entry> read(String resource) {
        try (var stream = NetFuzzCorpus.class.getResourceAsStream("/" + resource)) {
            if (stream == null) throw new IllegalArgumentException("missing corpus " + resource);
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            if (json.get("version").getAsInt() != 1) throw new IllegalArgumentException("unsupported corpus version");
            var entries = new ArrayList<Entry>();
            for (var item : json.getAsJsonArray("cases")) {
                var c = item.getAsJsonObject();
                entries.add(new Entry(
                        c.get("id").getAsString(),
                        c.get("payload").getAsString(),
                        c.get("hex").getAsString(),
                        c.get("refused").getAsBoolean()));
            }
            return List.copyOf(entries);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
