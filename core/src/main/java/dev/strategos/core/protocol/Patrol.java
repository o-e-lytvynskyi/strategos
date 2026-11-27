package dev.strategos.core.protocol;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.Hasher;
import dev.strategos.core.shared.Vec2d;
import java.util.ArrayList;
import java.util.List;

/**
 * Patrol own {@code units} between {@code points} (ORD-07): each walks to the first point, then the next, and from the
 * last back to the first, for ever, fighting the enemies it meets on every leg as an attack-move does and then
 * resuming the leg. A command that names one point patrols between the unit's own place and it. At most
 * {@value #MAX_POINTS} points. The order never ends by itself: a new order replaces it, and what is queued after it
 * waits for that.
 */
public record Patrol(UnitSet units, List<Vec2d> points, QueueMode mode) implements Command {
    /** The most points one command names. */
    public static final int MAX_POINTS = 16;

    public Patrol {
        points = List.copyOf(points);
    }

    static final MapCodec<Patrol> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                    UnitSet.CODEC.fieldOf("units").forGetter(Patrol::units),
                    Vec2d.CODEC.listOf().fieldOf("points").forGetter(Patrol::points),
                    QueueMode.CODEC.fieldOf("mode").forGetter(Patrol::mode))
            .apply(i, Patrol::new));

    /** Units, a count and the points, queue mode; wire id 36. */
    static final WireCodec<Patrol> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, Patrol value) {
            WireCodecs.UNIT_SET.write(out, value.units());
            out.writeVarInt(value.points().size());
            for (Vec2d point : value.points()) {
                WireCodecs.VEC2D.write(out, point);
            }
            WireCodecs.QUEUE_MODE.write(out, value.mode());
        }

        @Override
        public Patrol read(WireReader in) {
            UnitSet units = WireCodecs.UNIT_SET.read(in);
            int count = in.readVarInt(MAX_POINTS);
            List<Vec2d> points = new ArrayList<>(count);
            for (int k = 0; k < count; k++) {
                points.add(WireCodecs.VEC2D.read(in));
            }
            return new Patrol(units, points, WireCodecs.QUEUE_MODE.read(in));
        }

        @Override
        public int maxSize() {
            return WireCodecs.UNIT_SET.maxSize()
                    + 1
                    + MAX_POINTS * WireCodecs.VEC2D.maxSize()
                    + WireCodecs.QUEUE_MODE.maxSize();
        }
    };

    /** Replaces the units' orders. */
    public static Patrol replace(UnitSet units, List<Vec2d> points) {
        return new Patrol(units, points, QueueMode.REPLACE);
    }

    @Override
    public void hashInto(Hasher h) {
        h.putByte((byte) 36); // the command type: ... 35 UseAbility, 36 Patrol, 37 Guard, 38 FormationMove
        units.hashInto(h);
        h.putInt(points.size());
        for (Vec2d point : points) {
            h.putDouble(point.x()).putDouble(point.z());
        }
        h.putString(mode.id());
    }
}
