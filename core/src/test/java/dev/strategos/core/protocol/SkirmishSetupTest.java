package dev.strategos.core.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.protocol.SkirmishSetup.Level;
import dev.strategos.core.protocol.SkirmishSetup.StartResources;
import dev.strategos.core.protocol.SkirmishSetup.Visibility;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** MATCH-02's setup: its defaults, bounds, resource presets and the wire that carries it from the client. */
class SkirmishSetupTest {
    private static byte[] raw(String map, int level, int resources, int cap, int visibility) {
        return new WireWriter()
                .writeString(map, 128)
                .writeByte(level)
                .writeByte(resources)
                .writeVarInt(cap)
                .writeByte(visibility)
                .toByteArray();
    }

    @Test
    void defaultsAreTheFirstSkirmishOnNormalWithFogAnd150() {
        SkirmishSetup d = SkirmishSetup.DEFAULTS;
        assertThat(d.map()).isEqualTo("strategos:first_skirmish");
        assertThat(d.level()).isEqualTo(Level.NORMAL);
        assertThat(d.resources()).isEqualTo(StartResources.MAP);
        assertThat(d.populationCap()).isEqualTo(150);
        assertThat(d.visibility()).isEqualTo(Visibility.FOG);
    }

    @Test
    void everyCombinationRoundTripsOnTheWire() {
        for (Level l : Level.values()) {
            for (StartResources r : StartResources.values()) {
                for (Visibility v : Visibility.values()) {
                    for (int cap : new int[] {50, 150, 200}) {
                        SkirmishSetup s = new SkirmishSetup("strategos:first_skirmish", l, r, cap, v);
                        byte[] b = WireCodec.encode(SkirmishSetup.WIRE, s);
                        assertThat(b).hasSizeLessThanOrEqualTo(SkirmishSetup.WIRE.maxSize());
                        assertThat(WireCodec.decode(SkirmishSetup.WIRE, b)).isEqualTo(s);
                    }
                }
            }
        }
    }

    @Test
    void thePopulationCapIsFiftyToTwoHundred() {
        for (int ok : new int[] {50, 200}) {
            assertThat(new SkirmishSetup("a:b", Level.EASY, StartResources.MAP, ok, Visibility.FOG).populationCap())
                    .isEqualTo(ok);
        }
        for (int bad : new int[] {Integer.MIN_VALUE, -1, 0, 49, 201, Integer.MAX_VALUE}) {
            assertThatThrownBy(() -> new SkirmishSetup("a:b", Level.EASY, StartResources.MAP, bad, Visibility.FOG))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void theMapIdIsNeitherEmptyNorLong() {
        assertThatThrownBy(() -> new SkirmishSetup("", Level.EASY, StartResources.MAP, 150, Visibility.FOG))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        new SkirmishSetup("a:" + "x".repeat(127), Level.EASY, StartResources.MAP, 150, Visibility.FOG))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theResourcePresetsScaleTheScenarioAndRoundDown() {
        Map<String, Long> scenario = Map.of("food", 300L, "stone", 101L);
        SkirmishSetup base = SkirmishSetup.DEFAULTS;
        assertThat(base.startResources(scenario)).containsEntry("food", 300L).containsEntry("stone", 101L);
        SkirmishSetup low = new SkirmishSetup(base.map(), base.level(), StartResources.LOW, 150, base.visibility());
        assertThat(low.startResources(scenario)).containsEntry("food", 150L).containsEntry("stone", 50L);
        SkirmishSetup high = new SkirmishSetup(base.map(), base.level(), StartResources.HIGH, 150, base.visibility());
        assertThat(high.startResources(scenario)).containsEntry("food", 600L).containsEntry("stone", 202L);
    }

    @Test
    void wireCodesAreStableAndDistinct() {
        assertThat(Level.values()).extracting(Level::code).containsExactly(0, 1, 2);
        assertThat(StartResources.values()).extracting(StartResources::code).containsExactly(0, 1, 2);
        assertThat(Visibility.values()).extracting(Visibility::code).containsExactly(0, 1, 2);
    }

    @Test
    void malformedInputFromTheWireIsAWireExceptionNeverAnythingElse() {
        byte[][] bad = {
            raw("a:b", 3, 0, 150, 0), // unknown level
            raw("a:b", 0, 3, 150, 0), // unknown resources
            raw("a:b", 0, 0, 150, 3), // unknown visibility
            raw("a:b", 0, 0, 49, 0), // cap under
            raw("a:b", 0, 0, 201, 0), // cap over
            raw("a:b", 0, 0, Integer.MAX_VALUE, 0), // cap huge
            raw("", 0, 0, 150, 0), // empty map
            raw("a:b", 255, 255, 150, 255),
            new byte[0],
            new byte[] {1},
            new byte[SkirmishSetup.WIRE.maxSize() + 1],
        };
        for (byte[] b : bad) {
            assertThatThrownBy(() -> WireCodec.decode(SkirmishSetup.WIRE, b)).isInstanceOf(WireException.class);
        }
        byte[] trailing = java.util.Arrays.copyOf(raw("a:b", 0, 0, 150, 0), raw("a:b", 0, 0, 150, 0).length + 1);
        assertThatThrownBy(() -> WireCodec.decode(SkirmishSetup.WIRE, trailing)).isInstanceOf(WireException.class);
    }

    @Test
    void aMapStringAboveItsBoundIsRefusedBeforeItIsRead() {
        byte[] b =
                new WireWriter().writeString("x".repeat(129), 256).writeByte(0).toByteArray();
        assertThatThrownBy(() -> WireCodec.decode(SkirmishSetup.WIRE, b)).isInstanceOf(WireException.class);
    }
}
