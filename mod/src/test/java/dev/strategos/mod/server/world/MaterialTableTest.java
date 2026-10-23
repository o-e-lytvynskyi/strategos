package dev.strategos.mod.server.world;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.sim.nav.Material;
import dev.strategos.core.sim.nav.MaterialKind;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

/**
 * ECO-02 in L2: the {@code resource_yield} entries the mod bundles decode, a bad one is refused with a reason, and the
 * material table takes each block's entry for all its states, whatever block it is: an entry is all a block needs.
 */
class MaterialTableTest {
    @Test
    void theBundledEntriesDecode() throws IOException {
        JsonElement file;
        try (InputStream in = getClass().getResourceAsStream("/data/strategos/data_maps/block/resource_yield.json")) {
            assertThat(in).isNotNull();
            file = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        for (Map.Entry<String, JsonElement> e :
                file.getAsJsonObject().getAsJsonObject("values").entrySet()) {
            assertThat(ResourceYield.CODEC.parse(JsonOps.INSTANCE, e.getValue()).error())
                    .as(e.getKey())
                    .isEmpty();
        }
    }

    @Test
    void aBadEntryIsRefusedWithItsReason() {
        assertThat(ResourceYield.CODEC
                        .parse(JsonOps.INSTANCE, JsonParser.parseString("{\"kind\": \"gem\"}"))
                        .error()
                        .orElseThrow()
                        .message())
                .contains("unknown material kind 'gem'");
        assertThat(ResourceYield.CODEC
                        .parse(JsonOps.INSTANCE, JsonParser.parseString("{\"kind\": \"log\"}"))
                        .error()
                        .orElseThrow()
                        .message())
                .contains("names its resource and subtype");
    }

    @Test
    void anyBlockWithAnEntryHasItsMaterialInEveryState() {
        ResourceYield melon = new ResourceYield("forage", "food", "fruit", 40, 0);
        MaterialTable table = MaterialTable.build(block -> block == Blocks.MELON ? melon : null);

        assertThat(table.of(Blocks.MELON.defaultBlockState()))
                .isEqualTo(new Material(MaterialKind.FORAGE, "food", "fruit", 40_000));
        assertThat(table.of(Blocks.OAK_LOG.defaultBlockState())).isNull();
        ResourceYield log = new ResourceYield("log", "wood", "tree", 20, 0);
        MaterialTable logs = MaterialTable.build(block -> block == Blocks.OAK_LOG ? log : null);
        Blocks.OAK_LOG
                .getStateDefinition()
                .getPossibleStates()
                .forEach(state -> assertThat(logs.of(state))
                        .as(state.toString())
                        .isEqualTo(new Material(MaterialKind.LOG, "wood", "tree", 20_000)));
    }
}
