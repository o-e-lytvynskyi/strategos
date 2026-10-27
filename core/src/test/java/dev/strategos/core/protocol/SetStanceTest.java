package dev.strategos.core.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.shared.Stance;
import org.junit.jupiter.api.Test;

class SetStanceTest {
    @Test
    void theCodesAreTheWiresAndNeverMove() {
        assertThat(Stance.values()).extracting(Stance::code).containsExactly(0, 1, 2, 3, 4);
        assertThat(Stance.byId("stand_ground")).isEqualTo(Stance.STAND_GROUND);
        assertThat(Stance.byId("hold")).isNull();
        assertThat(Stance.of(5)).isNull();
    }

    @Test
    void aStanceCodeNoStanceHasIsRefusedOnTheWire() {
        WireWriter out = new WireWriter();
        SetStance.WIRE.write(out, new SetStance(UnitSet.of(7), Stance.PASSIVE));
        byte[] bytes = out.toByteArray();
        assertThat(SetStance.WIRE.read(new WireReader(bytes)).stance()).isEqualTo(Stance.PASSIVE);
        bytes[bytes.length - 1] = 9;
        assertThatThrownBy(() -> SetStance.WIRE.read(new WireReader(bytes))).isInstanceOf(WireException.class);
    }

    @Test
    void aStanceNameNoStanceHasIsRefusedInJson() {
        JsonObject json = JsonParser.parseString(
                        "{\"kind\":\"strategos:set_stance\",\"units\":[7],\"stance\":\"hold\"}")
                .getAsJsonObject();
        assertThat(CommandKinds.DEFAULT.codec().parse(JsonOps.INSTANCE, json).error())
                .isPresent();
    }
}
