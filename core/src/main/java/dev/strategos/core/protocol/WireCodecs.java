package dev.strategos.core.protocol;

import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import org.jspecify.annotations.Nullable;

/** Wire codecs of the shared value types the command and payload codecs are built from. */
public final class WireCodecs {
    /** Two raw doubles; a non-finite coordinate does not decode. */
    public static final WireCodec<Vec2d> VEC2D = new WireCodec<>() {
        @Override
        public void write(WireWriter out, Vec2d value) {
            out.writeDouble(value.x()).writeDouble(value.z());
        }

        @Override
        public Vec2d read(WireReader in) {
            double x = in.readDouble();
            double z = in.readDouble();
            if (!Double.isFinite(x) || !Double.isFinite(z)) {
                throw new WireException("non-finite position " + x + ", " + z);
            }
            return new Vec2d(x, z);
        }

        @Override
        public int maxSize() {
            return 16;
        }
    };

    /**
     * A count, the first handle as a zigzag varint, then each later handle as its distance from the one before (the set
     * is sorted and distinct, so every distance is at least 1): consecutive handles cost a byte each.
     */
    public static final WireCodec<UnitSet> UNIT_SET = new WireCodec<>() {
        @Override
        public void write(WireWriter out, UnitSet value) {
            out.writeVarInt(value.size());
            for (int i = 0; i < value.size(); i++) {
                if (i == 0) {
                    out.writeZigZag(value.get(0));
                } else {
                    out.writeVarLong((long) value.get(i) - value.get(i - 1));
                }
            }
        }

        @Override
        public UnitSet read(WireReader in) {
            int count = in.readVarInt(Limits.UNITS_PER_COMMAND);
            int[] handles = new int[count];
            long previous = 0;
            for (int i = 0; i < count; i++) {
                long handle;
                if (i == 0) {
                    handle = in.readZigZagInt();
                } else {
                    long step = in.readVarLong();
                    if (step < 1) {
                        throw new WireException("unit handles out of order or repeated");
                    }
                    handle = previous + step;
                    if (handle > Integer.MAX_VALUE) {
                        throw new WireException("unit handle beyond the int range");
                    }
                }
                handles[i] = (int) handle;
                previous = handle;
            }
            return UnitSet.of(handles);
        }

        @Override
        public int maxSize() {
            return 2 + 5 + (Limits.UNITS_PER_COMMAND - 1) * 5;
        }
    };

