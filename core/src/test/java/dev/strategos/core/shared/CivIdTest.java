package dev.strategos.core.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

/** A civilisation id fits {@code match_join}'s 48 bytes, so no loaded id can fail a join. */
class CivIdTest {
    private static String ofLength(int n) {
        return "t:" + "x".repeat(n - 2);
    }

    @Test
    void anIdOfTheMostBytesIsAccepted() {
        assertThat(new CivId(ofLength(CivId.MAX_BYTES)).value()).hasSize(CivId.MAX_BYTES);
    }

    @Test
    void aLongerIdIsRefusedByTheConstructorAndTheCodec() {
        String long49 = ofLength(CivId.MAX_BYTES + 1);
        assertThatThrownBy(() -> new CivId(long49))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("the most is 48");
        assertThat(CivId.CODEC
                        .parse(JsonOps.INSTANCE, new JsonPrimitive(long49))
                        .error())
                .isPresent();
    }
}
