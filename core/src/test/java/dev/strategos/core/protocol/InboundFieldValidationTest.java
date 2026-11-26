package dev.strategos.core.protocol;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class InboundFieldValidationTest {
    @Test
    void aCameraRequestCannotCarryANonFiniteAngleAndMalformedBytesRemainAWireFault() {
        assertThatThrownBy(() -> new CameraJump(1, 2, 3, Float.NaN, 0)).isInstanceOf(IllegalArgumentException.class);
        byte[] bad = new WireWriter()
                .writeDouble(1)
                .writeDouble(2)
                .writeDouble(3)
                .writeFloat(Float.NaN)
                .writeFloat(0)
                .toByteArray();
        assertThatThrownBy(() -> WireCodec.decode(CameraJump.WIRE, bad)).isInstanceOf(WireException.class);
    }

    @Test
    void aModeRequestCannotHideNonFiniteCoordinatesInItsUnusedColumn() {
        assertThatThrownBy(() -> new ModeSwitch(false, Double.NaN, 0)).isInstanceOf(IllegalArgumentException.class);
        byte[] bad = new WireWriter()
                .writeBoolean(false)
                .writeDouble(Double.NaN)
                .writeDouble(0)
                .toByteArray();
        assertThatThrownBy(() -> WireCodec.decode(ModeSwitch.WIRE, bad)).isInstanceOf(WireException.class);
    }

    @Test
    void unsupportedSpeedCannotEnterTheTypedModelAndDecodeStillHasItsWireContract() {
        assertThatThrownBy(() -> new MatchControl(MatchControl.SPEED, 2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MatchControl(MatchControl.PAUSE, Float.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class);
        byte[] bad =
                new WireWriter().writeByte(MatchControl.SPEED).writeFloat(2).toByteArray();
        assertThatThrownBy(() -> WireCodec.decode(MatchControl.WIRE, bad)).isInstanceOf(WireException.class);
    }
}
