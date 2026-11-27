package dev.strategos.core.protocol;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Locale;

/** The shape a formation keeps (ORD-06): a square-ish block, or a wide line of three ranks. */
public enum FormationShape {
    BOX,
    LINE;

    /** Lower-case names, as in Minecraft's own data. */
    public static final Codec<FormationShape> CODEC =
            Codec.STRING.comapFlatMap(FormationShape::read, FormationShape::id);

    /** One byte: 0 box, 1 line. */
    static final WireCodec<FormationShape> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, FormationShape value) {
            out.writeByte(value.code());
        }

        @Override
        public FormationShape read(WireReader in) {
            int code = in.readUnsignedByte();
            return switch (code) {
                case 0 -> BOX;
                case 1 -> LINE;
                default -> throw new WireException("unknown formation shape " + code);
            };
        }

        @Override
        public int maxSize() {
            return 1;
        }
    };

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The shape stored as {@code code} in a save: 0 box, 1 line. */
    public static FormationShape ofCode(int code) {
        return switch (code) {
            case 0 -> BOX;
            case 1 -> LINE;
            default -> throw new IllegalArgumentException("no formation shape " + code);
        };
    }

    /** The code a save and the hash keep: never the ordinal's accident, a number assigned once. */
    public int code() {
        return this == BOX ? 0 : 1;
    }

    private static DataResult<FormationShape> read(String id) {
        for (FormationShape shape : values()) {
            if (shape.id().equals(id)) {
                return DataResult.success(shape);
            }
        }
        return DataResult.error(() -> "unknown formation shape '" + id + "'");
    }
}
