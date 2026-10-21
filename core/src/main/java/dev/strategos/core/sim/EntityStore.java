package dev.strategos.core.sim;

import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.HotPath;
import it.unimi.dsi.fastutil.ints.IntRBTreeSet;
import java.util.Arrays;

/**
 * Dense entity handles: a 20-bit index and a 12-bit generation ({@link Handle}). A destroyed index gets a new
 * generation, so a stale handle is never alive again; freed indices are reused lowest first, so the store stays dense
 * and every run allocates the same handles.
 */
@HotPath
public final class EntityStore {
    private static final int INITIAL_CAPACITY = 64;

    /** Per index: the generation of its live entity, or the one its next entity gets. */
    private int[] generations;

    private boolean[] alive;
    private final IntRBTreeSet free = new IntRBTreeSet();
    private int highWater;
    private int aliveCount;

    public EntityStore() {
        generations = new int[INITIAL_CAPACITY];
        alive = new boolean[INITIAL_CAPACITY];
    }

    /** A new entity at the lowest free index, else at the high-water mark. */
    public int create() {
        int index;
        if (free.isEmpty()) {
            if (highWater > Handle.MAX_INDEX) {
                throw new IllegalStateException("more than " + (Handle.MAX_INDEX + 1) + " entities");
            }
            index = highWater++;
            ensureCapacity(highWater);
        } else {
            index = free.firstInt();
            free.remove(index);
        }
        int generation = generations[index] == 0 ? Handle.nextGeneration(0) : generations[index];
        generations[index] = generation;
        alive[index] = true;
        aliveCount++;
        return Handle.of(index, generation);
    }

    /** Frees the handle's index with a new generation; a dead or stale handle throws. */
    public void destroy(int handle) {
        if (!isAlive(handle)) {
            throw new IllegalArgumentException("destroying " + Handle.toString(handle) + ", which is not alive");
        }
        int index = Handle.index(handle);
        alive[index] = false;
        generations[index] = Handle.nextGeneration(generations[index]);
        free.add(index);
        aliveCount--;
    }

    public boolean isAlive(int handle) {
        int index = Handle.index(handle);
        return handle != Handle.NONE
                && index < highWater
                && alive[index]
                && generations[index] == Handle.generation(handle);
    }

    public int aliveCount() {
        return aliveCount;
    }

    /** One past the highest index ever issued. */
    public int highWater() {
        return highWater;
    }

    public boolean aliveAt(int index) {
        return index < highWater && alive[index];
    }

    /** The live handle at {@code index}, or {@link Handle#NONE}. */
    public int handleAt(int index) {
        return aliveAt(index) ? Handle.of(index, generations[index]) : Handle.NONE;
    }

    public EntitySlots slots() {
        int[] freeIndices = new int[free.size()];
        int i = 0;
        for (var it = free.iterator(); it.hasNext(); ) {
            freeIndices[i++] = it.nextInt();
        }
        return new EntitySlots(Arrays.copyOf(generations, highWater), freeIndices);
    }

    /**
     * Rebuilds a store: every index below the high-water mark is either free or holds exactly one of
     * {@code aliveHandles}, at its slot's generation; anything else throws.
     */
    public static EntityStore restore(EntitySlots slots, int[] aliveHandles) {
        int[] generations = slots.generations();
        int[] freeIndices = slots.freeIndices();
        EntityStore store = new EntityStore();
        store.highWater = generations.length;
        store.ensureCapacity(generations.length);
        System.arraycopy(generations, 0, store.generations, 0, generations.length);
        for (int index : freeIndices) {
            if (index < 0 || index >= generations.length) {
                throw new IllegalArgumentException("free index " + index + " outside 0.." + (generations.length - 1));
            }
            store.free.add(index);
        }
        for (int handle : aliveHandles) {
            int index = Handle.index(handle);
            if (index >= generations.length
                    || store.free.contains(index)
                    || store.alive[index]
                    || generations[index] != Handle.generation(handle)) {
                throw new IllegalArgumentException(
                        "live handle " + Handle.toString(handle) + " does not fit the slots");
            }
            store.alive[index] = true;
            store.aliveCount++;
        }
        if (store.aliveCount + store.free.size() != generations.length) {
            throw new IllegalArgumentException("slots hold " + generations.length + " indices but " + store.aliveCount
                    + " live and " + store.free.size() + " free");
        }
        return store;
    }

    private void ensureCapacity(int size) {
        if (size > generations.length) {
            int capacity = Math.max(size, generations.length * 2);
            generations = Arrays.copyOf(generations, capacity);
            alive = Arrays.copyOf(alive, capacity);
        }
    }
}
