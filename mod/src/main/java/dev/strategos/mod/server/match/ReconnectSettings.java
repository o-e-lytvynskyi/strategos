package dev.strategos.mod.server.match;

import java.util.Objects;

/** Validated policy captured when the server starts; owner configuration uses seconds. */
public record ReconnectSettings(int timeoutSeconds, Policy policy) {
    public static final int DEFAULT_SECONDS = 300;
    public static final int MAX_SECONDS = 86_400;
    public static final ReconnectSettings DEFAULTS = new ReconnectSettings(DEFAULT_SECONDS, Policy.DEFEAT);

    public enum Policy {
        DEFEAT,
        AI_TAKEOVER
    }

    public ReconnectSettings {
        if (timeoutSeconds < 1 || timeoutSeconds > MAX_SECONDS) {
            throw new IllegalArgumentException("reconnect timeout must be between 1 and 86400 seconds");
        }
        Objects.requireNonNull(policy, "policy");
    }

    public long timeoutNanos() {
        return timeoutSeconds * 1_000_000_000L;
    }
}
