package dev.strategos.core.sim.persist;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import java.util.List;
import org.junit.jupiter.api.Test;

class MigrationsTest {

    /** A test-only step that appends its version to a "trail" string, so the order is visible. */
    private static MigrationStep step(int from) {
        return new MigrationStep() {
            @Override
            public int from() {
                return from;
            }

            @Override
            public <T> Dynamic<T> apply(Dynamic<T> input) {
                String trail = input.get("trail").asString("");
                return input.set("trail", input.createString(trail + from + ">"));
            }
        };
    }

    private static Dynamic<JsonElement> json(String text) {
        return new Dynamic<>(JsonOps.INSTANCE, JsonParser.parseString(text));
    }

    @Test
    void schemaThreeBlocksOldReadersAndBindsOnlyTheOriginalReservedTable() {
        Dynamic<JsonElement> old = json("{\"v\":2,\"buildings\":[{\"template\":31}]}");
        var migrated = Migrations.production().migrate(old, 3).getOrThrow();
        assertThat(migrated.get("v").asInt(0)).isEqualTo(3);
        assertThat(migrated.get("buildings").get().getOrThrow())
                .isEqualTo(old.get("buildings").get().getOrThrow());
        var bindings = migrated.get("templateBindings").asStream().toList();
        assertThat(bindings).hasSize(5);
        assertThat(bindings.getLast().get("index").asInt(-1)).isEqualTo(4);
        assertThat(bindings.getLast().get("id").asString("")).isEqualTo("strategos:building/civic_centre");
        assertThat(Migrations.production().migrate(migrated, 2).error()).isPresent();
    }

    @Test
    void aMissingVersionFails() {
        DataResult<Dynamic<JsonElement>> result = Migrations.production().migrate(json("{\"tick\":1}"), 1);
        assertThat(result.error()).isPresent();
        assertThat(result.error().get().message()).contains("\"v\"");
    }

    @Test
    void aVersionAboveTheTargetFailsNamingBoth() {
        DataResult<Dynamic<JsonElement>> result = Migrations.production().migrate(json("{\"v\":3}"), 1);
        assertThat(result.error()).isPresent();
        assertThat(result.error().get().message()).contains("3").contains("1");
    }

    @Test
    void aChainAppliesInOrderAndRewritesTheVersion() {
        Migrations chain = new Migrations(List.of(step(2), step(1)));

        JsonObject migrated =
                chain.migrate(json("{\"v\":1}"), 3).getOrThrow().getValue().getAsJsonObject();

        assertThat(migrated.get("trail").getAsString()).isEqualTo("1>2>");
        assertThat(migrated.get("v").getAsInt()).isEqualTo(3);
        assertThat(chain.migrate(json("{\"v\":3}"), 3)
                        .getOrThrow()
                        .getValue()
                        .getAsJsonObject()
                        .has("trail"))
                .as("an up-to-date snapshot is left alone")
                .isFalse();
    }

    @Test
    void aGapInTheChainFails() {
        Migrations gap = new Migrations(List.of(step(1), step(3)));
        DataResult<Dynamic<JsonElement>> result = gap.migrate(json("{\"v\":1}"), 4);
        assertThat(result.error()).isPresent();
        assertThat(result.error().get().message()).contains("2").contains("3");
    }
}
