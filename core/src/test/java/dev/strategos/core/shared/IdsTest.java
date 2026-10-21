package dev.strategos.core.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

class IdsTest {

    @Test
    void matchIdsStartAtOne() {
        assertThat(new MatchId(1)).hasToString("m1");
        assertThatThrownBy(() -> new MatchId(0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(MatchId.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(0)).error())
                .isPresent();
    }

    @Test
    void factionSlotsAreZeroToFifteen() {
        assertThat(new FactionSlot(0).value()).isZero();
        assertThat(new FactionSlot(15).value()).isEqualTo(15);
        assertThatThrownBy(() -> new FactionSlot(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FactionSlot(16)).isInstanceOf(IllegalArgumentException.class);
        assertThat(FactionSlot.CODEC
                        .parse(JsonOps.INSTANCE, new JsonPrimitive(16))
                        .error())
                .isPresent();
    }

    @Test
    void playerSlotsAreAdminOrZeroToFifteen() {
        assertThat(PlayerSlot.ADMIN.isAdmin()).isTrue();
        assertThat(new PlayerSlot(3).isAdmin()).isFalse();
        assertThatThrownBy(() -> new PlayerSlot(-2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlayerSlot(16)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void adminControlsEveryFactionAndAPlayerItsOwn() {
        assertThat(PlayerSlot.ADMIN.controls(new FactionSlot(0))).isTrue();
        assertThat(PlayerSlot.ADMIN.controls(new FactionSlot(15))).isTrue();
        assertThat(new PlayerSlot(2).controls(new FactionSlot(2))).isTrue();
        assertThat(new PlayerSlot(2).controls(new FactionSlot(3))).isFalse();
    }

    @Test
    void templateIdsAreNamespaceColonPath() {
        assertThat(new TemplateId("strategos:unit/debug_walker").value()).isEqualTo("strategos:unit/debug_walker");
        for (String bad : new String[] {"", "noColon", "Strategos:unit", "strategos:", ":unit", "a:b c"}) {
            assertThatThrownBy(() -> new TemplateId(bad)).as("'%s'", bad).isInstanceOf(IllegalArgumentException.class);
            assertThat(TemplateId.CODEC
                            .parse(JsonOps.INSTANCE, new JsonPrimitive(bad))
                            .error())
                    .as("'%s'", bad)
                    .isPresent();
        }
    }

    @Test
    void everyIdRoundTripsThroughJson() {
        assertRoundTrip(MatchId.CODEC, new MatchId(7));
        assertRoundTrip(FactionSlot.CODEC, new FactionSlot(15));
        assertRoundTrip(PlayerSlot.CODEC, PlayerSlot.ADMIN);
        assertRoundTrip(PlayerSlot.CODEC, new PlayerSlot(4));
        assertRoundTrip(TemplateId.CODEC, new TemplateId("strategos:unit/debug_walker"));
        assertRoundTrip(Vec2d.CODEC, new Vec2d(12.5, -0.25));
    }

    @Test
    void hex64RoundTripsTheEdges() {
        for (long v : new long[] {0, -1, Long.MIN_VALUE, Long.MAX_VALUE, 0x0123456789abcdefL}) {
            String text = Hex64.format(v);
            assertThat(text).hasSize(16).matches("[0-9a-f]{16}");
            assertThat(Hex64.parse(text)).isEqualTo(v);
            assertRoundTrip(Hex64.CODEC, v);
        }
        assertThat(Hex64.format(0)).isEqualTo("0000000000000000");
        assertThat(Hex64.format(-1)).isEqualTo("ffffffffffffffff");
        assertThat(Hex64.format(Long.MIN_VALUE)).isEqualTo("8000000000000000");
    }

    @Test
    void hex64RejectsAnythingButSixteenLowercaseDigits() {
        for (String bad :
                new String[] {"", "0", "000000000000000", "00000000000000000", "FFFFFFFFFFFFFFFF", "000000000000000g"
                }) {
            assertThatThrownBy(() -> Hex64.parse(bad)).as("'%s'", bad).isInstanceOf(IllegalArgumentException.class);
            assertThat(Hex64.CODEC
                            .parse(JsonOps.INSTANCE, new JsonPrimitive(bad))
                            .error())
                    .as("'%s'", bad)
                    .isPresent();
        }
    }

    private static <T> void assertRoundTrip(Codec<T> codec, T value) {
        JsonElement json = codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow();
        assertThat(codec.parse(JsonOps.INSTANCE, json).getOrThrow()).isEqualTo(value);
    }
}
