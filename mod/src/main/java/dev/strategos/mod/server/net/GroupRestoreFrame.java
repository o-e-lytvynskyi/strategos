package dev.strategos.mod.server.net;

import dev.strategos.core.protocol.PlayerAuthority;
import dev.strategos.core.protocol.UnitSummary;
import dev.strategos.core.protocol.WireCodec;
import dev.strategos.core.protocol.WireReader;
import dev.strategos.core.protocol.WireWriter;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.replicate.Visibility;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Exact current unit identities for validating client-owned groups after a full private join. */
public record GroupRestoreFrame(long epoch, int matchId, List<Integer> handles) {
    public static final int MAX_HANDLES = UnitSummary.MAX_UNITS;

    /** Same primary-vision floor as the ordinary summary; grants never extend vision. */
    public static GroupRestoreFrame capture(
            long epoch, PlayerAuthority authority, MatchQueries queries, Visibility visibility) {
        var handles = new ArrayList<Integer>();
        if (!authority.player().isAdmin()) {
            var primary = new FactionSlot(authority.primaryFaction());
            for (int handle : queries.unitHandles()) {
                var unit = queries.unit(handle);
                if (unit != null
                        && authority.controlsFaction(unit.faction().value())
                        && (unit.faction().equals(primary) || visibility.sees(primary, unit))) {
                    handles.add(handle);
                    if (handles.size() == MAX_HANDLES) break;
                }
            }
        }
        return new GroupRestoreFrame(epoch, authority.matchId(), handles);
    }

    public GroupRestoreFrame {
        handles = List.copyOf(handles);
        if (epoch <= 0
                || matchId <= 0
                || handles.size() > MAX_HANDLES
                || new HashSet<>(handles).size() != handles.size()
                || handles.stream().anyMatch(h -> Handle.generation(h) == 0))
            throw new IllegalArgumentException("invalid group restoration frame");
    }

    public static final WireCodec<GroupRestoreFrame> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, GroupRestoreFrame frame) {
            out.writeLong(frame.epoch())
                    .writeVarInt(frame.matchId())
                    .writeVarInt(frame.handles().size());
            for (int handle : frame.handles()) out.writeInt(handle);
        }

        @Override
        public GroupRestoreFrame read(WireReader in) {
            long epoch = in.readLong();
            int matchId = in.readVarInt();
            int count = in.readVarInt(MAX_HANDLES);
            var handles = new ArrayList<Integer>(count);
            for (int i = 0; i < count; i++) handles.add(in.readInt());
            return new GroupRestoreFrame(epoch, matchId, handles);
        }

        @Override
        public int maxSize() {
            return Long.BYTES + 5 + 2 + MAX_HANDLES * Integer.BYTES;
        }
    };
}
