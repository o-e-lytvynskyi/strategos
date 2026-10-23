package dev.strategos.core.protocol;

import dev.strategos.core.shared.CoreApi;

/**
 * A commander's switch between the RTS view and first person (CAM-05, 04 §13.7): to first person at the world column
 * ({@code x}, {@code z}) it wants to stand on, which the server checks (inside the match space, explored, standable,
 * the cooldown over); or back to the RTS view, where the column is unused.
 */
@CoreApi
public record ModeSwitch(boolean firstPerson, double x, double z) {
    public static final ModeSwitch BACK = new ModeSwitch(false, 0, 0);

    public static final WireCodec<ModeSwitch> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, ModeSwitch m) {
            out.writeBoolean(m.firstPerson()).writeDouble(m.x()).writeDouble(m.z());
        }

        @Override
        public ModeSwitch read(WireReader in) {
            return new ModeSwitch(in.readBoolean(), in.readDouble(), in.readDouble());
        }

        @Override
        public int maxSize() {
            return 1 + 2 * 8;
        }
    };
}
