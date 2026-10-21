package dev.strategos.core.sim.journal;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.Hex64;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.InputEvent;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.TickInputs;
import dev.strategos.core.sim.persist.MatchSnapshotCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * A journal export as data: {@code {"v":1, start, checkpointEvery, lastTick, ticks:[{tick, events}],
 * checkpoints:[[tick, hex]], liveTick, liveHash}}. The nested start snapshot has no {@code "v"} of its own.
 */
@CoreApi
public final class JournalCodec {
    private static final int VERSION = 1;

    /** M0's only input event is a command envelope. */
    private static final Codec<InputEvent> EVENT = CommandEnvelope.CODEC.xmap(e -> e, JournalCodec::envelope);

    private static final Codec<TickInputs> TICK = RecordCodecBuilder.create(i -> i.group(
                    Codec.LONG.fieldOf("tick").forGetter(TickInputs::tick),
                    EVENT.listOf().fieldOf("events").forGetter(TickInputs::events))
            .apply(i, TickInputs::new));

    /** A checkpoint as {@code [tick, "hex"]}. */
    private static final Codec<Checkpoint> CHECKPOINT = new CheckpointCodec();

    public static final Codec<JournalExport> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Codec.intRange(VERSION, VERSION).fieldOf("v").forGetter(e -> VERSION), // another version fails
                    MatchSnapshotCodec.CODEC
                            .fieldOf("start")
                            .forGetter(e -> e.journal().start()),
                    Codec.INT
                            .fieldOf("checkpointEvery")
                            .forGetter(e -> e.journal().checkpointEvery()),
                    Codec.LONG.fieldOf("lastTick").forGetter(e -> e.journal().lastTick()),
                    TICK.listOf().fieldOf("ticks").forGetter(e -> e.journal().nonEmptyTicks()),
                    CHECKPOINT.listOf().fieldOf("checkpoints").forGetter(e -> checkpoints(e.journal())),
                    Codec.LONG.fieldOf("liveTick").forGetter(JournalExport::liveTick),
                    Hex64.CODEC.fieldOf("liveHash").forGetter(JournalExport::liveHash))
            .apply(i, JournalCodec::export));

    private JournalCodec() {}

    private static JournalExport export(
            int version,
            MatchSnapshot start,
            int checkpointEvery,
            long lastTick,
            List<TickInputs> ticks,
            List<Checkpoint> checkpoints,
            long liveTick,
            long liveHash) {
        long[] cpTicks = new long[checkpoints.size()];
        long[] cpHashes = new long[checkpoints.size()];
        for (int i = 0; i < cpTicks.length; i++) {
            cpTicks[i] = checkpoints.get(i).tick();
            cpHashes[i] = checkpoints.get(i).hash();
        }
        return new JournalExport(
                new Journal(start, lastTick, ticks, checkpointEvery, cpTicks, cpHashes), liveTick, liveHash);
    }

    private static List<Checkpoint> checkpoints(Journal journal) {
        long[] ticks = journal.checkpointTicks();
        long[] hashes = journal.checkpointHashes();
        List<Checkpoint> list = new ArrayList<>(ticks.length);
        for (int i = 0; i < ticks.length; i++) {
            list.add(new Checkpoint(ticks[i], hashes[i]));
        }
        return list;
    }

    private static CommandEnvelope envelope(InputEvent event) {
        return switch (event) {
            case CommandEnvelope envelope -> envelope;
        };
    }

    private record Checkpoint(long tick, long hash) {}

    /** {@code [tick, "hex"]}: a number and a {@code Hex64} string in a two-element list. */
    private static final class CheckpointCodec implements Codec<Checkpoint> {
        @Override
        public <T> DataResult<T> encode(Checkpoint input, DynamicOps<T> ops, T prefix) {
            return ops.mergeToPrimitive(
                    prefix,
                    ops.createList(
                            Stream.of(ops.createLong(input.tick()), ops.createString(Hex64.format(input.hash())))));
        }

        @Override
        public <T> DataResult<Pair<Checkpoint, T>> decode(DynamicOps<T> ops, T input) {
            return ops.getStream(input).flatMap(stream -> {
                List<T> parts = stream.toList();
                if (parts.size() != 2) {
                    return DataResult.error(() -> "a checkpoint is [tick, hash], got " + parts.size() + " elements");
                }
                return ops.getNumberValue(parts.get(0))
                        .flatMap(tick -> Hex64.CODEC
                                .parse(ops, parts.get(1))
                                .map(hash -> Pair.of(new Checkpoint(tick.longValue(), hash), input)));
            });
        }
    }
}
