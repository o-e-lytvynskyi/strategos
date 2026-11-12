package dev.strategos.core.protocol;

import dev.strategos.core.shared.CoreApi;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@code unit_detail} payload (NET-08): what a player's {@code ui_query} asked for, own units only, when it
 * changed and at most ten times a second: each unit's template, state, hit points and their most, effective walk
 * speed, its orders (kind and match-local target), at most {@value #MAX_ORDERS} of them, and the load it carries
 * (UI-06): the resource's index in the match's economy, -1 for none, and the amount in thousandths. Cooldowns join with
 * the cards that bring them. A trailer after the units (CMB-17) lists the units whose current order attacks a unit the
 * viewer may see, each as its place in the list and the target's index; it is left out when none does, so a frame
 * without attacks is byte-for-byte what it was. A second trailer (CMB-05) lists the units a player gave a stance, each
 * as its place in the list and the stance's code; a unit without one fights as its template says and is not listed,
 * and the trailer is left out when no unit has one. When only it is present, the attack trailer before it is an empty
 * count.
 */
@CoreApi
public record UnitDetail(int matchId, long tick, List<Unit> units) {
    public static final int MAX_ORDERS = 16;

    /** {@link Unit#target}'s value for a unit attacking no one the viewer may see: the target's index otherwise. */
    public static final int NO_TARGET = -1;

    /** {@link Unit#stance}'s value for a unit that fights as its template says. */
    public static final int NO_STANCE = -1;

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
            long load,
            int target,
            int stance) {
        public Unit {
            orders = List.copyOf(orders.size() > MAX_ORDERS ? orders.subList(0, MAX_ORDERS) : orders);
        }

        Unit withTarget(int target) {
            return new Unit(
                    handle, template, state, health, maxHealth, walkSpeed, orders, carried, load, target, stance);
        }

        Unit withStance(int stance) {
            return new Unit(
                    handle, template, state, health, maxHealth, walkSpeed, orders, carried, load, target, stance);
        }

        /** A unit with the stance its template gives it. */
        public Unit(
                int handle,
                int template,
                int state,
                float health,
                float maxHealth,
                float walkSpeed,
                List<Order> orders,
                int carried,
                long load,
                int target) {
            this(handle, template, state, health, maxHealth, walkSpeed, orders, carried, load, target, NO_STANCE);
        }

        /** A unit attacking no one. */
        public Unit(
                int handle,
                int template,
                int state,
                float health,
                float maxHealth,
                float walkSpeed,
                List<Order> orders,
                int carried,
                long load) {
            this(handle, template, state, health, maxHealth, walkSpeed, orders, carried, load, NO_TARGET);
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
            int targeted = 0;
            for (Unit u : d.units()) {
                targeted += u.target() == NO_TARGET ? 0 : 1;
            }
            int stanced = 0;
            for (Unit u : d.units()) {
                stanced += u.stance() == NO_STANCE ? 0 : 1;
            }
            if (targeted > 0 || stanced > 0) {
                out.writeVarInt(targeted);
                for (int k = 0; k < d.units().size(); k++) {
                    int target = d.units().get(k).target();
                    if (target != NO_TARGET) {
                        out.writeVarInt(k).writeVarInt(target);
                    }
                }
            }
            if (stanced > 0) {
                out.writeVarInt(stanced);
                for (int k = 0; k < d.units().size(); k++) {
                    int stance = d.units().get(k).stance();
                    if (stance != NO_STANCE) {
                        out.writeVarInt(k).writeByte(stance);
                    }
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
            if (in.remaining() > 0) {
                int targeted = in.readVarInt(count);
                for (int j = 0; j < targeted; j++) {
                    int at = in.readVarInt(count - 1);
                    units.set(at, units.get(at).withTarget(in.readVarInt()));
                }
                if (in.remaining() > 0) {
                    int stanced = in.readVarInt(count);
                    for (int j = 0; j < stanced; j++) {
                        int at = in.readVarInt(count - 1);
                        units.set(at, units.get(at).withStance(in.readUnsignedByte()));
                    }
                }
            }
            return new UnitDetail(matchId, tick, units);
        }

        @Override
        public int maxSize() {
            return 5
                    + 10
                    + 5
                    + UiQuery.MAX_HANDLES * (4 + 5 + 1 + 12 + 5 + MAX_ORDERS * (5 + 8) + 5 + 10 + 10 + 6)
                    + 5
                    + 5;
        }
    };
}
