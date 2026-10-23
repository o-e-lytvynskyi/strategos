package dev.strategos.mod.server.world;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.strategos.core.protocol.FxKind;
import dev.strategos.core.sim.world.WritePriority;
import dev.strategos.core.sim.world.WriteScheduler;
import dev.strategos.core.sim.world.WriteSite;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;

/**
 * The only writer of blocks (rule B7, ADR-0021, BLD-15): every block Strategos places or removes goes through here.
 * Writes wait in one {@link WriteScheduler} per level and leave in its order:
 *
 * <ul>
 *   <li>one budget per server tick, shared by every level where a match runs ({@code writerBudget}, 64); a level
 *       where none runs writes under {@code importBudget} instead, outside the shared budget (MAP-01);
 *   <li>sites take turns, each writing in a burst at most every 4 ticks; inside a site, priorities, then arrival.
 * </ul>
 *
 * Each write uses {@code UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE} (Block.java:78, 81), so neighbours do not cascade, with
 * {@link OwnWrite} set, and keeps the {@link ProtectedBlockIndex} in step. A site whose queue empties gets one
 * shape-update pass over the outside of what it wrote, except for {@code IMPORT} writes: a map's tiles hold their final
 * states, and a pass at a tile's edge would change them (leaves recount their distance on the tick it schedules).
 * Server thread only.
 */
public final class BlockWriter {
    public static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private final int budget;
    private final int importBudget;
    private final Map<ResourceKey<Level>, LevelWrites> levels = new HashMap<>();
    private final Map<String, BlockState> states = new HashMap<>();
    private final OwnWrite ownWrite = new OwnWrite();
    private int left;
    private long placements;
    private long nanos;
    private @Nullable Observer observer;

    /** Sees every write right after its {@code setBlock}, inside the same {@link OwnWrite}; for GameTests. */
    @FunctionalInterface
    public interface Observer {
        void wrote(ServerLevel level, long tick, WriteSite site, BlockPos pos, BlockState state);
    }

    /** A write waiting: where, what, who protects it (0 for nobody), whether the shape pass covers it, its priority. */
    private record Pending(BlockPos pos, BlockState state, int owner, boolean shaped, WritePriority priority) {}

    /**
     * A write the match's viewers see and hear (AUD-04): a block broken (a node's removal, a building's collapse) or
     * placed (construction), with the state that went or came, by its id.
     */
    public record Written(BlockPos pos, FxKind kind, int stateId) {}

    /** One level's queue, protected blocks, and what each site wrote since its last shape pass. */
    private static final class LevelWrites {
        final WriteScheduler<Pending> scheduler = new WriteScheduler<>();
        final ProtectedBlockIndex protectedBlocks = new ProtectedBlockIndex();
        final Map<WriteSite, LongOpenHashSet> written = new HashMap<>();
        final Map<Integer, List<Written>> fx = new TreeMap<>();
    }

    public BlockWriter(int budget, int importBudget) {
        this.budget = budget;
        this.importBudget = importBudget;
        this.left = budget;
    }

    public void enqueue(
            ServerLevel level, WriteSite site, WritePriority priority, BlockPos pos, BlockState state, int owner) {
        writesOf(level)
                .scheduler
                .enqueue(
                        site,
                        priority,
                        new Pending(pos.immutable(), state, owner, priority != WritePriority.IMPORT, priority));
    }

