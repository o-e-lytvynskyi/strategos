package dev.strategos.core.sim.econ;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.ValuePath;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.CarcassGone;
import dev.strategos.core.sim.CarcassLeft;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.MotionStore;
import dev.strategos.core.sim.UnitStruck;
import dev.strategos.core.sim.combat.Damage;
import dev.strategos.core.sim.nav.MaterialKind;
import java.util.List;

/**
 * Livestock (ECO-16, spec §1.6 row 11): an animal is a Gaia unit whose template has a {@code Fauna/meat} above 0, and
 * from its spawn it has its meat node, a {@link MaterialKind#CARCASS} of food's {@code meat} at its cell, with {@code
 * Fauna/slots} work slots round it, linked to it. A gatherer sent to the node strikes the live animal with its {@code
 * ResourceGatherer/slaughter} once a second through core damage; once it is dead the node is gathered as any other, and
 * the body the mod keeps goes when it is empty.
 */
public final class Livestock {
    public static final String MEAT = "Fauna/meat";
    public static final String SLOTS = "Fauna/slots";
    public static final String SLAUGHTER = "ResourceGatherer/slaughter";
    public static final String SUBTYPE = "meat";

    /** Ticks between a gatherer's blows. */
    static final int REPEAT = 20;

    private Livestock() {}

    /** Whether template {@code netIndex} is an animal: a {@code Fauna/meat} above 0. */
    public static boolean isFauna(TemplateSet templates, int netIndex) {
        ValuePath p = templates.valuePaths().byPath(MEAT);
        return templates.has(netIndex) && p != null && templates.value(netIndex, p.id()) > 0;
    }

    /** Animal {@code handle}, just spawned, gets its meat node; its id. */
    public static int spawn(MatchState s, int handle) {
        int i = Handle.index(handle);
        int template = s.identity().templateIndex(i);
        TemplateSet templates = s.templates();
        EconomyRules economy = s.config().rules().economy();
        int food = Math.max(0, economy.indexOf("food"));
        int meat = Math.max(0, economy.resources().get(food).subtypes().indexOf(SUBTYPE));
        MotionStore m = s.motion();
        int x = (int) Math.floor(m.x(i));
        int y = (int) Math.floor(m.y(i));
        int z = (int) Math.floor(m.z(i));
        int[] ring = NodeDiscovery.ring(new int[] {NodeStore.cell(x, z)});
        int want = Math.max(1, (int) number(templates, template, SLOTS, 3));
        int[] slots = new int[Math.min(want, ring.length)];
        for (int k = 0; k < slots.length; k++) {
            slots[k] = ring[k * ring.length / slots.length]; // spread round it
        }
        long amount = Amount.of(number(templates, template, MEAT, 0));
        int id = s.addNode(
                MaterialKind.CARCASS,
                food,
                meat,
                0,
                amount,
                x + 0.5,
                z + 0.5,
                y,
                new long[] {NodeStore.pack(x, y, z)},
                slots);
        s.nodes().setTemplate(id, template);
        s.nodes().setUnit(id, handle);
        s.ledger().found(food, amount);
        return id;
    }

    /** The meat node of live animal {@code handle}, or 0. */
    public static int nodeOf(MatchState s, int handle) {
        NodeStore nodes = s.nodes();
        for (int id = 1; id <= nodes.count(); id++) {
            if (nodes.unit(id) == handle && nodes.kind(id) == MaterialKind.CARCASS) {
                return id;
            }
        }
        return 0;
    }

    /** Whether meat node {@code node}'s animal still lives. */
    public static boolean alive(MatchState s, int node) {
        int unit = s.nodes().unit(node);
        return unit != 0 && s.entities().isAlive(unit);
    }

    /** Animal {@code handle} died: its node is a carcass from now on, and the mod keeps its body. */
    public static void died(MatchState s, int handle) {
        int node = nodeOf(s, handle);
        if (node != 0) {
            s.nodes().setUnit(node, 0);
            s.outputs().add(new CarcassLeft(handle, node));
        }
    }

    /**
     * Gatherer {@code i} ({@code handle}) at live animal {@code node}'s slot: a blow once {@code nextBlow}, a tick, has
     * come; the tick of the next one.
     */
    public static int strike(MatchState s, int i, int handle, int node, int nextBlow) {
        int tick = (int) s.tick();
        if (tick < nextBlow) {
            return nextBlow;
        }
        int path = s.stats().path(SLAUGHTER);
        double v = path < 0 ? Double.NaN : s.stats().read(i, path);
        float blow = Double.isFinite(v) && v > 0 ? (float) v : 1f;
        int animal = s.nodes().unit(node);
        s.queueDamage(new Damage(animal, handle, blow, 0, 0, 0, List.of(), false));
        s.outputs().add(new UnitStruck(handle, animal));
        return tick + REPEAT;
    }

    /** Takes up to {@code want} thousandths from carcass {@code node}; the last of it removes the body. */
    public static long take(MatchState s, int node, long want) {
        NodeStore nodes = s.nodes();
        long given = nodes.take(node, want);
        if (given > 0 && nodes.exhausted(node)) {
            s.outputs().add(new CarcassGone(node));
        }
        return given;
    }

    private static double number(TemplateSet templates, int netIndex, String path, double otherwise) {
        ValuePath p = templates.valuePaths().byPath(path);
        double v = p == null ? Double.NaN : templates.value(netIndex, p.id());
        return Double.isFinite(v) ? v : otherwise;
    }
}
