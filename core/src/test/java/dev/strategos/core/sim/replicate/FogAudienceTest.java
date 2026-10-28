package dev.strategos.core.sim.replicate;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FogAudienceTest {
    private static FogAudience.Evidence evidence(
            boolean member, boolean observer, boolean own, boolean source, boolean destination, boolean remembered) {
        return new FogAudience.Evidence(member, observer, own, false, source, destination, remembered);
    }

    @Test
    void hiddenEntityChannelsDenyAndVisibleControlsPass() {
        for (FogAudience.Channel channel : FogAudience.Channel.values()) {
            if (channel == FogAudience.Channel.COMMANDER || channel == FogAudience.Channel.SCOREBOARD_TEAM) {
                continue;
            }
            assertThat(FogAudience.allows(channel.name(), evidence(true, false, false, false, false, false)))
                    .as("hidden %s", channel)
                    .isFalse();
            assertThat(FogAudience.allows(channel.name(), evidence(true, false, false, true, false, false)))
                    .as("visible %s", channel)
                    .isTrue();
        }
    }

    @Test
    void outsideMatchDeniesEveryEvidenceCombinationAndUnknownIdsAlwaysDeny() {
        for (FogAudience.Channel channel : FogAudience.Channel.values()) {
            for (int flags = 0; flags < 64; flags++) {
                assertThat(FogAudience.allows(
                                channel.name(),
                                new FogAudience.Evidence(
                                        false,
                                        (flags & 1) != 0,
                                        (flags & 2) != 0,
                                        (flags & 4) != 0,
                                        (flags & 8) != 0,
                                        (flags & 16) != 0,
                                        (flags & 32) != 0)))
                        .as("%s outside match, flags %s", channel, flags)
                        .isFalse();
            }
        }
        assertThat(FogAudience.allows("undeclared_payload", evidence(true, true, true, true, true, true)))
                .isFalse();
        assertThat(FogAudience.allows(null, evidence(true, true, true, true, true, true)))
                .isFalse();
    }

    @Test
    void destinationSightOnlyAuthorizesProjectileAndMemoryOnlyAuthorizesMirage() {
        for (FogAudience.Channel channel : FogAudience.Channel.values()) {
            assertThat(FogAudience.allows(channel.name(), evidence(true, false, false, false, true, false)))
                    .as("destination-only %s", channel)
                    .isEqualTo(channel == FogAudience.Channel.PROJECTILE);
            assertThat(FogAudience.allows(channel.name(), evidence(true, false, false, false, false, true)))
                    .as("memory-only %s", channel)
                    .isEqualTo(channel == FogAudience.Channel.MIRAGE);
        }
    }

    @Test
    void enemyCommandersAreAlwaysHiddenAndScoreboardMembershipIsNeverSent() {
        assertThat(FogAudience.allows("COMMANDER", evidence(true, false, false, true, true, true)))
                .isFalse();
        assertThat(FogAudience.allows("COMMANDER", evidence(true, false, true, false, false, false)))
                .isTrue();
        assertThat(FogAudience.allows("COMMANDER", evidence(true, true, false, false, false, false)))
                .isTrue();
        assertThat(FogAudience.allows(
                        "COMMANDER", new FogAudience.Evidence(true, false, false, true, false, false, false)))
                .as("authorized ally commander")
                .isTrue();
        assertThat(FogAudience.allows("SCOREBOARD_TEAM", evidence(true, true, true, true, true, true)))
                .isFalse();
    }

    @Test
    void observersAndOwnersHaveControlsButSharingAloneDoesNotAuthorizeEnemyDetails() {
        for (FogAudience.Channel channel : FogAudience.Channel.values()) {
            boolean allowed = channel != FogAudience.Channel.SCOREBOARD_TEAM;
            assertThat(FogAudience.allows(channel.name(), evidence(true, true, false, false, false, false)))
                    .as("observer %s", channel)
                    .isEqualTo(allowed);
            assertThat(FogAudience.allows(channel.name(), evidence(true, false, true, false, false, false)))
                    .as("owner %s", channel)
                    .isEqualTo(allowed);
        }
        for (String channel : new String[] {"BODY", "COMMANDER", "FIRST_PERSON"}) {
            assertThat(FogAudience.allows(
                            channel, new FogAudience.Evidence(true, false, false, true, false, false, false)))
                    .as("authorized allied body %s", channel)
                    .isTrue();
        }
        assertThat(FogAudience.allows(
                        "DETAIL", new FogAudience.Evidence(true, false, false, true, false, false, false)))
                .isFalse();
    }
}
