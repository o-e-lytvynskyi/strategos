package dev.strategos.mod.server.world;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.shorts.Short2LongMap;
import it.unimi.dsi.fastutil.shorts.Short2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

/**
 * Which blocks of a level belong to a building or a resource node (04 §11.9): positions mapped to the owner, a match
 * and a handle in it, per chunk section. The block writer fills it as it writes and empties a match's share when the
 * match is disposed; the protection handlers (M4) and the block-change backstop read it. Server thread only.
 */
public final class ProtectedBlockIndex {
    private final Long2ObjectOpenHashMap<Short2LongOpenHashMap> sections = new Long2ObjectOpenHashMap<>();
    private int size;

    /** {@code pos} now belongs to {@code owner}, a positive handle in match {@code match}. */
    public void protect(BlockPos pos, int match, int owner) {
        if (owner <= 0 || match <= 0) {
            throw new IllegalArgumentException("owner " + owner + " of match " + match + " is not a handle");
        }
        Short2LongOpenHashMap section = sections.computeIfAbsent(sectionKey(pos), k -> new Short2LongOpenHashMap());
        if (section.put(local(pos), (long) match << 32 | owner) == 0) {
            size++;
        }
    }

    public void release(BlockPos pos) {
        long key = sectionKey(pos);
        Short2LongOpenHashMap section = sections.get(key);
        if (section != null && section.remove(local(pos)) != 0) {
            size--;
            if (section.isEmpty()) {
                sections.remove(key);
            }
        }
    }

    /** Releases every block of {@code match}. */
    public void releaseMatch(int match) {
        ObjectIterator<Short2LongOpenHashMap> it = sections.values().iterator();
        while (it.hasNext()) {
            Short2LongOpenHashMap section = it.next();
            ObjectIterator<Short2LongMap.Entry> entries =
                    section.short2LongEntrySet().iterator();
            while (entries.hasNext()) {
                if ((int) (entries.next().getLongValue() >>> 32) == match) {
                    entries.remove();
                    size--;
                }
            }
            if (section.isEmpty()) {
                it.remove();
            }
        }
    }

    /** The owner's handle, or 0 when nothing protects {@code pos}. */
    public int owner(BlockPos pos) {
        return (int) packed(pos);
    }

    /** The owner's match, or 0 when nothing protects {@code pos}. */
    public int matchOf(BlockPos pos) {
        return (int) (packed(pos) >>> 32);
    }

    public int size() {
        return size;
    }

    /** How many positions the one section a lookup of {@code pos} consults holds (at most 4,096), or 0 without one. */
    int sectionSize(BlockPos pos) {
        Short2LongOpenHashMap section = sections.get(sectionKey(pos));
        return section == null ? 0 : section.size();
    }

    private long packed(BlockPos pos) {
        Short2LongOpenHashMap section = sections.get(sectionKey(pos));
        return section == null ? 0 : section.get(local(pos));
    }

    private static long sectionKey(BlockPos pos) {
        return SectionPos.asLong(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
    }

    private static short local(BlockPos pos) {
        return (short) ((pos.getY() & 15) << 8 | (pos.getZ() & 15) << 4 | (pos.getX() & 15));
    }
}
