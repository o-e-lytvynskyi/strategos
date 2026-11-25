package dev.strategos.core.protocol;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.Hasher;
import dev.strategos.core.shared.TemplateId;
import java.util.List;

/**
 * Place a line of walls of {@code wall} for the issuer's faction (BLD-13): a straight run from match-local cell
 * ({@code x1}, {@code z1}) towards ({@code x2}, {@code z2}) along the dominant axis, cut into segments the template's
 * {@code Wall/segment} blocks long; the segments whose indices are in {@code gates} are the gate template's instead.
 * Each segment is its own foundation with its own placement check, so a refused one leaves a gap; {@code builders} go
 * to build them in order. At most {@value #MAX_SEGMENTS} segments, and as many gates.
 */
public record PlaceWall(TemplateId wall, int x1, int z1, int x2, int z2, List<Integer> gates, UnitSet builders)
        implements Command {
    /** The most segments one command lays (and the most gate indices it names). */
    public static final int MAX_SEGMENTS = 64;

    public PlaceWall {
        gates = List.copyOf(gates);
    }

    static final MapCodec<PlaceWall> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                    TemplateId.CODEC.fieldOf("wall").forGetter(PlaceWall::wall),
                    Codec.INT.fieldOf("x1").forGetter(PlaceWall::x1),
                    Codec.INT.fieldOf("z1").forGetter(PlaceWall::z1),
                    Codec.INT.fieldOf("x2").forGetter(PlaceWall::x2),
                    Codec.INT.fieldOf("z2").forGetter(PlaceWall::z2),
                    Codec.INT.listOf().optionalFieldOf("gates", List.of()).forGetter(PlaceWall::gates),
                    UnitSet.CODEC.fieldOf("builders").forGetter(PlaceWall::builders))
            .apply(i, PlaceWall::new));

    /** Template, the two ends, the gate indices, the builders; wire id 34. */
    static final WireCodec<PlaceWall> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, PlaceWall value) {
            WireCodecs.TEMPLATE_ID.write(out, value.wall());
            out.writeZigZag(value.x1())
                    .writeZigZag(value.z1())
                    .writeZigZag(value.x2())
                    .writeZigZag(value.z2());
            out.writeVarInt(value.gates().size());
            for (int gate : value.gates()) {
                out.writeVarInt(gate);
            }
            WireCodecs.UNIT_SET.write(out, value.builders());
        }

        @Override
        public PlaceWall read(WireReader in) {
            TemplateId wall = WireCodecs.TEMPLATE_ID.read(in);
            int x1 = in.readZigZagInt();
            int z1 = in.readZigZagInt();
            int x2 = in.readZigZagInt();
            int z2 = in.readZigZagInt();
            int count = in.readVarInt(MAX_SEGMENTS);
            java.util.ArrayList<Integer> gates = new java.util.ArrayList<>(count);
            for (int k = 0; k < count; k++) {
                gates.add(in.readVarInt());
            }
            return new PlaceWall(wall, x1, z1, x2, z2, gates, WireCodecs.UNIT_SET.read(in));
        }

        @Override
        public int maxSize() {
            return WireCodecs.TEMPLATE_ID.maxSize() + 4 * 5 + 1 + MAX_SEGMENTS * 5 + WireCodecs.UNIT_SET.maxSize();
        }
    };

    @Override
    public void hashInto(Hasher h) {
        h.putByte((byte) 34); // the command type: ... 32 Capture, 33 UpgradeBuilding, 34 PlaceWall
        h.putString(wall.value()).putInt(x1).putInt(z1).putInt(x2).putInt(z2);
        h.putInt(gates.size());
        for (int gate : gates) {
            h.putInt(gate);
        }
        builders.hashInto(h);
    }
}