    /** One byte: 0 replace, 1 queue, 2 push front. */
    public static final WireCodec<QueueMode> QUEUE_MODE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, QueueMode value) {
            out.writeByte(
                    switch (value) {
                        case REPLACE -> 0;
                        case QUEUE -> 1;
                        case PUSH_FRONT -> 2;
                    });
        }

        @Override
        public QueueMode read(WireReader in) {
            int code = in.readUnsignedByte();
            return switch (code) {
                case 0 -> QueueMode.REPLACE;
                case 1 -> QueueMode.QUEUE;
                case 2 -> QueueMode.PUSH_FRONT;
                default -> throw new WireException("unknown queue mode " + code);
            };
        }

        @Override
        public int maxSize() {
            return 1;
        }
    };

    public static final WireCodec<TemplateId> TEMPLATE_ID = new WireCodec<>() {
        @Override
        public void write(WireWriter out, TemplateId value) {
            out.writeString(value.value(), Limits.ID_BYTES);
        }

        @Override
        public TemplateId read(WireReader in) {
            String id = in.readString(Limits.ID_BYTES);
            try {
                return new TemplateId(id);
            } catch (IllegalArgumentException e) {
                throw new WireException(String.valueOf(e.getMessage()));
            }
        }

        @Override
        public int maxSize() {
            return 2 + Limits.ID_BYTES;
        }
    };

    public static final WireCodec<FactionSlot> FACTION_SLOT = new WireCodec<>() {
        @Override
        public void write(WireWriter out, FactionSlot value) {
            out.writeByte(value.value());
        }

        @Override
        public FactionSlot read(WireReader in) {
            int slot = in.readUnsignedByte();
            if (slot > FactionSlot.MAX) {
                throw new WireException("faction slot " + slot + " above " + FactionSlot.MAX);
            }
            return new FactionSlot(slot);
        }

        @Override
        public int maxSize() {
            return 1;
        }
    };

    /** A player slot, -1 (the administrator) to {@link FactionSlot#MAX}, as a zigzag varint. */
    public static final WireCodec<PlayerSlot> PLAYER_SLOT = new WireCodec<>() {
        @Override
        public void write(WireWriter out, PlayerSlot value) {
            out.writeZigZag(value.value());
        }

        @Override
        public PlayerSlot read(WireReader in) {
            int slot = in.readZigZagInt();
            if (slot < -1 || slot > FactionSlot.MAX) {
                throw new WireException("player slot " + slot + " outside -1.." + FactionSlot.MAX);
            }
            return new PlayerSlot(slot);
        }

        @Override
        public int maxSize() {
            return 1;
        }
    };

    public static final WireCodec<CommandOutcome> COMMAND_OUTCOME = new WireCodec<>() {
        @Override
        public void write(WireWriter out, CommandOutcome value) {
            out.writeByte(
                    switch (value) {
                        case ACCEPTED -> 0;
                        case PARTIAL -> 1;
                        case REJECTED -> 2;
                    });
        }

        @Override
        public CommandOutcome read(WireReader in) {
            int code = in.readUnsignedByte();
            return switch (code) {
                case 0 -> CommandOutcome.ACCEPTED;
                case 1 -> CommandOutcome.PARTIAL;
                case 2 -> CommandOutcome.REJECTED;
                default -> throw new WireException("unknown command outcome " + code);
            };
        }

        @Override
        public int maxSize() {
            return 1;
        }
    };

    /** A refusal's reason as one byte, or 0 for none. */
    public static void writeReason(WireWriter out, @Nullable RejectReason value) {
        out.writeByte(
                value == null
                        ? 0
                        : switch (value) {
                            case UNKNOWN_TARGET -> 1;
                            case NOT_OWNER -> 2;
                            case OUT_OF_BOUNDS -> 3;
                            case UNSUPPORTED -> 4;
                            case UNKNOWN_TEMPLATE -> 5;
                            case UNIT_CAP -> 6;
                            case NOT_PERMITTED -> 7;
                            case INVALID_ARGUMENT -> 8;
                            case QUEUE_FULL -> 9;
                        });
    }

    public static @Nullable RejectReason readReason(WireReader in) {
        int code = in.readUnsignedByte();
        return switch (code) {
            case 0 -> null;
            case 1 -> RejectReason.UNKNOWN_TARGET;
            case 2 -> RejectReason.NOT_OWNER;
            case 3 -> RejectReason.OUT_OF_BOUNDS;
            case 4 -> RejectReason.UNSUPPORTED;
            case 5 -> RejectReason.UNKNOWN_TEMPLATE;
            case 6 -> RejectReason.UNIT_CAP;
            case 7 -> RejectReason.NOT_PERMITTED;
            case 8 -> RejectReason.INVALID_ARGUMENT;
            case 9 -> RejectReason.QUEUE_FULL;
            default -> throw new WireException("unknown reject reason " + code);
        };
    }

    public static final WireCodec<NoticeKind> NOTICE_KIND = new WireCodec<>() {
        @Override
        public void write(WireWriter out, NoticeKind value) {
            out.writeByte(
                    switch (value) {
                        case ORDER_FAILED -> 0;
                        case STUCK -> 1;
                        case UNIT_LOST -> 2;
                    });
        }

        @Override
        public NoticeKind read(WireReader in) {
            int code = in.readUnsignedByte();
            return switch (code) {
                case 0 -> NoticeKind.ORDER_FAILED;
                case 1 -> NoticeKind.STUCK;
                case 2 -> NoticeKind.UNIT_LOST;
                default -> throw new WireException("unknown notice kind " + code);
            };
        }

        @Override
        public int maxSize() {
            return 1;
        }
    };

    private WireCodecs() {}
}
