package dev.strategos.mod.server.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.sim.MatchBounds;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

/** CORE-01.3 (§1.6 row 26): a match's space from GameTest bounds or a centre, its overlap rule and coordinates. */
class MatchSpaceTest {
    /** A 32 x 32 GameTest structure, 6 blocks high, at (100, -60, 200). */
    private static final AABB ARENA = new AABB(100, -60, 200, 132, -54, 232);

    @Test
    void fromBoundsTakesTheWholeStructure() {
        MatchSpace space = MatchSpace.fromBounds(Level.OVERWORLD, ARENA);

        assertThat(space.level()).isEqualTo(Level.OVERWORLD);
        assertThat(space.origin()).isEqualTo(new BlockPos(100, -60, 200));
        assertThat(space.sizeX()).isEqualTo(32);
        assertThat(space.sizeZ()).isEqualTo(32);
        assertThat(space.minY()).isEqualTo(-60);
        assertThat(space.maxY()).as("the top block of the box").isEqualTo(-55);
        assertThat(space.toBounds(-59.0)).isEqualTo(new MatchBounds(32, 32, -60, -55, -59.0));
    }

    @Test
    void fromBoundsTakesAPartOfTheStructure() {
        MatchSpace space = MatchSpace.fromBounds(Level.OVERWORLD, ARENA, 4, 16, 12, 16);

        assertThat(space.origin()).isEqualTo(new BlockPos(104, -60, 216));
        assertThat(space.sizeX()).isEqualTo(12);
        assertThat(space.sizeZ()).isEqualTo(16);
        assertThatThrownBy(() -> MatchSpace.fromBounds(Level.OVERWORLD, ARENA, 4, 17, 12, 16))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not fit");
    }

    @Test
    void centredOnPutsTheCentreInTheMiddle() {
        MatchSpace space = MatchSpace.centredOn(Level.OVERWORLD, new BlockPos(0, 64, 0), 16, 16, 4, 20);

        assertThat(space.origin()).isEqualTo(new BlockPos(-8, 60, -8));
        assertThat(space.minY()).isEqualTo(60);
        assertThat(space.maxY()).isEqualTo(84);
        assertThat(space.contains(new BlockPos(0, 64, 0))).isTrue();
        assertThat(space.contains(new BlockPos(-8, 60, 7))).isTrue();
        assertThat(space.contains(new BlockPos(8, 64, 0))).as("x is half-open").isFalse();
        assertThat(space.contains(new BlockPos(0, 85, 0))).isFalse();
        assertThat(space.contains(new BlockPos(0, 59, 0))).isFalse();
    }

    @Test
    void localAndWorldCoordinatesConvertBothWays() {
        MatchSpace space = MatchSpace.fromBounds(Level.OVERWORLD, ARENA);

        assertThat(space.worldX(4.5)).isEqualTo(104.5);
        assertThat(space.worldZ(0.25)).isEqualTo(200.25);
        assertThat(space.localX(104.5)).isEqualTo(4.5);
        assertThat(space.localZ(231.75)).isEqualTo(31.75);
        assertThat(space.minChunk()).isEqualTo(new ChunkPos(6, 12));
        assertThat(space.maxChunk()).as("blocks 131 and 231").isEqualTo(new ChunkPos(8, 14));
    }

    @Test
    void aGapUnderTheMarginOverlapsAndAGapOfTheMarginDoesNot() {
        MatchSpace a = new MatchSpace(Level.OVERWORLD, new BlockPos(0, 0, 0), 16, 16, 0, 15);

        assertThat(a.overlaps(shifted(a, 16, 0), 2)).as("touching").isTrue();
        assertThat(a.overlaps(shifted(a, 17, 0), 2)).as("a gap of 1").isTrue();
        assertThat(a.overlaps(shifted(a, 18, 0), 2)).as("a gap of 2").isFalse();
        assertThat(a.overlaps(shifted(a, 0, -17), 2))
                .as("a gap of 1 on the other side")
                .isTrue();
        assertThat(a.overlaps(shifted(a, 0, -18), 2)).isFalse();
        assertThat(a.overlaps(shifted(a, 5, 5), 2)).as("intersecting").isTrue();
        assertThat(a.overlaps(new MatchSpace(Level.NETHER, a.origin(), 16, 16, 0, 15), 2))
                .as("another level")
                .isFalse();
        assertThat(a.overlaps(new MatchSpace(Level.OVERWORLD, new BlockPos(0, 18, 0), 16, 16, 18, 30), 2))
                .as("a band 2 blocks above")
                .isFalse();
    }

    private static MatchSpace shifted(MatchSpace s, int dx, int dz) {
        return new MatchSpace(s.level(), s.origin().offset(dx, 0, dz), s.sizeX(), s.sizeZ(), s.minY(), s.maxY());
    }

    @Test
    void theCodecRoundTripsAndReportsABadSpaceAsAnError() {
        MatchSpace space = MatchSpace.fromBounds(Level.OVERWORLD, ARENA, 4, 16, 12, 16);

        JsonElement json = MatchSpace.CODEC.encodeStart(JsonOps.INSTANCE, space).getOrThrow();

        assertThat(MatchSpace.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow()).isEqualTo(space);
        JsonElement empty = JsonParser.parseString(json.toString().replace("\"sizeX\":12", "\"sizeX\":0"));
        assertThat(MatchSpace.CODEC.parse(JsonOps.INSTANCE, empty).error()).isPresent();
    }
}
