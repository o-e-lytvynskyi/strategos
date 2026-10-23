package dev.strategos.core.sim.econ;

import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.WorldEffect;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.nav.MaterialKind;
import dev.strategos.core.sim.world.WritePriority;
import java.util.ArrayList;
import java.util.List;

/**
 * Fields (ECO-09, 04 §11.2, M3 spec §1.6 row 10): a finished field is a crop node of its own, five farmers' slots on it,
 * that first ripens over its {@code Field/ripenTime} (its crop's age rising 0 to 7) while they wait, then holds its
 * {@code Field/amount}, its age showing what is left, {@code ceil(7 × left ÷ amount)}; reaped empty it stays at age 0
 * until a paid reseed ripens it again (D-14: manual in M3). n farmers reap {@code rate × (1 − 0.9ⁿ) ÷ 0.1} together, each
 * its rate times {@code (1 − 0.9ⁿ) ÷ (0.1 n)}. The ages are written by the core alone; the adapter denies crop growth in
 * a match.
 */
@CoreApi
public final class Fields {
    public static final String AMOUNT = "Field/amount";
    public static final String RIPEN = "Field/ripenTime";
    public static final String RESEED = "Field/reseed/";

    /** The crop's top age: wheat grows from 0 to 7. */
    public static final int STAGES = 7;

    /** What each extra farmer adds, as a share of the one before (0 A.D.'s 0.9, the starting value). */
    public static final double RETURNS = 0.9;

    /** How many farmers a field takes: one a slot. */
    public static final int SLOTS = 5;

    private Fields() {}

    /** Work slot {@code k}'s cell in a field's blueprint, x then z: four round its middle and the middle. */
    private static int slotX(int k) {
        return k == 2 ? 2 : k % 3 == 0 ? 1 : 3;
    }

    private static int slotZ(int k) {
        return k < 2 ? 1 : k == 2 ? 2 : 3;
    }

    /** Whether template {@code template} makes a field: its {@code Field/amount} above 0. */
    public static boolean isField(MatchState s, int template, int faction) {
        return value(s, template, faction, AMOUNT) > 0;
    }

    /** Field {@code handle} stands finished: its crop node, ripening from now. */
    public static void create(MatchState s, int handle) {
        BuildingStore b = s.buildings();
        int template = b.template(handle);
        int faction = b.faction(handle);
        long amount = Amount.of(value(s, template, faction, AMOUNT));
        Blueprint bp = s.blueprintOf(handle);
        Footprint at = s.footprintOf(handle);
        int baseY = b.baseY(handle);
        List<Long> crops = new ArrayList<>();
        for (int k = bp.count() - 1; k >= 0; k--) { // top-down, as nodes keep their blocks
            if (bp.palette().get(bp.state(k)).contains("wheat")) {
                crops.add(NodeStore.pack(at.worldX(bp.x(k), bp.z(k)), baseY + bp.y(k), at.worldZ(bp.x(k), bp.z(k))));
            }
        }
        int[] slots = new int[SLOTS];
        for (int k = 0; k < SLOTS; k++) {
            int x = Math.min(slotX(k), bp.sizeX() - 1);
            int z = Math.min(slotZ(k), bp.sizeZ() - 1);
            slots[k] = NodeStore.cell(at.worldX(x, z), at.worldZ(x, z));
        }
        java.util.Arrays.sort(slots);
        int resource = s.config().rules().economy().indexOf("food");
        int subtype = resource < 0
                ? 0
                : Math.max(
                        0,
                        s.config()
                                .rules()
                                .economy()
                                .resources()
                                .get(resource)
                                .subtypes()
                                .indexOf("grain"));
        int node = s.addNode(
                MaterialKind.CROP,
                Math.max(0, resource),
                subtype,
                0,
                amount,
                at.anchorX() + at.spanX() / 2.0,
                at.anchorZ() + at.spanZ() / 2.0,
                baseY + 1,
                crops.stream().mapToLong(Long::longValue).toArray(),
                slots);
        NodeStore nodes = s.nodes();
        nodes.take(node, amount); // it holds nothing until it ripens
        nodes.setRipening(node, ripenTicks(s, template, faction));
        b.setField(handle, node);
        show(s, node);
    }

    /** The ticks a field of {@code template} takes to ripen. */
    public static long ripenTicks(MatchState s, int template, int faction) {
        return Math.max(1, Math.round(value(s, template, faction, RIPEN) * 20));
    }

    /** The share of its rate each of {@code n} farmers reaps: {@code (1 − 0.9ⁿ) ÷ (0.1 n)}, 1 for one. */
    public static double share(int n) {
        if (n <= 1) {
            return 1;
        }
        return (1 - DetMath.pow(RETURNS, n)) / ((1 - RETURNS) * n);
    }

