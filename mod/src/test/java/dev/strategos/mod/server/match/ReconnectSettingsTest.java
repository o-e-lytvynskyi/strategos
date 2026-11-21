package dev.strategos.mod.server.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.mod.StrategosConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReconnectSettingsTest {
    @Test
    void defaultIsFiveMinutesWithDefeatPolicy() {
        assertThat(ReconnectSettings.DEFAULTS.timeoutSeconds()).isEqualTo(300);
        assertThat(ReconnectSettings.DEFAULTS.timeoutNanos()).isEqualTo(300_000_000_000L);
        assertThat(ReconnectSettings.DEFAULTS.policy()).isEqualTo(ReconnectSettings.Policy.DEFEAT);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 86_400})
    void bothPublishedDurationBoundsAreAcceptedWithoutLosingPrecision(int seconds) {
        var settings = new ReconnectSettings(seconds, ReconnectSettings.Policy.AI_TAKEOVER);
        assertThat(settings.timeoutNanos()).isEqualTo(seconds == 1 ? 1_000_000_000L : 86_400_000_000_000L);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 86_401, Integer.MAX_VALUE})
    void unsupportedDurationsCannotCreateAReservationPolicy(int seconds) {
        assertThatThrownBy(() -> new ReconnectSettings(seconds, ReconnectSettings.Policy.DEFEAT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @SuppressWarnings("NullAway") // Deliberately cross the typed boundary to verify constructor validation.
    void aMissingPolicyIsNotAnImplicitFallback() {
        assertThatThrownBy(() -> new ReconnectSettings(300, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void registeredServerConfigDefaultsMatchThePublicReservationContract() {
        assertThat(StrategosConfig.INSTANCE.reconnectSeconds.getSpec().getDefault())
                .isEqualTo(300);
        assertThat(StrategosConfig.INSTANCE.reconnectPolicy.getSpec().getDefault())
                .isEqualTo(ReconnectSettings.Policy.DEFEAT);
    }
}
