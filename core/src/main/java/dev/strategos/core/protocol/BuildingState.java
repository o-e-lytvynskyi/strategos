package dev.strategos.core.protocol;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.QueueEntry;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@code building_state} payload (BLD-01, 04 §12.2): the buildings of match {@code matchId} a player knows of, each
 * with where it stands, its state, progress and hit points (in 255ths of its most); its own faction's also carry what
 * only the owner sees: the production queue's template indices, the head's progress in thousandths and the rally
 * point, and for a field (ECO-09) the food its crop holds, in thousandths (-1 for no field), and the ticks it still
 * ripens; and for a farmstead its auto-reseed toggle (ECO-17). Until fog (M6) every building is
 * known. Sent to each seated player when it changes.
 */
@CoreApi
public record BuildingState(int matchId, long tick, List<Entry> buildings) {
    public static final int MAX_BUILDINGS = 512;
    public static final int MAX_QUEUE = 8;

    /**
     * A queue with a tech entry (TECH-01) writes its size with this bit set, then a byte with one bit per entry, set
     * for a tech; sizes are at most {@value #MAX_QUEUE}, so the bit never meets an old size. A tech entry is
     * {@code -1 - index} in {@link Entry#queue()} (the store's own encoding) and its index on the wire.
     */
    static final int TECH_FLAG = 0x10;

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
            int fieldRipening,
            boolean autoReseed,
            List<Integer> batches) {
        /** An entry whose queue holds single units (PROD-02's batches aside). */
        public Entry(
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
                int fieldRipening,
                boolean autoReseed) {
            this(
                    handle,
                    template,
                    faction,
                    x,
                    z,
                    rotation,
                    baseY,
                    state,
                    progress,
                    health,
                    own,
                    queue,
                    rally,
                    rallyX,
                    rallyZ,
                    trained,
                    fieldLeft,
                    fieldRipening,
                    autoReseed,
                    List.of());
        }

        /** An entry with its auto-reseed off: every building but a farmstead's own. */
        public Entry(
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
            this(
                    handle,
                    template,
                    faction,
                    x,
                    z,
                    rotation,
                    baseY,
                    state,
                    progress,
                    health,
                    own,
                    queue,
                    rally,
                    rallyX,
                    rallyZ,
                    trained,
                    fieldLeft,
                    fieldRipening,
                    false,
                    List.of());
        }

        /** Entry {@code index}'s batch: how many it trains at once, 1 for a single one. */
        public int batch(int index) {
            return index < batches.size() ? Math.max(1, batches.get(index)) : 1;
        }

        public Entry {
            queue = List.copyOf(queue);
            batches = batches.stream().anyMatch(n -> n > 1) ? List.copyOf(batches) : List.of(); // single ones: none
            if (queue.size() > MAX_QUEUE || health < 0 || health > UnitHud.WHOLE || rotation < 0 || rotation > 3) {
                throw new IllegalArgumentException("a building entry out of range: " + handle);
            }
            if (!own
                    && (!queue.isEmpty()
                            || rally
                            || trained != 0
                            || fieldLeft != -1
                            || fieldRipening != 0
                            || autoReseed
                            || !batches.isEmpty())) {
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
                    int techs = 0; // one bit per entry that is a tech (TECH-01), the store's -1 - index
                    for (int k = 0; k < e.queue().size(); k++) {
                        techs |= (QueueEntry.isTech(e.queue().get(k)) ? 1 : 0) << k;
                    }
                    if (techs == 0) {
                        out.writeVarInt(e.queue().size()); // a queue of units: the bytes of before techs
                    } else {
                        out.writeVarInt(TECH_FLAG | e.queue().size()).writeByte(techs);
                    }
                    for (int t : e.queue()) {
                        out.writeVarInt(QueueEntry.isTech(t) ? QueueEntry.techIndex(t) : t);
                    }
                    out.writeByte(e.rally() ? 1 : 0);
                    if (e.rally()) {
                        out.writeZigZag(e.rallyX()).writeZigZag(e.rallyZ());
                    }
                    if (!e.queue().isEmpty()) {
                        out.writeVarInt(e.trained());
                        for (int k = 0; k < e.queue().size(); k++) {
                            out.writeVarInt(e.batch(k));
                        }
                    }
                    out.writeVarInt(e.fieldLeft() + 1);
                    if (e.fieldLeft() >= 0) {
                        out.writeVarInt(e.fieldRipening());
                    }
                    out.writeByte(e.autoReseed() ? 1 : 0);
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
                boolean autoReseed = false;
                List<Integer> batches = new ArrayList<>();
                if (own) {
                    int head = in.readVarInt(TECH_FLAG | MAX_QUEUE);
                    boolean hasTechs = head >= TECH_FLAG;
                    int q = head & ~TECH_FLAG;
                    if (q > MAX_QUEUE) {
                        throw new WireException("a queue of " + q + " entries");
                    }
                    int techs = hasTechs ? in.readUnsignedByte() : 0;
                    if (hasTechs && (techs == 0 || techs >>> q != 0)) { // only the one way to write it reads
                        throw new WireException("a tech mask " + techs + " for a queue of " + q);
                    }
                    for (int i = 0; i < q; i++) {
                        int t = in.readVarInt();
                        queue.add((techs >> i & 1) != 0 ? QueueEntry.techEntry(t) : t);
                    }
                    rally = in.readUnsignedByte() == 1;
                    if (rally) {
                        rx = in.readZigZagInt();
                        rz = in.readZigZagInt();
                    }
                    if (q > 0) {
                        trained = in.readVarInt();
                        for (int e = 0; e < q; e++) {
                            batches.add(in.readVarInt(TrainBatch.MOST));
                        }
                    }
                    fieldLeft = in.readVarInt() - 1;
                    if (fieldLeft >= 0) {
                        ripening = in.readVarInt();
                    }
                    autoReseed = in.readUnsignedByte() == 1;
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
                        ripening,
                        autoReseed,
                        batches));
            }
            return new BuildingState(matchId, tick, out);
        }

        @Override
        public int maxSize() {
            return 5
                    + 10
                    + 2
                    + MAX_BUILDINGS
                            * (5
                                    + 5
                                    + 5
                                    + 5
                                    + 5
                                    + 1
                                    + 5
                                    + 5
                                    + 1
                                    + 1
                                    + 1
                                    + MAX_QUEUE * 5
                                    + 2
                                    + 10
                                    + 5
                                    + 5
                                    + 5
                                    + 1
                                    + MAX_QUEUE);
        }
    };
}
