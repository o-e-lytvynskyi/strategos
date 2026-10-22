package dev.strategos.core.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * JSON in one canonical text, so equal content has equal bytes and an equal SHA-256 (ADR-0012): keys sorted at every
 * level, no whitespace, numbers in their shortest exact decimal form ({@code 1.0} and {@code 1} are both {@code 1}).
 */
public final class CanonicalJson {
    private CanonicalJson() {}

    public static String write(JsonElement json) {
        StringBuilder sb = new StringBuilder();
        append(sb, json);
        return sb.toString();
    }

    public static byte[] sha256(String canonical) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every Java platform has SHA-256", e);
        }
    }

    private static void append(StringBuilder sb, JsonElement json) {
        if (json.isJsonObject()) {
            JsonObject o = json.getAsJsonObject();
            List<String> keys = new ArrayList<>(o.keySet());
            keys.sort(null);
            sb.append('{');
            for (int i = 0; i < keys.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                appendString(sb, keys.get(i));
                sb.append(':');
                append(sb, o.get(keys.get(i)));
            }
            sb.append('}');
        } else if (json.isJsonArray()) {
            JsonArray a = json.getAsJsonArray();
            sb.append('[');
            for (int i = 0; i < a.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                append(sb, a.get(i));
            }
            sb.append(']');
        } else if (json.isJsonNull()) {
            sb.append("null");
        } else {
            JsonPrimitive p = json.getAsJsonPrimitive();
            if (p.isNumber()) {
                BigDecimal d = new BigDecimal(p.getAsString()).stripTrailingZeros();
                sb.append(d.signum() == 0 ? "0" : d.toPlainString());
            } else if (p.isBoolean()) {
                sb.append(p.getAsBoolean());
            } else {
                appendString(sb, p.getAsString());
            }
        }
    }

    private static void appendString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}