    /** The farmers holding a slot of field node {@code node}. */
    public static int farmers(NodeStore nodes, int node) {
        return nodes.slotCount(node) - nodes.freeSlots(node);
    }

    /** The age field node {@code node}'s crop shows. */
    public static int age(NodeStore nodes, int node, long ripenTicks) {
        long ripening = nodes.ripening(node);
        if (ripening > 0) {
            return (int) Math.min(STAGES - 1, (ripenTicks - ripening) * STAGES / Math.max(1, ripenTicks));
        }
        long most = nodes.most(node);
        if (most <= 0 || nodes.amount(node) <= 0) {
            return 0;
        }
        return (int) ((STAGES * nodes.amount(node) + most - 1) / most);
    }

    /** Takes up to {@code want} thousandths from a ripe field; what it gave. Its crop's age follows. */
    public static long take(MatchState s, int node, long want) {
        NodeStore nodes = s.nodes();
        long ripen = ripenOf(s, node);
        int before = age(nodes, node, ripen);
        long given = nodes.ripening(node) > 0 ? 0 : nodes.take(node, want);
        if (age(nodes, node, ripen) != before) {
            show(s, node);
        }
        return given;
    }

    /** One tick of every field that ripens: its age rises, and ripe, it holds its amount, which the ledger counts. */
    public static void ripen(MatchState s) {
        BuildingStore b = s.buildings();
        for (int handle : b.handles()) {
            int node = b.field(handle);
            if (node == 0 || s.nodes().ripening(node) <= 0) {
                continue;
            }
            NodeStore nodes = s.nodes();
            long ripen = ripenTicks(s, b.template(handle), b.faction(handle));
            int before = age(nodes, node, ripen);
            long left = nodes.ripening(node) - 1;
            nodes.setRipening(node, left);
            if (left == 0) {
                long most = nodes.most(node);
                nodes.refill(node, most);
                s.ledger().found(nodes.resource(node), most);
            }
            if (age(nodes, node, ripen) != before) {
                show(s, node);
            }
        }
    }

    /** Whether field {@code handle} may be reseeded now: reaped empty and not ripening. */
    public static boolean empty(MatchState s, int handle) {
        int node = s.buildings().field(handle);
        return node != 0 && s.nodes().amount(node) <= 0 && s.nodes().ripening(node) <= 0;
    }

    /** What reseeding field {@code handle} costs its faction, per resource in thousandths. */
    public static long[] reseedCost(MatchState s, int handle) {
        BuildingStore b = s.buildings();
        EconomyRules economy = s.config().rules().economy();
        long[] cost = new long[economy.size()];
        for (int r = 0; r < cost.length; r++) {
            cost[r] = Amount.of(value(
                    s,
                    b.template(handle),
                    b.faction(handle),
                    RESEED + economy.resources().get(r).id()));
        }
        return cost;
    }

    /** Field {@code handle} is sown again: it ripens afresh. */
    public static void reseed(MatchState s, int handle) {
        BuildingStore b = s.buildings();
        int node = b.field(handle);
        s.nodes().setRipening(node, ripenTicks(s, b.template(handle), b.faction(handle)));
        show(s, node);
    }

    /** Field {@code handle} goes: its crop holds nothing more, lost to everyone. */
    public static void lost(MatchState s, int handle) {
        int node = s.buildings().field(handle);
        if (node == 0) {
            return;
        }
        NodeStore nodes = s.nodes();
        long left = nodes.amount(node);
        nodes.take(node, left);
        nodes.setRipening(node, 0);
        s.ledger().lost(nodes.resource(node), left);
        while (nodes.gone(node) < nodes.blocks(node).length) {
            nodes.removeTop(node);
        }
        s.buildings().setField(handle, 0);
    }

    private static long ripenOf(MatchState s, int node) {
        BuildingStore b = s.buildings();
        for (int handle : b.handles()) {
            if (b.field(handle) == node) {
                return ripenTicks(s, b.template(handle), b.faction(handle));
            }
        }
        return 1;
    }

    private static void show(MatchState s, int node) {
        NodeStore nodes = s.nodes();
        long[] blocks = nodes.blocks(node);
        String state = "[age=" + age(nodes, node, ripenOf(s, node)) + "]";
        for (int k = nodes.gone(node); k < blocks.length; k++) {
            long p = blocks[k];
            s.outputs()
                    .add(new WorldEffect(
                            node, WritePriority.COSMETIC, NodeStore.x(p), NodeStore.y(p), NodeStore.z(p), state, 0));
        }
    }

    private static double value(MatchState s, int template, int faction, String path) {
        int p = s.stats().path(path);
        double v = p < 0 ? Double.NaN : s.stats().blockValue(template, faction, p);
        return Double.isFinite(v) ? Math.max(0, v) : 0;
    }
}
