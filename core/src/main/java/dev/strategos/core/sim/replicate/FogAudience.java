package dev.strategos.core.sim.replicate;

import dev.strategos.core.shared.CoreApi;
import org.jspecify.annotations.Nullable;

/**
 * Cold decisions for entity information, not a network sender or proof that existing senders apply them.
 * AFTER-M5: audit and adapt actual projectile FX endpoints.
 * AFTER-M6: derive sight from the authoritative view, not client flags.
 * AFTER-M7: classify recipients and wire every vanilla/custom channel with mock-player controls.
 */
@CoreApi
public final class FogAudience {
    public enum Channel {
        BODY,
        SOUND,
        PARTICLE,
        PROJECTILE,
        HEALTH,
        DETAIL,
        SUMMARY,
        PING,
        BLOCK_PROGRESS,
        SCOREBOARD_TEAM,
        COMMANDER,
        FIRST_PERSON,
        MIRAGE
    }

    /** Trusted evidence for one match, viewer and event; sharing means an explicitly authorized direct source. */
    public record Evidence(
            boolean member,
            boolean observer,
            boolean own,
            boolean sharing,
            boolean sourceVisible,
            boolean destinationVisible,
            boolean remembered) {}

    private FogAudience() {}

    public static boolean allows(@Nullable String channelId, Evidence evidence) {
        if (!evidence.member()) {
            return false;
        }
        for (Channel channel : Channel.values()) {
            if (channel.name().equals(channelId)) {
                return allows(channel, evidence);
            }
        }
        return false;
    }

    private static boolean allows(Channel channel, Evidence e) {
        if (channel == Channel.SCOREBOARD_TEAM) {
            return false;
        }
        if (e.observer() || e.own()) {
            return true;
        }
        return switch (channel) {
            case SCOREBOARD_TEAM -> false;
            case COMMANDER -> e.sharing();
            case BODY, FIRST_PERSON -> e.sharing() || e.sourceVisible();
            case PROJECTILE -> e.sourceVisible() || e.destinationVisible();
            case MIRAGE -> e.sourceVisible() || e.remembered();
            case SOUND, PARTICLE, HEALTH, DETAIL, SUMMARY, PING, BLOCK_PROGRESS -> e.sourceVisible();
        };
    }
}
