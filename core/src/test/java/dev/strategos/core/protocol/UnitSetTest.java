package dev.strategos.core.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class UnitSetTest {

    @Test
    void sortsAndDropsDuplicates() {
        UnitSet set = UnitSet.of(30, 10, 20, 10, 30);
        assertThat(set.toArray()).containsExactly(10, 20, 30);
        assertThat(set.size()).isEqualTo(3);
        assertThat(set.get(0)).isEqualTo(10);
        assertThat(set.contains(20)).isTrue();
        assertThat(set.contains(25)).isFalse();
    }

    @Test
    void holdsAtMostThreeHundredUnits() {
        assertThat(UnitSet.of(IntStream.rangeClosed(1, 300).toArray()).size()).isEqualTo(300);
        assertThatThrownBy(() -> UnitSet.of(IntStream.rangeClosed(1, 301).toArray()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(UnitSet.of(IntStream.rangeClosed(1, 301).map(i -> i % 300).toArray())
                        .size())
                .as("duplicates count once")
                .isEqualTo(300);
    }

    @Test
    void isEqualByContent() {
        assertThat(UnitSet.of(1, 2, 3)).isEqualTo(UnitSet.of(3, 2, 1, 1)).hasSameHashCodeAs(UnitSet.of(3, 2, 1));
        assertThat(UnitSet.of(1, 2)).isNotEqualTo(UnitSet.of(1, 2, 3));
    }

    @Test
    void toArrayIsACopy() {
        UnitSet set = UnitSet.of(1, 2);
        set.toArray()[0] = 99;
        assertThat(set.get(0)).isEqualTo(1);
    }

    @Test
    void roundTripsThroughJson() {
        UnitSet set = UnitSet.of(7, 3, 5);
        JsonElement json = UnitSet.CODEC.encodeStart(JsonOps.INSTANCE, set).getOrThrow();
        assertThat(json.toString()).isEqualTo("[3,5,7]");
        assertThat(UnitSet.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow()).isEqualTo(set);
    }

    @Test
    void decodingMoreThanThreeHundredFails() {
        JsonArray json = new JsonArray();
        IntStream.rangeClosed(1, 301).forEach(json::add);
        assertThat(UnitSet.CODEC.parse(JsonOps.INSTANCE, json).error()).isPresent();
    }
}