    /** A block state written as Minecraft prints it, such as {@code minecraft:oak_stairs[facing=east]}; cached. */
    public BlockState state(String text) {
        BlockState cached = states.get(text);
        if (cached != null) {
            return cached;
        }
        try {
            BlockState parsed = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), text, false)
                    .blockState();
            states.put(text, parsed);
            return parsed;
        } catch (CommandSyntaxException e) {
            throw new IllegalArgumentException("not a block state: " + text + " (" + e.getMessage() + ")", e);
        }
    }

    /**
     * {@code current} with the properties {@code text} names, {@code [age=2]} or {@code [age=2,facing=east]} (a
     * core's property-only effect, ECO-05); null when the block has no such property or value, or the text is malformed.
     */
    public static @Nullable BlockState withProperties(BlockState current, String text) {
        if (text.length() < 2 || text.charAt(0) != '[' || text.charAt(text.length() - 1) != ']') {
            return null;
        }
        BlockState out = current;
        for (String pair : text.substring(1, text.length() - 1).split(",", -1)) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                return null;
            }
            Property<?> property = current.getBlock()
                    .getStateDefinition()
                    .getProperty(pair.substring(0, eq).trim());
            if (property == null) {
                return null;
            }
            BlockState next = with(out, property, pair.substring(eq + 1).trim());
            if (next == null) {
                return null;
            }
            out = next;
        }
        return out;
    }

    private static <T extends Comparable<T>> @Nullable BlockState with(
            BlockState state, Property<T> property, String value) {
        return property.getValue(value).map(v -> state.setValue(property, v)).orElse(null);
    }

    /** At the start of each server tick: the shared budget refills. */
    public void startTick() {
        left = budget;
    }

    /**
     * Writes {@code level}'s share of tick {@code tick}: under the shared budget when a match there runs, under the
     * import budget otherwise.
     */
    public void flush(ServerLevel level, long tick, boolean matchRunning) {
        LevelWrites writes = levels.get(level.dimension());
        if (writes == null || writes.scheduler.pending() == 0) {
            return;
        }
        int allowed = matchRunning ? left : importBudget;
        int[] count = new int[1];
        List<WriteSite> emptied = writes.scheduler.drain(tick, allowed, (site, w) -> {
            write(level, writes, tick, site, w);
            count[0]++;
        });
        if (matchRunning) {
            left -= count[0];
        }
        for (WriteSite site : emptied) {
            shapePass(level, writes.written.remove(site));
        }
    }

    private void write(ServerLevel level, LevelWrites writes, long tick, WriteSite site, Pending w) {
        Observer o = observer;
        long start = System.nanoTime();
        if (site.match() != 0) {
            Written seen = seen(level.getBlockState(w.pos()), w);
            if (seen != null) {
                writes.fx.computeIfAbsent(site.match(), k -> new ArrayList<>()).add(seen);
            }
        }
        ownWrite.around(() -> {
            level.setBlock(w.pos(), w.state(), FLAGS);
            if (o != null) {
                o.wrote(level, tick, site, w.pos(), w.state());
            }
        });
        nanos += System.nanoTime() - start;
        placements++;
        if (w.owner() != 0 && !w.state().isAir() && site.match() != 0) {
            writes.protectedBlocks.protect(w.pos(), site.match(), w.owner());
        } else {
            writes.protectedBlocks.release(w.pos());
        }
        if (w.shaped()) {
            writes.written
                    .computeIfAbsent(site, k -> new LongOpenHashSet())
                    .add(w.pos().asLong());
        }
    }

    /**
     * After a site's burst emptied its queue: the blocks it wrote that touch something it did not write update their
     * own shape, and their neighbours'. Inside the site the written states already connect.
     */
    private void shapePass(ServerLevel level, @Nullable LongOpenHashSet written) {
        if (written == null) {
            return;
        }
        BlockPos.MutableBlockPos neighbour = new BlockPos.MutableBlockPos();
        ownWrite.around(() -> written.forEach((long packed) -> {
            BlockPos pos = BlockPos.of(packed);
            boolean edge = false;
            for (Direction d : Direction.values()) {
                if (!written.contains(neighbour.setWithOffset(pos, d).asLong())) {
                    edge = true;
                    break;
                }
            }
            if (!edge) {
                return;
            }
            BlockState state = level.getBlockState(pos);
            BlockState shaped = Block.updateFromNeighbourShapes(state, level, pos);
            if (!shaped.equals(state)) {
                level.setBlock(pos, shaped, Block.UPDATE_CLIENTS);
            }
            shaped.updateNeighbourShapes(level, pos, Block.UPDATE_CLIENTS);
        }));
    }

    /** What a write shows: a removal by a node or a collapse breaks the block there, construction places one. */
    private static @Nullable Written seen(BlockState before, Pending w) {
        boolean removes = w.state().isAir() && !before.isAir();
        if (removes && (w.priority() == WritePriority.NODE_REMOVAL || w.priority() == WritePriority.DAMAGE_STAGE)) {
            return new Written(w.pos(), FxKind.BLOCK_BREAK, Block.getId(before));
        }
        if (!w.state().isAir() && w.priority() == WritePriority.CONSTRUCTION) {
            return new Written(w.pos(), FxKind.BLOCK_PLACE, Block.getId(w.state()));
        }
        return null;
    }

    /** The writes of {@code level} seen since the last call, by match id, ascending; the record is cleared. */
    public Map<Integer, List<Written>> takeWritten(ServerLevel level) {
        LevelWrites writes = levels.get(level.dimension());
        if (writes == null || writes.fx.isEmpty()) {
            return Map.of();
        }
        Map<Integer, List<Written>> out = new TreeMap<>(writes.fx);
        writes.fx.clear();
        return out;
    }

    /** A disposed match: its waiting writes are dropped and its blocks are no longer protected. */
    public void forget(ServerLevel level, int match) {
        LevelWrites writes = levels.get(level.dimension());
        if (writes == null) {
            return;
        }
        writes.scheduler.drop(match);
        writes.protectedBlocks.releaseMatch(match);
        writes.written.keySet().removeIf(site -> site.match() == match);
        writes.fx.remove(match);
    }

    public OwnWrite ownWrite() {
        return ownWrite;
    }

    public ProtectedBlockIndex protectedBlocks(ServerLevel level) {
        return writesOf(level).protectedBlocks;
    }

    /**
     * The states match {@code match}'s waiting writes will leave in the box from {@code min} to {@code max}
     * (inclusive), the last write of a position winning: what a page capture lays over the blocks it copies.
     */
    public Map<BlockPos, BlockState> pendingIn(ServerLevel level, int match, BlockPos min, BlockPos max) {
        Map<BlockPos, BlockState> out = new HashMap<>();
        LevelWrites writes = levels.get(level.dimension());
        if (writes == null || writes.scheduler.pending() == 0) {
            return out;
        }
        writes.scheduler.forEachPending(match, w -> {
            BlockPos p = w.pos();
            if (p.getX() >= min.getX()
                    && p.getX() <= max.getX()
                    && p.getY() >= min.getY()
                    && p.getY() <= max.getY()
                    && p.getZ() >= min.getZ()
                    && p.getZ() <= max.getZ()) {
                out.put(p, w.state());
            }
        });
        return out;
    }

    /** Writes waiting at one site of {@code level}. */
    public int pending(ServerLevel level, WriteSite site) {
        LevelWrites writes = levels.get(level.dimension());
        return writes == null ? 0 : writes.scheduler.pending(site);
    }

    /** Writes waiting in {@code level}. */
    public int pending(ServerLevel level) {
        LevelWrites writes = levels.get(level.dimension());
        return writes == null ? 0 : writes.scheduler.pending();
    }

    /** Blocks written since the server started, and the time their {@code setBlock} calls took. */
    public long placements() {
        return placements;
    }

    public long writeNanos() {
        return nanos;
    }

    public void observe(@Nullable Observer observer) {
        this.observer = observer;
    }

    private LevelWrites writesOf(ServerLevel level) {
        return levels.computeIfAbsent(level.dimension(), k -> new LevelWrites());
    }
}
