package dev.strategos.core.protocol;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** An immutable catalogue of command kinds, with the codec that dispatches on the field {@code kind}. */
public final class CommandKinds {
    public static final CommandKind<Move> MOVE = new CommandKind<>("strategos:move", Move.class, Move.CODEC, false);
    public static final CommandKind<Stop> STOP = new CommandKind<>("strategos:stop", Stop.class, Stop.CODEC, false);
    public static final CommandKind<SpawnUnits> SPAWN_UNITS =
            new CommandKind<>("strategos:spawn_units", SpawnUnits.class, SpawnUnits.CODEC, true);
    public static final CommandKinds DEFAULT = new CommandKinds(List.of(MOVE, STOP, SPAWN_UNITS));

    private final List<CommandKind<?>> kinds;
    private final Codec<Command> codec;

    public CommandKinds(List<CommandKind<?>> kinds) {
        for (int i = 0; i < kinds.size(); i++) {
            for (int j = 0; j < i; j++) {
                if (kinds.get(i).id().equals(kinds.get(j).id())) {
                    throw new IllegalArgumentException(
                            "command kind '" + kinds.get(i).id() + "' listed twice");
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

    /** Every command as a map with {@code "kind": "<id>"}; an unknown id fails naming it. */
    public Codec<Command> codec() {
        return codec;
    }

    private DataResult<CommandKind<?>> read(String id) {
        CommandKind<?> kind = byId(id);
        return kind != null ? DataResult.success(kind) : DataResult.error(() -> "unknown command kind '" + id + "'");
    }
}
