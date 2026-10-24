package dev.strategos.core.sim.econ;

import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.ValuePath;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.WorldEffect;
import dev.strategos.core.sim.build.BuildingLife;
import dev.strategos.core.sim.build.Placing;
import dev.strategos.core.sim.nav.MaterialKind;
import dev.strategos.core.sim.world.WritePriority;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Mines and quarries (ECO-08, 04 §11.2): a deposit is a building template with a {@code Deposit} component, spawned
 * where a map or a scenario puts it, owned by nobody. Its node holds the template's amount over the blueprint's
 * blocks, top-down, and as many work slots as the template says round its footprint, which units cannot cross. As
 * its amount falls to 75, 50, 25 and 0 % the writer turns the next quarter of its blocks to the template's {@code
 * depleted} block; empty, it frees its slots and its footprint stays.
 */
public final class Deposits {
    public static final String AMOUNT = "Deposit/amount";
    public static final String SLOTS = "Deposit/slots";
    public static final String RESOURCE = "Deposit/resource";
    public static final String SUBTYPE = "Deposit/subtype";
    public static final String DEPLETED = "Deposit/depleted";

    /** Its look's stages: 4 quarters, the fourth the empty deposit. */
    static final int STAGES = 4;

    private Deposits() {}

    /** Whether template {@code netIndex} is a deposit: a blueprint and a {@code Deposit/amount} above 0. */
    public static boolean isDeposit(TemplateSet templates, int netIndex) {
        return templates.has(netIndex)
                && templates.blueprint(netIndex) != null
                && number(templates, netIndex, AMOUNT) > 0;
    }

    /** Spawns deposit template {@code netIndex} with its corner at {@code at}; its node's id. */
    public static int spawn(MatchState s, int netIndex, Footprint at) {
        TemplateSet templates = s.templates();
        Blueprint b = Objects.requireNonNull(templates.blueprint(netIndex), "a deposit without a blueprint");
        int baseY = Placing.baseY(s, b, at);
        int[] columns = at.columns(b);
        for (int cell : columns) {
            s.blockColumn(cell >>> 16, cell & 0xFFFF);
        }
        long[] blocks = new long[b.count()];
        for (int k = 0; k < b.count(); k++) { // top-down: the reverse of the build order
            int j = b.count() - 1 - k;
            blocks[k] = NodeStore.pack(at.worldX(b.x(j), b.z(j)), baseY + b.y(j), at.worldZ(b.x(j), b.z(j)));
        }
        int[] ring = NodeDiscovery.ring(columns);
        int want = Math.max(1, (int) number(templates, netIndex, SLOTS));
        int[] slots = new int[Math.min(want, ring.length)];
        for (int k = 0; k < slots.length; k++) {
            slots[k] = ring[k * ring.length / slots.length]; // spread round it
        }
        EconomyRules economy = s.config().rules().economy();
        int resource =
                Math.max(0, economy.indexOf(Objects.requireNonNullElse(text(templates, netIndex, RESOURCE), "")));
        String sub = text(templates, netIndex, SUBTYPE);
        int subtype = Math.max(0, economy.resources().get(resource).subtypes().indexOf(sub == null ? "" : sub));
        long amount = Amount.of(number(templates, netIndex, AMOUNT));
        int id = s.addNode(
                MaterialKind.DEPOSIT,
                resource,
                subtype,
                0,
                amount,
                at.anchorX() + at.spanX() / 2.0,
                at.anchorZ() + at.spanZ() / 2.0,
                baseY,
                blocks,
                slots);
        s.nodes().setTemplate(id, netIndex);
        s.ledger().found(resource, amount);
        BuildingLife.writeBlueprint(s, id, 0, b, at, baseY);
        return id;
    }

    /**
     * A restored match's deposits block their footprints again, as its buildings do (the world has their blocks), laid
     * over the pages before they are published, so no rebuild follows; a deposit blocks its columns for good, spent or
     * not.
     */
    public static void restoreFootprints(MatchState s) {
        NodeStore nodes = s.nodes();
        for (int id = 1; id <= nodes.count(); id++) {
            int template = nodes.template(id);
            Blueprint b = template < 0 ? null : s.templates().blueprint(template);
            if (b == null) {
                continue;
            }
            Footprint at = footprint(nodes, id, b);
            for (int cell : at.columns(b)) {
                s.restoreColumn(cell >>> 16, cell & 0xFFFF);
            }
        }
    }

    /** Where deposit {@code id} stands. */
    static Footprint footprint(NodeStore nodes, int id, Blueprint b) {
        return footprint(b, nodes.anchorX(id), nodes.anchorZ(id));
    }

    /** Where a deposit of blueprint {@code b} whose node's anchor is ({@code anchorX}, {@code anchorZ}) stands: the anchor is its footprint's middle. */
    public static Footprint footprint(Blueprint b, double anchorX, double anchorZ) {
        Footprint probe = Footprint.of(b, 0, 0, 0);
        return Footprint.of(
                b, (int) Math.round(anchorX - probe.spanX() / 2.0), (int) Math.round(anchorZ - probe.spanZ() / 2.0), 0);
    }

    /** Takes up to {@code want} thousandths from deposit {@code node}; each quarter it passes turns its blocks. */
    public static long take(MatchState s, int node, long want) {
        NodeStore nodes = s.nodes();
        long most = nodes.most(node);
        int from = stage(nodes.amount(node), most);
        long given = nodes.take(node, want);
        int to = stage(nodes.amount(node), most);
        if (to > from) {
            int template = nodes.template(node);
            String depleted = template < 0 ? null : text(s.templates(), template, DEPLETED);
            long[] blocks = nodes.blocks(node);
            for (int st = from + 1; st <= to; st++) {
                for (int k = (st - 1) * blocks.length / STAGES; k < st * blocks.length / STAGES; k++) {
                    s.outputs()
                            .add(new WorldEffect(
                                    node,
                                    WritePriority.DAMAGE_STAGE,
                                    NodeStore.x(blocks[k]),
                                    NodeStore.y(blocks[k]),
                                    NodeStore.z(blocks[k]),
                                    depleted == null ? "minecraft:stone" : depleted,
                                    0));
                }
            }
        }
        return given;
    }

    /** The stage of a deposit holding {@code amount} of its {@code most}: 0 whole, then a quarter more gone each. */
    static int stage(long amount, long most) {
        if (most <= 0 || amount <= 0) {
            return STAGES;
        }
        return STAGES - (int) ((amount * STAGES + most - 1) / most);
    }

    private static double number(TemplateSet templates, int netIndex, String path) {
        ValuePath p = templates.valuePaths().byPath(path);
        return p == null ? Double.NaN : templates.value(netIndex, p.id());
    }

    private static @Nullable String text(TemplateSet templates, int netIndex, String path) {
        ValuePath p = templates.tokenPaths().byPath(path);
        return p == null ? null : templates.text(netIndex, p.id());
    }
}
