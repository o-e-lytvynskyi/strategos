package dev.strategos.core.protocol;

import dev.strategos.core.shared.CoreApi;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@code ui_query} payload, client to server (NET-08): the own units whose details this player wants, at most
 * {@value #MAX_HANDLES}; each query replaces the last, and an empty one ends the subscription. At most four a second.
 */
@CoreApi
public record UiQuery(List<Integer> handles) {
    public static final int MAX_HANDLES = 32;

    public UiQuery {
        if (handles.size() > MAX_HANDLES) {
            throw new IllegalArgumentException(handles.size() + " handles, more than " + MAX_HANDLES);
        }
        handles = List.copyOf(handles);
    }

    public static final WireCodec<UiQuery> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, UiQuery q) {
            out.writeVarInt(q.handles().size());
            for (int h : q.handles()) {
                out.writeInt(h);
            }
        }

        @Override
        public UiQuery read(WireReader in) {
            int count = in.readVarInt(MAX_HANDLES);
            List<Integer> handles = new ArrayList<>(count);
            for (int k = 0; k < count; k++) {
                handles.add(in.readInt());
            }
            return new UiQuery(handles);
        }

        @Override
        public int maxSize() {
            return 5 + MAX_HANDLES * 4;
        }
    };
}
