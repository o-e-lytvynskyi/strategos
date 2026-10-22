package dev.strategos.mod.server.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.mod.registry.ModPayloads;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * NET-01.3 (spec §7.3): every payload type declares where it goes, in our namespace, once: a server-to-client one at
 * least one audience, a client-to-server one none; the gateway refuses every audience its type does not declare, and
 * any payload the server does not send.
 */
class PayloadAudienceTest {
    @Test
    void everyPayloadDeclaresItsAudiences() {
        Set<String> ids = new HashSet<>();
        for (PayloadSpec spec : ModPayloads.specs()) {
            assertThat(spec.id().getNamespace()).isEqualTo("strategos");
            assertThat(ids.add(spec.id().toString()))
                    .as("%s declared once", spec.id())
                    .isTrue();
            assertThat(spec.cap()).as("%s's cap", spec.id()).isPositive();
            if (spec.toClient()) {
                assertThat(spec.audiences()).as("%s's audiences", spec.id()).isNotEmpty();
            } else {
                assertThat(spec.audiences()).isEmpty();
            }
        }
        assertThat(ids)
                .contains("strategos:commands", "strategos:match_join", "strategos:match_state", "strategos:feedback");
    }

    @Test
    void theGatewayRefusesAnyOtherAudience() {
        for (PayloadSpec spec : ModPayloads.specs()) {
            for (Audience.Kind kind : Audience.Kind.values()) {
                if (spec.toClient() && spec.audiences().contains(kind)) {
                    assertThatCode(() -> NetGateway.check(spec, spec.id(), kind))
                            .doesNotThrowAnyException();
                } else {
                    assertThatThrownBy(() -> NetGateway.check(spec, spec.id(), kind))
                            .as("%s to %s", spec.id(), kind)
                            .isInstanceOf(IllegalArgumentException.class);
                }
            }
        }
        assertThatThrownBy(
                        () -> NetGateway.check(null, ModPayloads.specs().get(0).id(), Audience.Kind.PLAYER))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
