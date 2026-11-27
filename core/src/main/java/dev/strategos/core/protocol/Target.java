package dev.strategos.core.protocol;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.Hasher;
import java.util.Locale;

/**
 * What an ability is aimed at (UNIT-10): nothing (the user itself), a point on the ground plane, or a unit by handle.
 * Permissive like every command record: the rule refuses a kind the ability does not take.
 */
public record Target(Kind kind, double x, double z, int unit) {
    /** The shapes a target has. */
    public enum Kind {
        NONE,
        POINT,
        UNIT;

        static final Codec<Kind> CODEC = Codec.STRING.comapFlatMap(Kind::read, Kind::id);

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        int code() {
            return switch (this) {
                case NONE -> 0;
                case POINT -> 1;
                case UNIT -> 2;
            };
        }

        static Kind ofCode(int code) {
            return switch (code) {
                case 0 -> NONE;
                case 1 -> POINT;
                case 2 -> UNIT;
                default -> throw new WireException("unknown target kind " + code);
            };
        }

        private static DataResult<Kind> read(String id) {
            for (Kind kind : values()) {
                if (kind.id().equals(id)) {
                    return DataResult.success(kind);
                }
            }
            return DataResult.error(() -> "unknown target kind '" + id + "'");
        }
    }

    static final MapCodec<Target> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                    Kind.CODEC.fieldOf("kind").forGetter(Target::kind),
                    Codec.DOUBLE.optionalFieldOf("x", 0.0).forGetter(Target::x),
                    Codec.DOUBLE.optionalFieldOf("z", 0.0).forGetter(Target::z),
                    Codec.INT.optionalFieldOf("unit", 0).forGetter(Target::unit))
            .apply(i, Target::new));

    /** One byte for the kind, then nothing, two doubles or a varint. */
    static final WireCodec<Target> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, Target value) {
            out.writeByte(value.kind().code());
            switch (value.kind()) {
                case NONE -> {}
                case POINT -> out.writeDouble(value.x()).writeDouble(value.z());
                case UNIT -> out.writeVarInt(value.unit());
            }
        }

        @Override
        public Target read(WireReader in) {
            int code = in.readUnsignedByte();
            return switch (Kind.ofCode(code)) {
                case NONE -> none();
                case POINT -> {
                    double x = in.readDouble();
                    double z = in.readDouble();
                    if (!Double.isFinite(x) || !Double.isFinite(z)) {
                        throw new WireException("a target point is not finite");
                    }
                    yield point(x, z);
                }
                case UNIT -> unit(in.readVarInt());
            };
        }

        @Override
        public int maxSize() {
            return 1 + 16;
        }
    };

    /** The user itself. */
    public static Target none() {
        return new Target(Kind.NONE, 0, 0, 0);
    }

    /** The ground at ({@code x}, {@code z}). */
    public static Target point(double x, double z) {
        return new Target(Kind.POINT, x, z, 0);
    }

    /** Unit {@code handle}. */
    public static Target unit(int handle) {
        return new Target(Kind.UNIT, 0, 0, handle);
    }

    void hashInto(Hasher h) {
        h.putByte((byte) kind.code());
        switch (kind) {
            case NONE -> {}
            case POINT -> h.putDouble(x).putDouble(z);
            case UNIT -> h.putInt(unit);
        }
    }
}
