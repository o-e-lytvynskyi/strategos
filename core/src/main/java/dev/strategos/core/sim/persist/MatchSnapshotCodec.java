package dev.strategos.core.sim.persist;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Hex64;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.EntitySlots;
import dev.strategos.core.sim.MatchConfig;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.UnitRecord;
import dev.strategos.core.sim.UnitState;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * A match snapshot as data. Seeds and hashes travel as {@code Hex64} strings, ticks as longs, slot arrays as int streams
 * (an {@code IntArrayTag} in NBT), templates by string id. {@link #encode} adds {@code "v"}; {@link #decode} migrates
 * first.
 */
@CoreApi
public final class MatchSnapshotCodec {
    private static final Codec<int[]> INTS = Codec.INT_STREAM.xmap(IntStream::toArray, Arrays::stream);

    private static final Codec<EntitySlots> SLOTS = RecordCodecBuilder.create(i -> i.group(
                    INTS.fieldOf("generations").forGetter(EntitySlots::generations),
                    INTS.fieldOf("free").forGetter(EntitySlots::freeIndices))
            .apply(i, EntitySlots::new));

    private static final Codec<UnitRecord> UNIT = RecordCodecBuilder.create(i -> i.group(
                    Codec.INT.fieldOf("handle").forGetter(UnitRecord::handle),
                    TemplateId.CODEC.fieldOf("template").forGetter(UnitRecord::template),
                    FactionSlot.CODEC.fieldOf("faction").forGetter(UnitRecord::faction),
                    Codec.DOUBLE.fieldOf("x").forGetter(UnitRecord::x),
                    Codec.DOUBLE.fieldOf("y").forGetter(UnitRecord::y),
                    Codec.DOUBLE.fieldOf("z").forGetter(UnitRecord::z),
                    Codec.FLOAT.fieldOf("yaw").forGetter(UnitRecord::yaw),
                    UnitState.CODEC.fieldOf("state").forGetter(UnitRecord::state),
                    Vec2d.CODEC.optionalFieldOf("target").forGetter(u -> Optional.ofNullable(u.target())))
            .apply(
                    i,
                    (handle, template, faction, x, y, z, yaw, state, target) ->
                            new UnitRecord(handle, template, faction, x, y, z, yaw, state, target.orElse(null))));

    /** The fields, without {@code "v"}. */
    public static final Codec<MatchSnapshot> CODEC = RecordCodecBuilder.create(i -> i.group(
                    MatchConfig.CODEC.fieldOf("config").forGetter(MatchSnapshot::config),
                    Codec.LONG.fieldOf("tick").forGetter(MatchSnapshot::tick),
                    Hex64.CODEC.fieldOf("templateSetHash").forGetter(MatchSnapshot::templateSetHash),
                    SLOTS.fieldOf("slots").forGetter(MatchSnapshot::slots),
                    UNIT.listOf().fieldOf("units").forGetter(MatchSnapshot::units),
                    CommandEnvelope.CODEC.listOf().fieldOf("pendingCommands").forGetter(MatchSnapshot::pendingCommands))
            .apply(i, MatchSnapshot::new));

    private MatchSnapshotCodec() {}

    /** The snapshot with {@code "v": CURRENT}; an encoding error is a bug and throws. */
    public static <T> T encode(DynamicOps<T> ops, MatchSnapshot snapshot) {
        T encoded = CODEC.encodeStart(ops, snapshot)
                .getOrThrow(message -> new IllegalStateException("cannot encode a snapshot: " + message));
        return new Dynamic<>(ops, encoded)
                .set("v", new Dynamic<>(ops, ops.createInt(SchemaVersion.CURRENT)))
                .getValue();
    }

    /** Migrates to the current version, then decodes; any problem is an error, never an exception. */
    public static <T> DataResult<MatchSnapshot> decode(Dynamic<T> raw, Migrations migrations) {
        return migrations.migrate(raw, SchemaVersion.CURRENT).flatMap(CODEC::parse);
    }
}
