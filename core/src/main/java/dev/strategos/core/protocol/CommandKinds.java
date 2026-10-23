package dev.strategos.core.protocol;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** An immutable catalogue of command kinds, with the codec that dispatches on the field {@code kind}. */
public final class CommandKinds {
    public static final CommandKind<Move> MOVE =
            new CommandKind<>("strategos:move", 1, Move.class, Move.CODEC, Move.WIRE, false);
    public static final CommandKind<Stop> STOP =
            new CommandKind<>("strategos:stop", 2, Stop.class, Stop.CODEC, Stop.WIRE, false);
    public static final CommandKind<SpawnUnits> SPAWN_UNITS =
            new CommandKind<>("strategos:spawn_units", 3, SpawnUnits.class, SpawnUnits.CODEC, SpawnUnits.WIRE, true);
    public static final CommandKind<Attack> ATTACK =
            new CommandKind<>("strategos:attack", 4, Attack.class, Attack.CODEC, Attack.WIRE, false);
    public static final CommandKind<AttackMove> ATTACK_MOVE =
            new CommandKind<>("strategos:attack_move", 5, AttackMove.class, AttackMove.CODEC, AttackMove.WIRE, false);
    public static final CommandKind<Follow> FOLLOW =
            new CommandKind<>("strategos:follow", 6, Follow.class, Follow.CODEC, Follow.WIRE, false);
    public static final CommandKinds DEFAULT =
            new CommandKinds(List.of(MOVE, STOP, SPAWN_UNITS, ATTACK, ATTACK_MOVE, FOLLOW));

    private final List<CommandKind<?>> kinds;
    private final Codec<Command> codec;
    private final WireCodec<Command> wire;

    public CommandKinds(List<CommandKind<?>> kinds) {
        for (int i = 0; i < kinds.size(); i++) {
            for (int j = 0; j < i; j++) {
                if (kinds.get(i).id().equals(kinds.get(j).id())) {
                    throw new IllegalArgumentException(
                            "command kind '" + kinds.get(i).id() + "' listed twice");
                }
                if (kinds.get(i).wireId() == kinds.get(j).wireId()) {
                    throw new IllegalArgumentException("wire id " + kinds.get(i).wireId() + " listed twice");
                }
                if (kinds.get(i).type() == kinds.get(j).type()) {
                    throw new IllegalArgumentException(
                            kinds.get(i).type().getSimpleName() + " has two kinds in one catalogue");
                }
            }
        }
        this.kinds = List.copyOf(kinds);
        Codec<CommandKind<?>> kindCodec = Codec.STRING.comapFlatMap(this::read, CommandKind::id);
        this.codec = kindCodec.dispatch("kind", this::of, CommandKind::codec);
        this.wire = new KindWire();
    }

    public List<CommandKind<?>> all() {
        return kinds;
    }

    public @Nullable CommandKind<?> byId(String id) {
        for (CommandKind<?> kind : kinds) {
            if (kind.id().equals(id)) {
                return kind;
            }
        }
        return null;
    }

    /** The kind of {@code command}'s record class; throws on a type this catalogue does not list. */
    public CommandKind<?> of(Command command) {
        for (CommandKind<?> kind : kinds) {
            if (kind.type() == command.getClass()) {
                return kind;
            }
        }
        throw new IllegalArgumentException(
                "no command kind for " + command.getClass().getName());
    }

    public @Nullable CommandKind<?> byWireId(int wireId) {
        for (CommandKind<?> kind : kinds) {
            if (kind.wireId() == wireId) {
                return kind;
            }
        }
        return null;
    }

    /** Every command as its wire id (two bytes) and its kind's wire codec; an unknown id does not decode. */
    public WireCodec<Command> wireCodec() {
        return wire;
    }

    /** {@code id=wireId} of every kind, in catalogue order: a journal written by another catalogue is refused. */
    public String fingerprint() {
        StringBuilder sb = new StringBuilder();
        for (CommandKind<?> kind : kinds) {
            if (!sb.isEmpty()) {
                sb.append(',');
            }
            sb.append(kind.id()).append('=').append(kind.wireId());
        }
        return sb.toString();
    }

    /**
     * Whether a journal written with catalogue {@code fingerprint} reads in this one: each of its kinds has the same
     * wire id here. A later build that only adds kinds reads every older journal (M2's attacks after M1's moves).
     */
    public boolean reads(String fingerprint) {
        if (fingerprint.isEmpty()) {
            return false;
        }
        List<String> here = List.of(fingerprint().split(",", -1));
        for (String pair : fingerprint.split(",", -1)) {
            if (!here.contains(pair)) {
                return false;
            }
        }
        return true;
    }

    /** Every command as a map with {@code "kind": "<id>"}; an unknown id fails naming it. */
    public Codec<Command> codec() {
        return codec;
    }

    private DataResult<CommandKind<?>> read(String id) {
        CommandKind<?> kind = byId(id);
        return kind != null ? DataResult.success(kind) : DataResult.error(() -> "unknown command kind '" + id + "'");
    }

    private final class KindWire implements WireCodec<Command> {
        private final int maxSize;

        KindWire() {
            int max = 0;
            for (CommandKind<?> kind : kinds) {
                max = Math.max(max, kind.wire().maxSize());
            }
            this.maxSize = 2 + max;
        }

        @Override
        public void write(WireWriter out, Command value) {
            CommandKind<?> kind = of(value);
            out.writeShort(kind.wireId());
            writeAs(kind, out, value);
        }

        @Override
        public Command read(WireReader in) {
            int id = in.readUnsignedShort();
            CommandKind<?> kind = byWireId(id);
            if (kind == null) {
                throw new WireException("unknown command wire id " + id);
            }
            return kind.wire().read(in);
        }

        @Override
        public int maxSize() {
            return maxSize;
        }

        private static <C extends Command> void writeAs(CommandKind<C> kind, WireWriter out, Command value) {
            kind.wire().write(out, kind.type().cast(value));
        }
    }
}
