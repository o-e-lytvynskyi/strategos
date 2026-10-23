package dev.strategos.core.protocol;

import dev.strategos.core.shared.CoreApi;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@code building_state} payload (BLD-01, 04 §12.2): the buildings of match {@code matchId} a player knows of, each
 * with where it stands, its state, progress and hit points (in 255ths of its most); its own faction's also carry what
 * only the owner sees: the production queue's template indices, the head's progress in thousandths and the rally
 * point, and for a field (ECO-09) the food its crop holds, in thousandths (-1 for no field), and the ticks it still
 * ripens. Until fog (M6) every building is
 * known. Sent to each seated player when it changes.
 */
@CoreApi
public record BuildingState(int matchId, long tick, List<Entry> buildings) {
    public static final int MAX_BUILDINGS = 512;
    public static final int MAX_QUEUE = 8;

    /** One building; {@code own} entries carry the owner's fields, the others leave them empty. */
    @CoreApi
    public record Entry(
            int handle,
            int template,
            int faction,
            int x,
            int z,
            int rotation,
            int baseY,
            int state,
            int progress,
            int health,
            boolean own,
            List<Integer> queue,
            boolean rally,
            int rallyX,
            int rallyZ,
            int trained,
            int fieldLeft,
            int fieldRipening) {
        public Entry {
            queue = List.copyOf(queue);
            if (queue.size() > MAX_QUEUE || health < 0 || health > UnitHud.WHOLE || rotation < 0 || rotation > 3) {
                throw new IllegalArgumentException("a building entry out of range: " + handle);
            }
            if (!own && (!queue.isEmpty() || rally || trained != 0 || fieldLeft != -1 || fieldRipening != 0)) {
                throw new IllegalArgumentException("a building of another faction carries owner fields: " + handle);
            }
        }
    }

    public BuildingState {
        buildings = List.copyOf(buildings);
        if (buildings.size() > MAX_BUILDINGS) {
            throw new IllegalArgumentException(buildings.size() + " buildings, more than " + MAX_BUILDINGS);
        }
    }

    /** The same state but for its tick. */
    public boolean sameAs(BuildingState other) {
        return matchId == other.matchId && buildings.equals(other.buildings);
    }

    public static final WireCodec<BuildingState> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, BuildingState s) {
            out.writeVarInt(s.matchId())
                    .writeVarLong(s.tick())
                    .writeVarInt(s.buildings().size());
            for (Entry e : s.buildings()) {
                out.writeVarInt(e.handle())
                        .writeVarInt(e.template())
                        .writeVarInt(e.faction())
                        .writeZigZag(e.x())
                        .writeZigZag(e.z())
                        .writeByte((e.rotation() << 4) | (e.state() & 0xF))
                        .writeZigZag(e.baseY())
                        .writeVarInt(e.progress())
                        .writeByte(e.health())
                        .writeByte(e.own() ? 1 : 0);
                if (e.own()) {
                    out.writeVarInt(e.queue().size());
                    for (int t : e.queue()) {
                        out.writeVarInt(t);
                    }
                    out.writeByte(e.rally() ? 1 : 0);
                    if (e.rally()) {
                        out.writeZigZag(e.rallyX()).writeZigZag(e.rallyZ());
                    }
                    if (!e.queue().isEmpty()) {
                        out.writeVarInt(e.trained());
                    }
                    out.writeVarInt(e.fieldLeft() + 1);
                    if (e.fieldLeft() >= 0) {
                        out.writeVarInt(e.fieldRipening());
                    }
                }
            }
        }

        @Override
        public BuildingState read(WireReader in) {
            int matchId = in.readVarInt();
            long tick = in.readVarLong();
            int n = in.readVarInt(MAX_BUILDINGS);
            List<Entry> out = new ArrayList<>(n);
            for (int k = 0; k < n; k++) {
                int handle = in.readVarInt();
                int template = in.readVarInt();
                int faction = in.readVarInt();
                int x = in.readZigZagInt();
                int z = in.readZigZagInt();
                int packed = in.readUnsignedByte();
                int baseY = in.readZigZagInt();
                int progress = in.readVarInt();
                int health = in.readUnsignedByte();
                boolean own = in.readUnsignedByte() == 1;
                List<Integer> queue = new ArrayList<>();
                boolean rally = false;
                int rx = 0;
                int rz = 0;
                int trained = 0;
                int fieldLeft = -1;
                int ripening = 0;
                if (own) {
                    int q = in.readVarInt(MAX_QUEUE);
                    for (int i = 0; i < q; i++) {
                        queue.add(in.readVarInt());
                    }
                    rally = in.readUnsignedByte() == 1;
                    if (rally) {
                        rx = in.readZigZagInt();
                        rz = in.readZigZagInt();
                    }
                    if (q > 0) {
                        trained = in.readVarInt();
                    }
                    fieldLeft = in.readVarInt() - 1;
                    if (fieldLeft >= 0) {
                        ripening = in.readVarInt();
                    }
                }
                out.add(new Entry(
                        handle,
                        template,
                        faction,
                        x,
                        z,
                        packed >> 4,
                        baseY,
                        packed & 0xF,
                        progress,
                        health,
                        own,
                        queue,
                        rally,
                        rx,
                        rz,
                        trained,
                        fieldLeft,
                        ripening));
            }
            return new BuildingState(matchId, tick, out);
        }

        @Override
        public int maxSize() {
            return 5
                    + 10
                    + 2
                    + MAX_BUILDINGS * (5 + 5 + 5 + 5 + 5 + 1 + 5 + 5 + 1 + 1 + 1 + MAX_QUEUE * 5 + 1 + 10 + 5 + 5 + 5);
        }
    };
}
