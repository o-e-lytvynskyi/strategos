package dev.strategos.core.protocol;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.Hasher;

/**
 * Attack unit {@code target} (ORD-04): close in and strike until it dies or the order ends. With {@code force} (ORD-05)
 * the target may be one of the issuer's own units or buildings; a journal written before the flag reads it as false.
 */
public record Attack(UnitSet units, int target, QueueMode mode, boolean force) implements Command {
    /** The queue mode byte's bit that carries {@code force}, so the wire stays the size it was. */
    private static final int FORCE_BIT = 0x10;

    static final MapCodec<Attack> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                    UnitSet.CODEC.fieldOf("units").forGetter(Attack::units),
                    Codec.INT.fieldOf("target").forGetter(Attack::target),
                    QueueMode.CODEC.fieldOf("mode").forGetter(Attack::mode),
                    Codec.BOOL.optionalFieldOf("force", false).forGetter(Attack::force))
            .apply(i, Attack::new));

    /** Units, the target's handle, queue mode with the force bit on top of it; wire id 4. */
    static final WireCodec<Attack> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, Attack value) {
            WireCodecs.UNIT_SET.write(out, value.units());
            out.writeVarInt(value.target());
            out.writeByte(modeCode(value.mode()) | (value.force() ? FORCE_BIT : 0));
        }

        @Override
        public Attack read(WireReader in) {
            UnitSet units = WireCodecs.UNIT_SET.read(in);
            int target = in.readVarInt();
            int code = in.readUnsignedByte();
            QueueMode mode = switch (code & ~FORCE_BIT) {
                case 0 -> QueueMode.REPLACE;
                case 1 -> QueueMode.QUEUE;
                case 2 -> QueueMode.PUSH_FRONT;
                default -> throw new WireException("unknown queue mode " + code);
            };
            return new Attack(units, target, mode, (code & FORCE_BIT) != 0);
        }

        @Override
        public int maxSize() {
            return WireCodecs.UNIT_SET.maxSize() + 5 + WireCodecs.QUEUE_MODE.maxSize();
        }
    };

    private static int modeCode(QueueMode mode) {
        return switch (mode) {
            case REPLACE -> 0;
            case QUEUE -> 1;
            case PUSH_FRONT -> 2;
        };
    }

    /** An attack that is not forced. */
    public Attack(UnitSet units, int target, QueueMode mode) {
        this(units, target, mode, false);
    }

    /** Replaces the units' orders. */
    public static Attack replace(UnitSet units, int target) {
        return new Attack(units, target, QueueMode.REPLACE);
    }

    @Override
    public void hashInto(Hasher h) {
        h.putByte((byte) 4); // the command type: 1 Move, 2 Stop, 3 SpawnUnits, 4 Attack, 5 AttackMove
        units.hashInto(h);
        h.putInt(target).putString(mode.id());
        if (force) {
            h.putByte((byte) 1); // only a forced attack, so older journals keep their hashes
        }
    }
}
