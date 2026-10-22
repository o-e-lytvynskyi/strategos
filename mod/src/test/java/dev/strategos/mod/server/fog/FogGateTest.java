package dev.strategos.mod.server.fog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * NET-01.7 (spec §1.6 row 8): the fog gate's stub over match ids: another match's member never sees a body, the same
 * match's member and a player in no match do; when the lookup of the viewer's match throws, the body stays hidden.
 */
class FogGateTest {
    private static final UUID VIEWER = UUID.fromString("00000000-0000-0000-0000-000000000007");

    @Test
    void decidesByMatch() {
        assertThat(FogGate.decide(3, 0)).as("a player in no match").isTrue();
        assertThat(FogGate.decide(3, 3)).as("a member of the body's match").isTrue();
        assertThat(FogGate.decide(3, 4)).as("a member of another match").isFalse();
    }

    @Test
    void hidesWhenTheLookupThrows() {
        assertThat(FogGate.canSee(3, VIEWER, uuid -> 3)).isTrue();
        assertThat(FogGate.canSee(3, VIEWER, uuid -> 4)).isFalse();
        assertThat(FogGate.canSee(3, VIEWER, uuid -> {
                    throw new IllegalStateException("no registry");
                }))
                .isFalse();
    }
}
