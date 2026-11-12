package dev.strategos.core.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.shared.Hasher;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** BLD-16's commands: the wire and JSON forms round-trip within their bounds, and their hashes differ by type. */
class GarrisonCommandsTest {
    private static final int BUILDING = (1 << 19) + 3;

    @Test
    void theWireFormsRoundTripAtTheirPinnedSizes() {
        Garrison garrison = new Garrison(UnitSet.of(5, 6), BUILDING);
        Ungarrison ungarrison = new Ungarrison(BUILDING, UnitSet.of(5, 6));
        Ungarrison everyone = new Ungarrison(BUILDING, UnitSet.of());
        WireWriter out = new WireWriter();
        Garrison.WIRE.write(out, garrison);
        byte[] bytes = out.toByteArray();
        assertThat(bytes).as("a count, two handles and a three-byte building").hasSize(6);
        assertThat(Garrison.WIRE.read(new WireReader(bytes))).isEqualTo(garrison);
        out = new WireWriter();
        Ungarrison.WIRE.write(out, ungarrison);
        bytes = out.toByteArray();
        assertThat(bytes).hasSize(6);
        assertThat(Ungarrison.WIRE.read(new WireReader(bytes))).isEqualTo(ungarrison);
        out = new WireWriter();
        Ungarrison.WIRE.write(out, everyone);
        bytes = out.toByteArray();
        assertThat(bytes).as("no units: the building and a zero count").hasSize(4);
        assertThat(Ungarrison.WIRE.read(new WireReader(bytes))).isEqualTo(everyone);
    }

    @Test
    void theLargestOfThemStayWithinTheirBounds() {
        int[] spread = new int[UnitSet.MAX_SIZE];
        Arrays.setAll(spread, i -> Integer.MIN_VALUE + i * 14_000_000);
        UnitSet widest = UnitSet.of(spread);
        WireWriter out = new WireWriter();
        Garrison.WIRE.write(out, new Garrison(widest, Integer.MAX_VALUE));
        assertThat(out.toByteArray().length).isLessThanOrEqualTo(Garrison.WIRE.maxSize());
        out = new WireWriter();
        Ungarrison.WIRE.write(out, new Ungarrison(Integer.MAX_VALUE, widest));
        assertThat(out.toByteArray().length).isLessThanOrEqualTo(Ungarrison.WIRE.maxSize());
    }

    @Test
    void theJsonFormsNameTheirKindsAndRoundTrip() {
        Garrison garrison = new Garrison(UnitSet.of(5, 6), BUILDING);
        JsonObject json = CommandKinds.DEFAULT
                .codec()
                .encodeStart(JsonOps.INSTANCE, garrison)
                .getOrThrow()
                .getAsJsonObject();
        assertThat(json.get("kind").getAsString()).isEqualTo("strategos:garrison");
        assertThat(CommandKinds.DEFAULT.codec().parse(JsonOps.INSTANCE, json).getOrThrow())
                .isEqualTo(garrison);
        JsonObject written = JsonParser.parseString(
                        "{\"kind\":\"strategos:ungarrison\",\"building\":" + BUILDING + ",\"units\":[]}")
                .getAsJsonObject();
        assertThat(CommandKinds.DEFAULT.codec().parse(JsonOps.INSTANCE, written).getOrThrow())
                .isEqualTo(new Ungarrison(BUILDING, UnitSet.of()));
    }

    @Test
    void theHashesTellTheTypesApart() {
        Hasher a = new Hasher(0);
        new Garrison(UnitSet.of(5), BUILDING).hashInto(a);
        Hasher b = new Hasher(0);
        new Ungarrison(BUILDING, UnitSet.of(5)).hashInto(b);
        assertThat(a.finish()).isNotEqualTo(b.finish());
    }

    @Test
    void theBellCommandsRoundTripAtTheirPinnedSizesAndNameTheirKinds() {
        RingBell ring = new RingBell(BUILDING);
        AllClear clear = new AllClear(BUILDING);
        WireWriter out = new WireWriter();
        RingBell.WIRE.write(out, ring);
        assertThat(out.toByteArray()).as("a three-byte building").hasSize(3);
        assertThat(RingBell.WIRE.read(new WireReader(out.toByteArray()))).isEqualTo(ring);
        out = new WireWriter();
        AllClear.WIRE.write(out, clear);
        assertThat(out.toByteArray()).hasSize(3);
        assertThat(AllClear.WIRE.read(new WireReader(out.toByteArray()))).isEqualTo(clear);
        out = new WireWriter();
        RingBell.WIRE.write(out, new RingBell(Integer.MAX_VALUE));
        assertThat(out.toByteArray().length).isLessThanOrEqualTo(RingBell.WIRE.maxSize());
        out = new WireWriter();
        AllClear.WIRE.write(out, new AllClear(Integer.MAX_VALUE));
        assertThat(out.toByteArray().length).isLessThanOrEqualTo(AllClear.WIRE.maxSize());

        assertThat(CommandKinds.DEFAULT.of(ring).wireId()).isEqualTo(28);
        assertThat(CommandKinds.DEFAULT.of(clear).wireId()).isEqualTo(29);
        assertThat(CommandKinds.DEFAULT.of(ring).id()).isEqualTo("strategos:ring_bell");
        assertThat(CommandKinds.DEFAULT.of(clear).id()).isEqualTo("strategos:all_clear");
        JsonObject json = CommandKinds.DEFAULT
                .codec()
                .encodeStart(JsonOps.INSTANCE, ring)
                .getOrThrow()
                .getAsJsonObject();
        assertThat(CommandKinds.DEFAULT.codec().parse(JsonOps.INSTANCE, json).getOrThrow())
                .isEqualTo(ring);
        Hasher a = new Hasher(0);
        ring.hashInto(a);
        Hasher b = new Hasher(0);
        clear.hashInto(b);
        assertThat(a.finish()).isNotEqualTo(b.finish());
    }
}
