package dev.strategos.mod.server.match;

import dev.strategos.core.shared.MatchId;
import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

/**
 * One level's matches by chunk: each chunk a match covers lists that match's id, in ascending order, so routing a
 * block to its match is one map lookup and a short scan.
 */
public final class MatchRegionIndex {
    private final Long2ObjectOpenHashMap<int[]> byChunk = new Long2ObjectOpenHashMap<>();
    private final Int2ObjectRBTreeMap<MatchSpace> spaces = new Int2ObjectRBTreeMap<>();

    public void add(MatchId id, MatchSpace space) {
        if (spaces.containsKey(id.value())) {
            throw new IllegalArgumentException("match " + id + " is already indexed");
        }
        spaces.put(id.value(), space);
        forEachChunk(space, 0, key -> byChunk.put(key, with(byChunk.get(key), id.value())));
    }

    public void remove(MatchId id) {
        MatchSpace space = spaces.remove(id.value());
        if (space == null) {
            return;
        }
        forEachChunk(space, 0, key -> {
            int[] rest = without(byChunk.get(key), id.value());
            if (rest.length == 0) {
                byChunk.remove(key);
            } else {
                byChunk.put(key, rest);
            }
        });
    }

    /** The match whose space holds {@code pos}; the lowest id when two share it. */
    public @Nullable MatchId at(BlockPos pos) {
        int[] ids = byChunk.get(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
        if (ids == null) {
            return null;
        }
        for (int id : ids) {
            if (spaces.get(id).contains(pos)) {
                return new MatchId(id);
            }
        }
        return null;
    }

    /** The lowest-id match closer than {@code margin} blocks to {@code space}, or null. */
    public @Nullable MatchId overlapping(MatchSpace space, int margin) {
        int[] best = {Integer.MAX_VALUE};
        forEachChunk(space, margin, key -> {
            int[] ids = byChunk.get(key);
            if (ids != null) {
                for (int id : ids) {
                    if (id < best[0] && spaces.get(id).overlaps(space, margin)) {
                        best[0] = id;
                    }
                }
            }
        });
        return best[0] == Integer.MAX_VALUE ? null : new MatchId(best[0]);
    }

    /** Every chunk of {@code space} grown by {@code margin} blocks on each side. */
    private static void forEachChunk(MatchSpace space, int margin, ChunkKeyConsumer action) {
        int minX = (space.origin().getX() - margin) >> 4;
        int minZ = (space.origin().getZ() - margin) >> 4;
        int maxX = (space.origin().getX() + space.sizeX() - 1 + margin) >> 4;
        int maxZ = (space.origin().getZ() + space.sizeZ() - 1 + margin) >> 4;
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                action.accept(ChunkPos.asLong(cx, cz));
            }
        }
    }

    private static int[] with(int @Nullable [] ids, int id) {
        if (ids == null) {
            return new int[] {id};
        }
        int[] grown = Arrays.copyOf(ids, ids.length + 1);
        grown[ids.length] = id;
        Arrays.sort(grown);
        return grown;
    }

    private static int[] without(int @Nullable [] ids, int id) {
        if (ids == null) {
            return new int[0];
        }
        return Arrays.stream(ids).filter(i -> i != id).toArray();
    }

    @FunctionalInterface
    private interface ChunkKeyConsumer {
        void accept(long chunkKey);
    }
}
