package dev.strategos.core.protocol;

import dev.strategos.core.shared.CoreApi;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@code unit_detail} payload (NET-08): what a player's {@code ui_query} asked for, own units only, when it
 * changed and at most ten times a second: each unit's template, state, hit points and their most, effective walk
 * speed, its orders (kind and match-local target), at most {@value #MAX_ORDERS} of them, and the load it carries
 * (UI-06): the resource's index in the match's economy, -1 for none, and the amount in thousandths. Cooldowns join with
 * the cards that bring them.
 */
@CoreApi
public record UnitDetail(int matchId, long tick, List<Unit> units) {
    public static final int MAX_ORDERS = 16;

    /** One order: its kind ({@code OrderKind}) and its match-local target. */
    @CoreApi
    public record Order(int kind, float x, float z) {}

    /** One unit's details. */
    @CoreApi
    public record Unit(
            int handle,
            int template,
            int state,
            float health,
            float maxHealth,
            float walkSpeed,
            List<Order> orders,
            int carried,
            long load) {
        public Unit {
            orders = List.copyOf(orders.size() > MAX_ORDERS ? orders.subList(0, MAX_ORDERS) : orders);
        }
    }

    public UnitDetail {
        if (units.size() > UiQuery.MAX_HANDLES) {
            throw new IllegalArgumentException(units.size() + " units, more than a query's " + UiQuery.MAX_HANDLES);
        }
        units = List.copyOf(units);
    }

    public static final WireCodec<UnitDetail> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, UnitDetail d) {
            out.writeVarInt(d.matchId())
                    .writeVarLong(d.tick())
                    .writeVarInt(d.units().size());
            for (Unit u : d.units()) {
                out.writeInt(u.handle())
                        .writeVarInt(u.template())
                        .writeByte(u.state())
                        .writeFloat(u.health())
                        .writeFloat(u.maxHealth())
                        .writeFloat(u.walkSpeed())
                        .writeVarInt(u.orders().size());
                for (Order o : u.orders()) {
                    out.writeVarInt(o.kind()).writeFloat(o.x()).writeFloat(o.z());
                }
                out.writeVarInt(u.carried() + 1);
                if (u.carried() >= 0) {
                    out.writeVarLong(u.load());
                }
            }
        }

        @Override
        public UnitDetail read(WireReader in) {
            int matchId = in.readVarInt();
            long tick = in.readVarLong();
            int count = in.readVarInt(UiQuery.MAX_HANDLES);
            List<Unit> units = new ArrayList<>(count);
            for (int k = 0; k < count; k++) {
                int handle = in.readInt();
                int template = in.readVarInt();
                int state = in.readUnsignedByte();
                float health = in.readFloat();
                float max = in.readFloat();
                float speed = in.readFloat();
                int orders = in.readVarInt(MAX_ORDERS);
                List<Order> list = new ArrayList<>(orders);
                for (int j = 0; j < orders; j++) {
                    list.add(new Order(in.readVarInt(), in.readFloat(), in.readFloat()));
                }
                int carried = in.readVarInt() - 1;
                long load = carried >= 0 ? in.readVarLong() : 0;
                units.add(new Unit(handle, template, state, health, max, speed, list, carried, load));
            }
            return new UnitDetail(matchId, tick, units);
        }

        @Override
        public int maxSize() {
            return 5 + 10 + 5 + UiQuery.MAX_HANDLES * (4 + 5 + 1 + 12 + 5 + MAX_ORDERS * (5 + 8) + 5 + 10);
        }
    };
}
