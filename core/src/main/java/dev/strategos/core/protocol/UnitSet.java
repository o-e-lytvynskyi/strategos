package dev.strategos.core.protocol;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import dev.strategos.core.shared.Hashable;
import dev.strategos.core.shared.Hasher;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

/** An immutable sorted set of distinct unit handles, at most {@value #MAX_SIZE} (0 A.D.'s selection limit). */
public final class UnitSet implements Hashable {
    public static final int MAX_SIZE = 300;

    /** A list of handles; the cap holds on decode too. */
    public static final Codec<UnitSet> CODEC = Codec.INT.listOf().comapFlatMap(UnitSet::read, UnitSet::toList);

    private final int[] handles;

    private UnitSet(int[] handles) {
        this.handles = handles;
    }

    /** Sorts and drops duplicates; more than {@value #MAX_SIZE} distinct handles throw. */
    public static UnitSet of(int... handles) {
        int[] distinct = Arrays.stream(handles).sorted().distinct().toArray();
        if (distinct.length > MAX_SIZE) {
            throw new IllegalArgumentException(distinct.length + " units, more than " + MAX_SIZE);
        }
        return new UnitSet(distinct);
    }

    public int size() {
        return handles.length;
    }

    public int get(int i) {
        return handles[i];
    }

    public boolean contains(int handle) {
        return Arrays.binarySearch(handles, handle) >= 0;
    }

    /** A copy, sorted. */
    public int[] toArray() {
        return handles.clone();
    }

    @Override
    public void hashInto(Hasher h) {
        h.putInt(handles.length);
        for (int handle : handles) {
            h.putInt(handle);
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof UnitSet other && Arrays.equals(handles, other.handles);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(handles);
    }

    @Override
    public String toString() {
        return "UnitSet" + Arrays.toString(handles);
    }

    private static DataResult<UnitSet> read(List<Integer> list) {
        if (list.size() > MAX_SIZE) {
            return DataResult.error(() -> list.size() + " units, more than " + MAX_SIZE);
        }
        return DataResult.success(of(list.stream().mapToInt(Integer::intValue).toArray()));
    }

    private List<Integer> toList() {
        return IntStream.of(handles).boxed().toList();
    }
}
