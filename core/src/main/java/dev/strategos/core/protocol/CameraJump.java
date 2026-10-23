package dev.strategos.core.protocol;

import dev.strategos.core.shared.CoreApi;

/**
 * A commander's request to move its camera far at once (CAM-03, ADR-0017): the eye's world position and angles, which
 * the server checks and answers with a teleport at the start of its next tick (spike S9). Pans never use it.
 */
@CoreApi
public record CameraJump(double x, double y, double z, float yaw, float pitch) {
    public static final WireCodec<CameraJump> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, CameraJump j) {
            out.writeDouble(j.x())
                    .writeDouble(j.y())
                    .writeDouble(j.z())
                    .writeFloat(j.yaw())
                    .writeFloat(j.pitch());
        }

        @Override
        public CameraJump read(WireReader in) {
            return new CameraJump(in.readDouble(), in.readDouble(), in.readDouble(), in.readFloat(), in.readFloat());
        }

        @Override
        public int maxSize() {
            return 3 * 8 + 2 * 4;
        }
    };
}
