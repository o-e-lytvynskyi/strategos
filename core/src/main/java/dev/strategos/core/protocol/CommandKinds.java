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
    public static final CommandKind<Grant> GRANT =
            new CommandKind<>("strategos:grant", 7, Grant.class, Grant.CODEC, Grant.WIRE, true);
    public static final CommandKind<SpawnBuilding> SPAWN_BUILDING = new CommandKind<>(
            "strategos:spawn_building", 8, SpawnBuilding.class, SpawnBuilding.CODEC, SpawnBuilding.WIRE, true);
    public static final CommandKind<Gather> GATHER =
            new CommandKind<>("strategos:gather", 9, Gather.class, Gather.CODEC, Gather.WIRE, false);
    public static final CommandKind<PlaceBuilding> PLACE_BUILDING = new CommandKind<>(
            "strategos:place_building", 10, PlaceBuilding.class, PlaceBuilding.CODEC, PlaceBuilding.WIRE, false);
    public static final CommandKind<Train> TRAIN =
            new CommandKind<>("strategos:train", 11, Train.class, Train.CODEC, Train.WIRE, false);
    public static final CommandKind<CancelTrain> CANCEL_TRAIN = new CommandKind<>(
            "strategos:cancel_train", 12, CancelTrain.class, CancelTrain.CODEC, CancelTrain.WIRE, false);
    public static final CommandKind<SetRally> SET_RALLY =
            new CommandKind<>("strategos:set_rally", 13, SetRally.class, SetRally.CODEC, SetRally.WIRE, false);
    public static final CommandKind<Reseed> RESEED =
            new CommandKind<>("strategos:reseed", 14, Reseed.class, Reseed.CODEC, Reseed.WIRE, false);
    public static final CommandKind<Resign> RESIGN =
            new CommandKind<>("strategos:resign", 15, Resign.class, Resign.CODEC, Resign.WIRE, false);
    public static final CommandKind<RuleChange> RULE_CHANGE =
            new CommandKind<>("strategos:rule_change", 16, RuleChange.class, RuleChange.CODEC, RuleChange.WIRE, true);
    public static final CommandKind<AnnounceWave> ANNOUNCE_WAVE = new CommandKind<>(
            "strategos:announce_wave", 17, AnnounceWave.class, AnnounceWave.CODEC, AnnounceWave.WIRE, false);
    public static final CommandKind<SetAi> SET_AI =
            new CommandKind<>("strategos:set_ai", 18, SetAi.class, SetAi.CODEC, SetAi.WIRE, true);
    public static final CommandKind<Build> BUILD =
            new CommandKind<>("strategos:build", 19, Build.class, Build.CODEC, Build.WIRE, false);
    public static final CommandKind<CancelBuilding> CANCEL_BUILDING = new CommandKind<>(
            "strategos:cancel_building", 20, CancelBuilding.class, CancelBuilding.CODEC, CancelBuilding.WIRE, false);
    public static final CommandKind<Demolish> DEMOLISH =
            new CommandKind<>("strategos:demolish", 21, Demolish.class, Demolish.CODEC, Demolish.WIRE, false);
    public static final CommandKind<SetAutoReseed> SET_AUTO_RESEED = new CommandKind<>(
            "strategos:set_auto_reseed", 22, SetAutoReseed.class, SetAutoReseed.CODEC, SetAutoReseed.WIRE, false);
    public static final CommandKind<TrainBatch> TRAIN_BATCH =
            new CommandKind<>("strategos:train_batch", 23, TrainBatch.class, TrainBatch.CODEC, TrainBatch.WIRE, false);
    public static final CommandKind<SpawnDeposit> SPAWN_DEPOSIT = new CommandKind<>(
            "strategos:spawn_deposit", 24, SpawnDeposit.class, SpawnDeposit.CODEC, SpawnDeposit.WIRE, true);
    public static final CommandKind<SetStance> SET_STANCE =
            new CommandKind<>("strategos:set_stance", 25, SetStance.class, SetStance.CODEC, SetStance.WIRE, false);
    public static final CommandKind<Garrison> GARRISON =
            new CommandKind<>("strategos:garrison", 26, Garrison.class, Garrison.CODEC, Garrison.WIRE, false);
    public static final CommandKind<Ungarrison> UNGARRISON =
            new CommandKind<>("strategos:ungarrison", 27, Ungarrison.class, Ungarrison.CODEC, Ungarrison.WIRE, false);
    public static final CommandKind<RingBell> RING_BELL =
            new CommandKind<>("strategos:ring_bell", 28, RingBell.class, RingBell.CODEC, RingBell.WIRE, false);
    public static final CommandKind<AllClear> ALL_CLEAR =
            new CommandKind<>("strategos:all_clear", 29, AllClear.class, AllClear.CODEC, AllClear.WIRE, false);
    public static final CommandKind<AttackSpread> ATTACK_SPREAD = new CommandKind<>(
            "strategos:attack_spread", 30, AttackSpread.class, AttackSpread.CODEC, AttackSpread.WIRE, false);
    public static final CommandKinds DEFAULT = new CommandKinds(List.of(
            MOVE,
            STOP,
            SPAWN_UNITS,
            ATTACK,
            ATTACK_MOVE,
            FOLLOW,
            GRANT,
            SPAWN_BUILDING,
            GATHER,
            PLACE_BUILDING,
            TRAIN,
            CANCEL_TRAIN,
            SET_RALLY,
            RESEED,
            RESIGN,
            RULE_CHANGE,
            ANNOUNCE_WAVE,
            SET_AI,
            BUILD,
            CANCEL_BUILDING,
            DEMOLISH,
            SET_AUTO_RESEED,
            TRAIN_BATCH,
            SPAWN_DEPOSIT,
            SET_STANCE,
            GARRISON,
            UNGARRISON,
            RING_BELL,
            ALL_CLEAR,
            ATTACK_SPREAD));

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
